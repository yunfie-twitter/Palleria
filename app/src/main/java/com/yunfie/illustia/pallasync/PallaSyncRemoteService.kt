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
    internal data class HistoricalSourceRead(
        val chainId: String,
        val origin: String,
    )

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
        private const val CHAIN_ROUTE_SEGMENTS = 3
        private val SUCCESS_CODES = 200..299
        private val clients = CopyOnWriteArrayList<WeakReference<OkHttpClient>>()
        private val calls = CopyOnWriteArrayList<Call>()

        /** `/chains/import` is global A scope; only `/chains/{chain_id}` and its descendants are chain-scoped. */
        internal fun chainIdForAuthorization(pathSegments: List<String>): String? {
            val isChainRoute = pathSegments.take(CHAIN_ROUTE_SEGMENTS) == listOf("pallasync", "v3", "chains")
            if (pathSegments.size <= CHAIN_ROUTE_SEGMENTS || !isChainRoute) {
                return null
            }
            val isTopLevelImport = pathSegments.size == CHAIN_ROUTE_SEGMENTS + 1 && pathSegments.last() == "import"
            if (isTopLevelImport) return null
            return pathSegments[CHAIN_ROUTE_SEGMENTS]
        }

        internal fun allowsHistoricalSourceRead(
            request: Request,
            scope: HistoricalSourceRead,
        ): Boolean =
            request.method == "GET" &&
                PallaSyncUrls.extractOrigin(request.url) == scope.origin &&
                chainIdForAuthorization(request.url.pathSegments) == scope.chainId

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

    fun cursorRestarted(
        base: okhttp3.HttpUrl,
        chain: String,
        expiredRequest: Request,
    ) = policy.retry.cursorRestarted(PallaSyncUrls.extractOrigin(base), chain, expiredRequest)

    fun defer(
        request: Request,
        result: PallaSyncHttpResult.Retryable,
    ) = policy.retry.observe(request, result)

    // Discard parser/transport exception text: §19 forbids leaking remote payloads or tokens.
    @Suppress("SwallowedException")
    fun <T> execute(
        request: Request,
        migrationDestination: Boolean = false,
        historicalSourceRead: HistoricalSourceRead? = null,
        enforceRetryGate: Boolean = true,
        onSuccess: (Response) -> PallaSyncHttpResult<T>,
    ): PallaSyncHttpResult<T> {
        val chainRequest = request.url.encodedPath == "/pallasync/v3/chains" || request.url.encodedPath.startsWith("/pallasync/v3/chains/")
        val legacySourceRequest =
            request.method == "GET" && request.url.encodedPath.startsWith("/pallasync/v2/chains/") &&
                request.url.encodedPath
                    .substringAfter("/pallasync/v2/chains/")
                    .contains('/')
        val trackedRequest = chainRequest || legacySourceRequest
        if (trackedRequest && enforceRetryGate) policy.retry.before(request)?.let { return it }
        val call = client.newCall(request)
        calls.add(call)
        var hint: PallaSyncProblemHint? = null
        var relayDate: String? = null
        var attempted = false
        val result =
            try {
                authorize(request, migrationDestination, historicalSourceRead)
                attempted = true
                call.execute().use { response ->
                    hint = problem(response)
                    relayDate = response.header("Date")
                    val classified = classifyPallaSyncHttpStatus(response.code, hint, request)
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
        if (trackedRequest && attempted && enforceRetryGate) policy.retry.observe(request, result, hint, relayDate)
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

    private fun authorize(
        request: Request,
        migrationDestination: Boolean,
        historicalSourceRead: HistoricalSourceRead?,
    ) {
        if (request.method == "GET" && request.url.encodedPath == "/pallasync/v3/health") {
            check(request.header("Authorization") == null && request.header("Cookie") == null && request.url.query == null) {
                "C_RELAY_DISCLOSURE_INVALID"
            }
        } else {
            val requestOrigin = PallaSyncUrls.extractOrigin(request.url)
            if (historicalSourceRead != null) {
                check(allowsHistoricalSourceRead(request, historicalSourceRead)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(historicalSourceRead.origin)
            } else {
                chainIdForAuthorization(request.url.pathSegments)?.let { chainId ->
                    if (migrationDestination) {
                        val prefix = "/pallasync/v3/chains/$chainId"
                        val suffix = request.url.encodedPath.removePrefix(prefix)
                        val allowed =
                            when {
                                request.method == "POST" && suffix == "/snapshots/parts" -> true
                                request.method == "PUT" && suffix == "/parameters" -> true
                                request.method == "GET" && suffix in setOf("/transparency/head", "/transparency/entries") -> true
                                request.method == "POST" && Regex("/devices/[0-9a-f-]{36}/keys/ack").matches(suffix) -> true
                                else -> false
                            }
                        check(allowed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                        policy.requireMigrationDestination(requestOrigin)
                    } else {
                        policy.requireChainOrigin(chainId, requestOrigin)
                    }
                }
                policy.requireCurrent(requestOrigin)
            }
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
    @SerialName("used_bytes") val usedBytes: Long? = null,
    @SerialName("max_bytes") val maxBytes: Long? = null,
    @SerialName("shutdown_at_ms") val shutdownAtMs: Long? = null,
    @SerialName("migration_origin") val migrationOrigin: String? = null,
)
