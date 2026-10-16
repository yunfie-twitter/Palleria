package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okio.Buffer
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** §§7.13/11.6/11.7. Public fetches and local consent contain no chain/account identity. */
@Suppress("TooManyFunctions")
class PallaSyncPolicyGate internal constructor(
    context: Context,
    isolatedTransport: OkHttpClient? = null,
) {
    private val masterKey = PallaSyncPlatformKeys.master(context)
    private val prefs =
        EncryptedSharedPreferences.create(
            context,
            "pallasync_policy",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    private val anonymous = protocolHttpClient(isolatedTransport)
    internal val retry =
        PallaSyncRetryGate(prefs, {
            android.provider.Settings.Global
                .getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
                .toLong()
        })

    @Serializable
    data class Consent(
        @SerialName("consent_id") val consentId: String,
        @SerialName("relay_origin") val relayOrigin: String,
        @SerialName("relay_key_id") val relayKeyId: String,
        @SerialName("policy_url") val policyUrl: String,
        @SerialName("policy_sha256") val policySha256: String,
        @SerialName("disclosure_url") val disclosureUrl: String,
        @SerialName("disclosure_revision") val disclosureRevision: Long,
        @SerialName("disclosure_sha256") val disclosureSha256: String,
        @SerialName("accepted_at_ms") val acceptedAtMs: Long,
        @SerialName("withdrawn_at_ms") val withdrawnAtMs: Long?,
    )

    @Serializable
    data class Validated(
        @SerialName("relay_origin") val relayOrigin: String,
        @SerialName("relay_key_id") val relayKeyId: String,
        val revision: Long,
        @SerialName("policy_sha256") val policySha256: String,
        @SerialName("disclosure_sha256") val disclosureSha256: String,
        val disclosure: JsonObject,
    )

    data class Offer(
        val origin: String,
        val publicKey: String,
        val keyId: String,
        internal val health: ByteArray,
    )

    data class Review(
        val validated: Validated,
        val markdown: String,
        val disclosure: String,
    )

    @Serializable
    internal data class TrustedSnapshotChoice(
        @SerialName("relay_origin") val relayOrigin: String,
        @SerialName("chain_id") val chainId: String,
        @SerialName("log_id") val logId: String,
        @SerialName("compaction_id") val compactionId: String,
        @SerialName("compaction_hash") val compactionHash: String,
        @SerialName("covered_through_seq") val coveredThroughSeq: Long,
        @SerialName("covered_log_hash") val coveredLogHash: String,
        @SerialName("state_sha256") val stateSha256: String,
        @SerialName("epoch_hash") val epochHash: String,
        @SerialName("author_device_id") val authorDeviceId: String,
        @SerialName("author_public_key") val authorPublicKey: String,
        @SerialName("part_count") val partCount: Int,
    )

    /** §§11.7/17.6: only the caller holding verified/protected authority binds a chain. */
    internal fun bindChainOrigin(
        chain: String,
        origin: String,
    ) {
        val canonical = normalizeOrigin(origin)
        synchronized(consentLock) {
            val previous = prefs.getString("chain-origin:$chain", null)
            check(previous == null || previous == canonical) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            check(prefs.edit().putString("chain-origin:$chain", canonical).commit()) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    /** §§11.7/17.6: editing a URL is not authenticated relay migration. */
    internal fun requireChainOrigin(
        chain: String,
        origin: String,
    ) {
        val previous = prefs.getString("chain-origin:$chain", null)
        check(previous == null || previous == normalizeOrigin(origin)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
    }

    /** Source choice, key trust and document agreement are distinct foreground actions. */
    fun chooseSource(
        origin: String,
        source: String,
        explicitlyConfirmed: Boolean,
    ) {
        PallaSyncInitialSourceChoice.requireExplicit(source, explicitlyConfirmed)
        check(prefs.edit().putString("source:${normalizeOrigin(origin)}", source).commit()) { "C_LOCAL_STORAGE_FULL" }
    }

    /** §11.6: persist the exact source, author, and coverage that the user explicitly trusted. */
    internal fun confirmTrustedSnapshot(
        choice: TrustedSnapshotChoice,
        explicitlyConfirmed: Boolean,
    ) {
        val origin = normalizeOrigin(choice.relayOrigin)
        check(
            explicitlyConfirmed && choice.relayOrigin == origin && choice.chainId.isNotBlank() && choice.logId.isNotBlank() &&
                choice.compactionId.isNotBlank() && choice.compactionHash.isNotBlank() && choice.coveredThroughSeq >= 0 &&
                choice.coveredLogHash.isNotBlank() && choice.stateSha256.isNotBlank() && choice.epochHash.isNotBlank() &&
                choice.authorDeviceId.isNotBlank() && choice.authorPublicKey.isNotBlank() && choice.partCount > 0,
        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        chooseSource(origin, "trusted_snapshot", explicitlyConfirmed = true)
        check(
            prefs
                .edit()
                .putString("snapshot-choice:${choice.chainId}", Json.encodeToString(choice))
                .commit(),
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    internal fun trustedSnapshotChoice(chainId: String): TrustedSnapshotChoice? =
        prefs.getString("snapshot-choice:$chainId", null)?.let { Json.decodeFromString<TrustedSnapshotChoice>(it) }

    fun inspectRelay(origin: String): Offer {
        val normalized = normalizeOrigin(origin)
        val bytes = fetch(normalized.toHttpUrl(), "/pallasync/v3/health", PALLASYNC_MEDIA_TYPE)
        val strictHealth = PallaSyncCore.validateHealth(bytes, normalized)
        val health =
            when (val parsed = parseHealthResponseBody(strictHealth, normalized)) {
                is PallaSyncHttpResult.Success -> parsed.value
                else -> error("C_RELAY_DISCLOSURE_INVALID")
            }
        val existing = prefs.getString("key:$normalized", null)
        check(existing == null || existing == health.relayPublicKey) { "C_RELAY_KEY_MISMATCH" }
        return Offer(normalized, health.relayPublicKey, health.relayKeyId, bytes)
    }

    fun confirmKey(
        offer: Offer,
        explicitlyConfirmed: Boolean,
    ) {
        check(explicitlyConfirmed && prefs.contains("source:${offer.origin}")) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val existing = prefs.getString("key:${offer.origin}", null)
        check(existing == null || existing == offer.publicKey) { "C_RELAY_KEY_MISMATCH" }
        check(
            prefs
                .edit()
                .putString("key:${offer.origin}", offer.publicKey)
                .putString("key-id:${offer.origin}", offer.keyId)
                .commit(),
        ) {
            "C_LOCAL_STORAGE_FULL"
        }
    }

    fun currentReview(origin: String): Review {
        val offer = inspectRelay(origin)
        val pinned = prefs.getString("key:${offer.origin}", null) ?: error("C_BOOTSTRAP_TRUST_REQUIRED")
        check(pinned == offer.publicKey) { "C_RELAY_KEY_MISMATCH" }
        val base = offer.origin.toHttpUrl()
        val md = fetch(base, "/privacy.md", "text/markdown")
        val doc = fetch(base, "/privacy.json", "application/json")
        val validated =
            Json.decodeFromString<Validated>(
                PallaSyncCore.validatePolicy(
                    md,
                    doc,
                    offer.health,
                    offer.origin,
                    pinned,
                    prefs.getLong("revision:${offer.origin}", 0),
                    prefs.getString("hash:${offer.origin}", "").orEmpty(),
                    retry.correctedTime(offer.origin),
                ),
            )
        synchronized(consentLock) {
            val revision = prefs.getLong("revision:${offer.origin}", 0)
            val hash = prefs.getString("hash:${offer.origin}", "").orEmpty()
            check(
                validated.revision >= revision && (validated.revision != revision || hash.isEmpty() || validated.disclosureSha256 == hash),
            ) {
                "C_RELAY_DISCLOSURE_INVALID"
            }
            check(
                prefs
                    .edit()
                    .putLong(
                        "revision:${offer.origin}",
                        validated.revision,
                    ).putString("hash:${offer.origin}", validated.disclosureSha256)
                    .commit(),
            ) {
                "C_LOCAL_STORAGE_FULL"
            }
        }
        return Review(validated, md.decodeToString(throwOnInvalidSequence = true), doc.decodeToString(throwOnInvalidSequence = true))
    }

    internal fun trustedRelayKey(origin: String): String =
        prefs.getString("key:${normalizeOrigin(origin)}", null) ?: error("C_BOOTSTRAP_TRUST_REQUIRED")

    /** Re-fetch after the interactive wait. A changed pair is displayed again, never silently agreed. */
    fun accept(
        displayed: Review,
        explicitlyConfirmed: Boolean,
    ): Consent {
        check(explicitlyConfirmed) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        val previousConsent = prefs.getString("consent:${displayed.validated.relayOrigin}", null)
        val latest = currentReview(displayed.validated.relayOrigin)
        check(latest.validated == displayed.validated && latest.markdown == displayed.markdown) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        val v = latest.validated
        val consent =
            Consent(
                UUID.randomUUID().toString(),
                v.relayOrigin,
                v.relayKeyId,
                "${v.relayOrigin}/privacy.md",
                v.policySha256,
                "${v.relayOrigin}/privacy.json",
                v.revision,
                v.disclosureSha256,
                retry.correctedTime(v.relayOrigin),
                null,
            )
        synchronized(consentLock) {
            check(prefs.getString("consent:${v.relayOrigin}", null) == previousConsent) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
            check(
                prefs
                    .edit()
                    .putString("consent:${v.relayOrigin}", Json.encodeToString(consent))
                    .putString("record:${consent.consentId}", Json.encodeToString(consent))
                    .putString("markdown:${consent.consentId}", latest.markdown)
                    .putString("disclosure:${consent.consentId}", latest.disclosure)
                    .commit(),
            ) { "C_LOCAL_STORAGE_FULL" }
        }
        return consent
    }

    fun requireCurrent(
        origin: String,
        source: String? = null,
    ) {
        check(source == null || prefs.getString("source:${normalizeOrigin(origin)}", null) == source) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val review = currentReview(origin)
        synchronized(consentLock) {
            val consent = accepted(review.validated.relayOrigin) ?: error("C_RELAY_POLICY_CONSENT_REQUIRED")
            val v = review.validated
            check(
                consent.withdrawnAtMs == null && consent.relayKeyId == v.relayKeyId && consent.policySha256 == v.policySha256 &&
                    consent.disclosureRevision == v.revision && consent.disclosureSha256 == v.disclosureSha256,
            ) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        }
    }

    fun accepted(origin: String): Consent? =
        prefs.getString("consent:${normalizeOrigin(origin)}", null)?.let {
            Json.decodeFromString<Consent>(it)
        }

    fun acceptedReview(origin: String): Review? {
        val c = accepted(origin) ?: return null
        val md = prefs.getString("markdown:${c.consentId}", null)
        val doc = prefs.getString("disclosure:${c.consentId}", null)
        return if (md == null || doc == null) {
            null
        } else {
            Review(
                Validated(
                    c.relayOrigin,
                    c.relayKeyId,
                    c.disclosureRevision,
                    c.policySha256,
                    c.disclosureSha256,
                    Json.parseToJsonElement(doc) as JsonObject,
                ),
                md,
                doc,
            )
        }
    }

    fun withdraw(origin: String) {
        synchronized(consentLock) {
            val current = accepted(origin) ?: return
            val withdrawn = current.copy(withdrawnAtMs = retry.correctedTime(current.relayOrigin))
            check(
                prefs
                    .edit()
                    .putString("consent:${current.relayOrigin}", Json.encodeToString(withdrawn))
                    .putString("record:${current.consentId}", Json.encodeToString(withdrawn))
                    .commit(),
            ) { "C_LOCAL_STORAGE_FULL" }
            PallaSyncRemoteService.cancelOrigin(current.relayOrigin)
        }
    }

    // Policy/transport exceptions may embed remote content. Only a fixed code crosses this boundary.
    @Suppress("SwallowedException")
    private fun fetch(
        base: HttpUrl,
        path: String,
        mediaType: String,
    ): ByteArray {
        check(retry.remaining(PallaSyncUrls.extractOrigin(base)) == 0L) { "C_RELAY_POLICY_UNAVAILABLE" }
        val request =
            Request
                .Builder()
                .url(base.newBuilder().encodedPath(path).build())
                .header("Accept", mediaType)
                .get()
                .build()
        try {
            anonymous.newCall(request).execute().use { response ->
                if (response.code != HTTP_OK) fetchFailure(request, response)
                check(
                    response.code == HTTP_OK && response.request.url.scheme == "https" &&
                        PallaSyncUrls.extractOrigin(response.request.url) == PallaSyncUrls.extractOrigin(base),
                ) { "C_RELAY_DISCLOSURE_INVALID" }
                val body = response.body
                check(
                    body.contentType()?.let {
                        "${it.type}/${it.subtype}"
                    } == mediaType && body.contentLength() <= MAX_BYTES,
                ) { "C_RELAY_DISCLOSURE_INVALID" }
                val buffer = Buffer()
                val source = body.source()
                while (buffer.size <= MAX_BYTES &&
                    source.read(buffer, minOf(READ_CHUNK_BYTES, MAX_BYTES + 1 - buffer.size)) != -1L
                ) { /* bounded after Content-Encoding decoding */ }
                check(buffer.size <= MAX_BYTES) { "C_RELAY_DISCLOSURE_INVALID" }
                return buffer.readByteArray()
            }
        } catch (failure: IOException) {
            retry.observe(request, PallaSyncHttpResult.Retryable("C_RELAY_POLICY_UNAVAILABLE"))
            unavailablePolicy()
        } catch (failure: IllegalArgumentException) {
            unavailablePolicy()
        } catch (failure: IllegalStateException) {
            if (failure.message in setOf("C_RELAY_DISCLOSURE_INVALID", "C_LOCAL_STORAGE_FULL")) throw failure
            unavailablePolicy()
        }
    }

    private fun fetchFailure(
        request: Request,
        response: Response,
    ): Nothing {
        val hint =
            runCatching {
                check(response.header("Content-Type")?.substringBefore(';')?.trim() == "application/problem+json")
                Json.decodeFromString<PallaSyncProblemHint>(PallaSyncCore.validateProblem(response.pallaSyncBytes(), response.code))
            }.getOrNull()
        val outcome = classifyPallaSyncHttpStatus(response.code, hint)
        if (outcome is PallaSyncHttpResult.Retryable) {
            val wait =
                PallaSyncRetryGate.retryAfter(
                    response.header("Retry-After"),
                    response.code == HTTP_RATE_LIMITED,
                    hint?.serverTimeMs ?: retry.correctedTime(PallaSyncUrls.extractOrigin(request.url)),
                )
            retry.observe(request, outcome.copy(retryAfterMs = maxOf(wait, outcome.retryAfterMs)), hint, response.header("Date"))
            unavailablePolicy()
        }
        error("C_RELAY_DISCLOSURE_INVALID")
    }

    companion object {
        private fun unavailablePolicy(): Nothing = throw IllegalStateException("C_RELAY_POLICY_UNAVAILABLE")

        private const val MAX_BYTES = 1_048_576L
        private const val HTTP_OK = 200
        private const val HTTP_RATE_LIMITED = 429
        private const val READ_CHUNK_BYTES = 8192L
        private const val CONNECT_SECONDS = 12L
        private const val READ_SECONDS = 20L
        private const val CALL_SECONDS = 30L
        private val consentLock = Any()

        internal fun normalizeOrigin(origin: String): String {
            val url = origin.toHttpUrl()
            check(
                url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() &&
                    url.encodedPath == "/" && url.query == null && url.fragment == null,
            ) {
                "C_RELAY_POLICY_UNAVAILABLE"
            }
            return PallaSyncUrls.extractOrigin(url)
        }

        internal fun protocolHttpClient(isolatedTransport: OkHttpClient? = null): OkHttpClient =
            (isolatedTransport?.newBuilder() ?: OkHttpClient.Builder())
                .apply {
                    interceptors().clear()
                    networkInterceptors().clear()
                }.dispatcher(Dispatcher())
                .authenticator(Authenticator.NONE)
                .proxyAuthenticator(Authenticator.NONE)
                .cache(null)
                .cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false)
                .followSslRedirects(false)
                .connectionSpecs(
                    listOf(ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS).tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()),
                ).connectTimeout(CONNECT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_SECONDS, TimeUnit.SECONDS)
                .callTimeout(CALL_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}
