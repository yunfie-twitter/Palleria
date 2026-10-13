package com.yunfie.illustia.pallasync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/** HTTP boundary for the relay. Protocol parsing remains explicit at each coordinator call site. */
internal class PallaSyncRemoteService(
    suppliedClient: OkHttpClient,
    private val policy: PallaSyncPolicyGate,
) {
    private val client =
        suppliedClient
            .newBuilder()
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }.dispatcher(Dispatcher())
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectionSpecs(
                listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()),
            ).build()
            .also { clients.add(WeakReference(it)) }

    companion object {
        private const val HTTP_RATE_LIMITED = 429
        private const val CHAIN_ID_INDEX = 3
        private val SUCCESS_CODES = 200..299
        private val clients = CopyOnWriteArrayList<WeakReference<OkHttpClient>>()
        private val calls = CopyOnWriteArrayList<Call>()

        fun cancelOrigin(origin: String) {
            calls.filter { PallaSyncUrls.extractOrigin(it.request().url) == origin }.forEach { it.cancel() }
            clients.forEach { reference ->
                reference.get()?.dispatcher?.let { dispatcher ->
                    (dispatcher.queuedCalls() + dispatcher.runningCalls())
                        .filter {
                            PallaSyncUrls.extractOrigin(it.request().url) == origin
                        }.forEach { it.cancel() }
                }
            }
        }
    }

    fun executeUnit(request: Request): PallaSyncHttpResult<Unit> = execute(request) { PallaSyncHttpResult.Success(Unit) }

    fun needsReconciliation(
        base: okhttp3.HttpUrl,
        chain: String,
    ): Boolean = policy.retry.needsReconciliation(PallaSyncUrls.extractOrigin(base), chain)

    fun reconciled(
        base: okhttp3.HttpUrl,
        chain: String,
    ) = policy.retry.reconciled(PallaSyncUrls.extractOrigin(base), chain)

    fun defer(
        request: Request,
        result: PallaSyncHttpResult.Retryable,
    ) = policy.retry.observe(request, result)

    // Discard parser/transport exception text: §19 forbids leaking remote payloads or tokens.
    @Suppress("SwallowedException")
    fun <T> execute(
        request: Request,
        onSuccess: (Response) -> PallaSyncHttpResult<T>,
    ): PallaSyncHttpResult<T> {
        val chainRequest = request.url.encodedPath == "/pallasync/v3/chains" || request.url.encodedPath.startsWith("/pallasync/v3/chains/")
        if (chainRequest) policy.retry.before(request)?.let { return it }
        val call = client.newCall(request)
        calls.add(call)
        var hint: PallaSyncProblemHint? = null
        var relayDate: String? = null
        var attempted = false
        val result =
            try {
                authorize(request)
                attempted = true
                call.execute().use { response ->
                    hint = problem(response)
                    relayDate = response.header("Date")
                    val classified = classifyPallaSyncHttpStatus(response.code, hint)
                    val headerWait =
                        PallaSyncRetryGate.retryAfter(
                            response.header("Retry-After"),
                            response.code == HTTP_RATE_LIMITED,
                            hint?.serverTimeMs ?: policy.retry.correctedTime(PallaSyncUrls.extractOrigin(request.url)),
                        )
                    val status =
                        if (classified is PallaSyncHttpResult.Retryable) {
                            classified.copy(retryAfterMs = maxOf(classified.retryAfterMs, headerWait))
                        } else {
                            classified
                        }
                    if (status != null) {
                        status
                    } else {
                        check(
                            response.header("Content-Type")?.substringBefore(';')?.trim() == request.header("Accept"),
                        ) { "C_SCHEMA_VIOLATION" }
                        onSuccess(response)
                    }
                }
            } catch (error: java.nio.charset.CharacterCodingException) {
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            } catch (error: IOException) {
                PallaSyncHttpResult.Retryable("PallaSync network request failed", reconciliationRequired = request.method != "GET")
            } catch (error: IllegalArgumentException) {
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            } catch (error: IllegalStateException) {
                operationalFailure(error, request)
            } finally {
                calls.remove(call)
            }
        if (chainRequest && attempted) policy.retry.observe(request, result, hint, relayDate)
        return result
    }

    private fun operationalFailure(
        error: IllegalStateException,
        request: Request,
    ): PallaSyncHttpResult<Nothing> {
        val wait = policy.retry.remaining(PallaSyncUrls.extractOrigin(request.url))
        return if (error.message == "C_RELAY_POLICY_UNAVAILABLE" &&
            policy.retry.hasDeferredRetry(PallaSyncUrls.extractOrigin(request.url))
        ) {
            PallaSyncHttpResult.Retryable("C_RELAY_POLICY_UNAVAILABLE", wait)
        } else {
            PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_RELAY_POLICY_UNAVAILABLE")
        }
    }

    private fun authorize(request: Request) {
        if (request.method == "GET" && request.url.encodedPath == "/pallasync/v3/health") {
            check(request.header("Authorization") == null && request.header("Cookie") == null && request.url.query == null) {
                "C_RELAY_DISCLOSURE_INVALID"
            }
        } else {
            val path = request.url.pathSegments
            if (path.size > CHAIN_ID_INDEX && path.take(CHAIN_ID_INDEX) == listOf("pallasync", "v3", "chains")) {
                policy.requireChainOrigin(path[CHAIN_ID_INDEX], PallaSyncUrls.extractOrigin(request.url))
            }
            policy.requireCurrent(PallaSyncUrls.extractOrigin(request.url))
        }
    }

    private fun problem(response: Response): PallaSyncProblemHint? {
        if (response.code in SUCCESS_CODES) return null
        return runCatching {
            check(response.header("Content-Type")?.substringBefore(';')?.trim() == "application/problem+json")
            Json.decodeFromString<PallaSyncProblemHint>(PallaSyncCore.validateProblem(response.pallaSyncBytes(), response.code))
        }.getOrNull()
    }
}

@Serializable
internal data class PallaSyncProblemHint(
    val status: Int,
    val code: String,
    val retryable: Boolean,
    @SerialName("server_time_ms") val serverTimeMs: Long? = null,
    @SerialName("retry_after_ms") val retryAfterMs: Long? = null,
)
