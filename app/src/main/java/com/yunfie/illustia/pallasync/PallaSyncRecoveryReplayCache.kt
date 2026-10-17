package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/** §7.11.5: only a successfully authenticated packet is remembered; no decrypted secret is cached.
 * Exact wire-byte retry returns its original public/ciphertext reply after the secret pair has ended.
 */
internal class PallaSyncRecoveryReplayCache<T> {
    data class Cached<T>(
        val value: T,
    )

    private data class Fingerprint(
        val bytes: String,
        val id: String,
        val direction: String,
    )

    private val ids = mutableMapOf<String, String>()
    private val sequences = mutableMapOf<String, String>()
    private val replies = mutableMapOf<String, Cached<T>>()

    @Synchronized
    fun lookup(packet: String): Cached<T>? {
        val fingerprint = fingerprint(packet)
        check(
            ids[fingerprint.id]?.let { it == fingerprint.bytes } != false &&
                sequences[fingerprint.direction]?.let { it == fingerprint.bytes } != false,
        ) { "C_RECOVERY_SHARE_INVALID" }
        return replies[fingerprint.bytes]
    }

    @Synchronized
    fun remember(
        packet: String,
        reply: T,
    ) {
        val fingerprint = fingerprint(packet)
        check(lookup(packet) == null) { "C_RECOVERY_SHARE_INVALID" }
        check(replies.size < MAX_PACKETS) { "C_LOCAL_LIMIT" }
        ids[fingerprint.id] = fingerprint.bytes
        sequences[fingerprint.direction] = fingerprint.bytes
        replies[fingerprint.bytes] = Cached(reply)
    }

    @Synchronized
    fun clear() {
        ids.clear()
        sequences.clear()
        replies.clear()
    }

    private fun fingerprint(packet: String): Fingerprint {
        // Use the native strict parser before the convenience JSON view can discard duplicate keys.
        checkNotNull(PallaSyncCore.objectHash(packet)) { "C_RECOVERY_SHARE_INVALID" }
        val public = Json.parseToJsonElement(packet) as? JsonObject ?: error("C_RECOVERY_SHARE_INVALID")

        fun field(name: String): String = (public[name] as? JsonPrimitive)?.content ?: error("C_RECOVERY_SHARE_INVALID")
        check(field("type") == "PALLASYNC-RECOVERY-TRANSFER-v3" && field("version") == "3.0") { "C_RECOVERY_SHARE_INVALID" }
        val bytes = MessageDigest.getInstance("SHA-256").digest(strictRootUtf8(packet)).joinToString("") { "%02x".format(it) }
        val direction = field("session_id") + "/" + field("sender_descriptor_hash") + "/" + field("message_seq")
        return Fingerprint(bytes, field("transfer_id"), direction)
    }

    companion object {
        private const val MAX_PACKETS = 1024
    }
}
