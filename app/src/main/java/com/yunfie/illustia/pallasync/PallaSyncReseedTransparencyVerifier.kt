package com.yunfie.illustia.pallasync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.Request
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** §17.5: verify the target relay's entire signed transparency suffix before cutover. */
internal class PallaSyncReseedTransparencyVerifier(
    private val remote: PallaSyncRemoteService,
) {
    /** Durable checkpoint supplied only after its page and receipts were committed atomically. */
    data class Resume(
        val head: JsonObject,
        val anchor: JsonObject,
        val verifiedReceiptIndices: Set<Long>,
    )

    suspend fun verifySuffix(
        root: PallaSyncRootSession,
        base: HttpUrl,
        pin: JsonObject,
        expectedReceipts: JsonArray,
        persistVerifiedPage: suspend (
            items: List<JsonObject>,
            anchor: JsonObject,
            observedHead: JsonObject,
            expectedReceiptIndices: Set<Long>,
        ) -> Unit,
        resume: Resume? = null,
    ): PallaSyncHttpResult<JsonObject> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        check(expectedReceipts.isNotEmpty()) { "C_ACCEPTANCE_UNPROVEN" }
        val chainId = pin.getValue("chain_id").jsonPrimitive.content
        val origin = PallaSyncUrls.extractOrigin(base)
        check(pin.getValue("origin").jsonPrimitive.content == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val pinText = checkNotNull(PallaSyncCore.canonicalJson(pin.toString())) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val receipts = expectedReceipts.map { it.jsonObject }
        val receiptIndices = receipts.associateBy { it.getValue("transparency_index").jsonPrimitive.long }
        check(receiptIndices.size == receipts.size) { "C_ACCEPTANCE_UNPROVEN" }
        val transparencyId =
            receipts
                .first()
                .getValue("transparency_id")
                .jsonPrimitive.content
        val relayKeyId =
            receipts
                .first()
                .getValue("relay_key_id")
                .jsonPrimitive.content
        check(receipts.all { it.getValue("transparency_id").jsonPrimitive.content == transparencyId }) {
            "C_TRANSPARENCY_INVALID"
        }
        check(receipts.all { it.getValue("relay_key_id").jsonPrimitive.content == relayKeyId }) { "C_RELAY_KEY_MISMATCH" }

        val observed =
            when (val result = readHead(root, base, chainId, origin, pinText)) {
                is PallaSyncHttpResult.Success -> result.value
                else -> return result.failure()
            }
        check(
            observed.getValue("log_id").jsonPrimitive.content == pin.getValue("log_id").jsonPrimitive.content &&
                observed.getValue("transparency_id").jsonPrimitive.content == transparencyId,
        ) { "C_TRANSPARENCY_INVALID" }
        resume?.let {
            check(
                it.head == observed && it.anchor
                    .getValue("transparency_id")
                    .jsonPrimitive.content == transparencyId,
            ) {
                "C_ROLLBACK_DETECTED"
            }
        }
        val through = observed.getValue("entry_index").jsonPrimitive.long
        check(receiptIndices.keys.all { it in 1..through }) { "C_ACCEPTANCE_UNPROVEN" }
        val initialAnchor =
            buildJsonObject {
                put("transparency_id", transparencyId)
                put("index", 0)
                put("hash", pallaSyncTransparencyZero(chainId, transparencyId, relayKeyId))
            }
        val startAnchor = resume?.anchor ?: initialAnchor
        val startIndex = startAnchor.getValue("index").jsonPrimitive.long
        check(startIndex in 0..through) { "C_TRANSPARENCY_INVALID" }
        if (resume != null) {
            val startsFromVerifiedZero = startIndex == 0L && startAnchor == initialAnchor
            check(
                resume.verifiedReceiptIndices == receiptIndices.keys.filterTo(sortedSetOf()) { it <= startIndex } &&
                    (startsFromVerifiedZero || startIndex > 0L),
            ) { "C_ACCEPTANCE_UNPROVEN" }
        }
        val receiptMatches = resume?.verifiedReceiptIndices?.toMutableSet() ?: mutableSetOf()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        var nextAnchor = startAnchor
        do {
            currentCoroutineContext().ensureActive()
            val url = PallaSyncUrls.transparencyEntries(base, chainId, transparencyId, startIndex, through, cursor)
            val token =
                checkNotNull(root.token(origin, "GET", url.encodedPath, query = url.encodedQuery.orEmpty())) {
                    "C_RELAY_POLICY_CONSENT_REQUIRED"
                }
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", PALLASYNC_MEDIA_TYPE)
                    .header("Authorization", "PallaSync $token")
                    .get()
                    .build()
            val page =
                when (
                    val result =
                        runInterruptible(Dispatchers.IO) {
                            remote.execute(request) { response ->
                                PallaSyncHttpResult.Success(
                                    Json
                                        .parseToJsonElement(
                                            PallaSyncCore.validateTransparencyPage(
                                                response.pallaSyncBytes(),
                                                pinText,
                                                nextAnchor.toString(),
                                                observed.toString(),
                                                PALLASYNC_PAGE_SIZE,
                                            ),
                                        ).jsonObject,
                                )
                            }
                        }
                ) {
                    is PallaSyncHttpResult.Success -> result.value
                    else -> return result.failure()
                }
            val response = page.getValue("response").jsonObject
            val items = response.getValue("items").jsonArray.map { it.jsonObject }
            val pageAnchor = nextAnchor
            var itemAnchor = pageAnchor
            items.forEach { entry ->
                val index = entry.getValue("entry_index").jsonPrimitive.long
                receiptIndices[index]?.let { receipt ->
                    val receiptPin =
                        checkNotNull(
                            PallaSyncCore.canonicalJson(
                                buildJsonObject {
                                    pin.forEach { (name, value) -> put(name, value) }
                                    put("log_id", receipt.getValue("log_id"))
                                }.toString(),
                            ),
                        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    check(
                        entry.getValue("operation").jsonPrimitive.content == "accept" &&
                            entry.getValue("chain_id").jsonPrimitive.content == chainId &&
                            entry.getValue("object_hash").jsonPrimitive.content == receipt.getValue("object_hash").jsonPrimitive.content &&
                            entry["relay_seq"] == receipt["relay_seq"] &&
                            entry.getValue("resulting_head_hash") == receipt.getValue("resulting_head_hash") &&
                            PallaSyncCore.validateReceiptInclusion(receipt.toString(), entry.toString(), receiptPin, itemAnchor.toString()),
                    ) { "C_ACCEPTANCE_UNPROVEN" }
                    check(receiptMatches.add(index)) { "C_TRANSPARENCY_INVALID" }
                }
                itemAnchor = advanceTransparencyAnchor(itemAnchor, entry)
            }
            nextAnchor = page.getValue("next_anchor").jsonObject
            check(itemAnchor == nextAnchor) { "C_TRANSPARENCY_INVALID" }
            persistVerifiedPage(items, nextAnchor, observed, receiptIndices.keys)
            cursor =
                response
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seenCursors.add(checkNotNull(cursor))) { "C_TRANSPARENCY_INVALID" }
        } while (cursor != null)
        check(nextAnchor.getValue("index").jsonPrimitive.long == through && receiptMatches == receiptIndices.keys) {
            "C_ACCEPTANCE_UNPROVEN"
        }
        return PallaSyncHttpResult.Success(
            buildJsonObject {
                put("anchor", nextAnchor)
                put("head", observed)
            },
        )
    }

    private fun advanceTransparencyAnchor(
        anchor: JsonObject,
        entry: JsonObject,
    ): JsonObject {
        val index = entry.getValue("entry_index").jsonPrimitive.long
        check(
            anchor.getValue("transparency_id").jsonPrimitive.content == entry.getValue("transparency_id").jsonPrimitive.content &&
                index == anchor.getValue("index").jsonPrimitive.long + 1,
        ) { "C_TRANSPARENCY_INVALID" }
        val prior = Base64.getUrlDecoder().decode(anchor.getValue("hash").jsonPrimitive.content)
        val entryHash = Base64.getUrlDecoder().decode(checkNotNull(PallaSyncCore.objectHash(entry.toString())))
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("PALLASYNC-TRANSPARENCY-LINK-v3\u0000".toByteArray(Charsets.US_ASCII))
        digest.update(prior)
        digest.update(ByteBuffer.allocate(8).putLong(index).array())
        digest.update(entryHash)
        return buildJsonObject {
            put("transparency_id", anchor.getValue("transparency_id"))
            put("index", index)
            put("hash", Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()))
        }
    }

    private suspend fun readHead(
        root: PallaSyncRootSession,
        base: HttpUrl,
        chainId: String,
        origin: String,
        pinJson: String,
    ): PallaSyncHttpResult<JsonObject> {
        val url = PallaSyncUrls.transparencyHead(base, chainId)
        val token =
            checkNotNull(root.token(origin, "GET", url.encodedPath)) {
                "C_RELAY_POLICY_CONSENT_REQUIRED"
            }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .get()
                .build()
        return runInterruptible(Dispatchers.IO) {
            remote.execute(request) { response ->
                PallaSyncHttpResult.Success(
                    Json.parseToJsonElement(PallaSyncCore.observeTransparencyHead(response.pallaSyncBytes(), pinJson)).jsonObject,
                )
            }
        }
    }

    private fun PallaSyncHttpResult<*>.failure(): PallaSyncHttpResult<JsonObject> =
        when (this) {
            PallaSyncHttpResult.Gone -> PallaSyncHttpResult.Gone
            is PallaSyncHttpResult.ProtocolError -> this
            is PallaSyncHttpResult.Retryable -> this
            is PallaSyncHttpResult.Success -> error("C_SCHEMA_VIOLATION")
        }
}

/** Spec §7.12.2 initial transparency anchor. */
internal fun pallaSyncTransparencyZero(
    chainId: String,
    transparencyId: String,
    relayKeyId: String,
): String {
    fun decode(value: String): ByteArray =
        runCatching {
            val bytes = Base64.getUrlDecoder().decode(value)
            check(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value)
            bytes
        }.getOrElse { error("C_SCHEMA_VIOLATION") }

    val chain = decode(chainId)
    val relayKey = decode(relayKeyId)
    check(chain.size == 32 && relayKey.size == 32) { "C_SCHEMA_VIOLATION" }
    val uuid =
        runCatching { UUID.fromString(transparencyId) }.getOrNull()
            ?: error("C_SCHEMA_VIOLATION")
    check(uuid.version() == 4 && uuid.variant() == 2 && uuid.toString() == transparencyId) { "C_SCHEMA_VIOLATION" }
    val uuidBytes =
        ByteBuffer
            .allocate(16)
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("PALLASYNC-TRANSPARENCY-v3\u0000".toByteArray(Charsets.US_ASCII))
    digest.update(chain)
    digest.update(uuidBytes)
    digest.update(relayKey)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
}
