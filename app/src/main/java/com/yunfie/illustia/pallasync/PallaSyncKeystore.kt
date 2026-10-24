package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.util.UUID

private const val UUID_VERSION = 4
private const val RFC_UUID_VARIANT = 2
private const val KEY_BYTES = 32
private const val MAX_EPOCH = 0xffff_ffffL

/**
 * Snapshot of local device keys and active epoch keys.
 * Note: Per PallaSync Protocol 3.0 §5.5 (C-02), the root mnemonic, admin key,
 * and root_seed MUST NOT be persisted to non-volatile storage.
 */
data class PallaSyncKeySnapshot(
    val chainId: String? = null,
    val deviceId: String? = null,
    val encryptionKeyBase64Url: String, // record_key
    val signingKeyBase64Url: String, // device_signing_key (Ed25519)
    val publicKeyBase64Url: String, // device_public_key (Ed25519)
    val kexPrivateKeyBase64Url: String = "", // device_kex_key (X25519)
    val kexPublicKeyBase64Url: String = "", // device_kex_public_key (X25519)
    val deviceMetaKeyBase64Url: String = "", // device_meta_key
    val collectionTagKeyBase64Url: String = "",
    val epochSecretBase64Url: String = "",
    val epoch: Long = 0,
) {
    override fun toString(): String = "PallaSync protected device keys"
}

class PallaSyncKeystore internal constructor(
    private val sharedPrefs: SharedPreferences,
) {
    constructor(context: Context) :
        this(
            EncryptedSharedPreferences.create(
                context,
                "pallasync_keystore",
                PallaSyncPlatformKeys.master(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ),
        )

    fun saveDevicePrivateKey(seedBase64Url: String) {
        sharedPrefs.edit().putString("device_private_seed", seedBase64Url).apply()
    }

    fun getDevicePrivateKey(): String? = sharedPrefs.getString("device_private_seed", null)

    fun saveDeviceSignPublicKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_sign_public", keyBase64Url).apply()
    }

    fun getDeviceSignPublicKey(): String? = sharedPrefs.getString("device_sign_public", null)

    fun saveDeviceHpkePrivateKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_hpke_private", keyBase64Url).apply()
    }

    fun getDeviceHpkePrivateKey(): String? = sharedPrefs.getString("device_hpke_private", null)

    fun saveDeviceHpkePublicKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_hpke_public", keyBase64Url).apply()
    }

    fun getDeviceHpkePublicKey(): String? = sharedPrefs.getString("device_hpke_public", null)

    fun saveDeviceCertificate(certificateJson: String) {
        sharedPrefs.edit().putString("device_certificate", certificateJson).apply()
    }

    fun getDeviceCertificate(): String? = sharedPrefs.getString("device_certificate", null)

    internal fun pendingActivation(): String? = sharedPrefs.getString("activation_pending_chain", null)

    internal fun stageActivation(chain: String) {
        val existing = pendingActivation()
        check(existing == null || existing == chain) { "C_MISSING_DEPENDENCY" }
        check(sharedPrefs.edit().putString("activation_pending_chain", chain).commit() && pendingActivation() == chain) {
            "C_LOCAL_STORAGE_FULL"
        }
    }

    internal fun completeActivation(chain: String) {
        check(pendingActivation() == chain) { "C_MISSING_DEPENDENCY" }
        check(sharedPrefs.edit().remove("activation_pending_chain").commit()) { "C_LOCAL_STORAGE_FULL" }
    }

    internal fun pinInitialAuthority(
        chainId: String,
        hash: String,
    ): Boolean {
        val key = "authority.$chainId.hash"
        val previous = sharedPrefs.getString(key, null)
        check(previous == null || previous == hash) { "C_ROLLBACK_DETECTED" }
        return sharedPrefs.edit().putString(key, hash).commit() && authorityHash(chainId) == hash
    }

    internal fun authorityHash(chainId: String): String? = sharedPrefs.getString("authority.$chainId.hash", null)

    /** Retain only a successful comparison against an independently signed, locally verified device checkpoint. */
    internal fun retainIndependentCheckpointComparison(
        chainId: String,
        comparison: PallaSyncIndependentCheckpointComparison,
    ): Boolean {
        require(chainId.isNotBlank() && comparison.checkedAtMs > 0 && comparison.peerDeviceId.isNotBlank())
        require(comparison.generation in 0..MAX_EPOCH && comparison.dataSequence >= 0 && comparison.transparencyIndex >= 0)
        val key = "independent_checkpoint_comparison.$chainId"
        val value =
            buildJsonObject {
                put("checked_at_ms", JsonPrimitive(comparison.checkedAtMs))
                put("generation", JsonPrimitive(comparison.generation))
                put("peer_device_id", JsonPrimitive(comparison.peerDeviceId))
                put("data_sequence", JsonPrimitive(comparison.dataSequence))
                put("transparency_index", JsonPrimitive(comparison.transparencyIndex))
            }.toString()
        return sharedPrefs.edit().putString(key, value).commit() && independentCheckpointComparison(chainId) == comparison
    }

    internal fun independentCheckpointComparison(chainId: String): PallaSyncIndependentCheckpointComparison? =
        runCatching {
            val value =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(checkNotNull(sharedPrefs.getString("independent_checkpoint_comparison.$chainId", null)))
                    .jsonObject
            PallaSyncIndependentCheckpointComparison(
                checkedAtMs = value.getValue("checked_at_ms").jsonPrimitive.long,
                generation = value.getValue("generation").jsonPrimitive.long,
                peerDeviceId = value.getValue("peer_device_id").jsonPrimitive.content,
                dataSequence = value.getValue("data_sequence").jsonPrimitive.long,
                transparencyIndex = value.getValue("transparency_index").jsonPrimitive.long,
            ).takeIf {
                it.checkedAtMs > 0 && it.generation in 0..MAX_EPOCH && it.peerDeviceId.isNotBlank() &&
                    it.dataSequence >= 0 && it.transparencyIndex >= 0
            }
        }.getOrNull()

    internal fun authorityTransition(chain: String): JsonObject? =
        sharedPrefs.getString("authority.$chain.transition", null)?.let { Json.decodeFromString<JsonObject>(it) }

    internal fun stageAuthorityTransition(
        chain: String,
        previous: String,
        authority: JsonObject,
    ): Boolean {
        check(authorityHash(chain) == previous) { "C_ROLLBACK_DETECTED" }
        val value =
            kotlinx.serialization.json.buildJsonObject {
                put("previous_hash", kotlinx.serialization.json.JsonPrimitive(previous))
                put("authority", authority)
            }
        val saved = authorityTransition(chain)
        check(saved == null || saved == value) { "C_ROLLBACK_DETECTED" }
        return sharedPrefs.edit().putString("authority.$chain.transition", value.toString()).commit() && authorityTransition(chain) == value
    }

    internal fun finishAuthorityTransition(
        chain: String,
        hash: String,
    ): Boolean {
        val saved = checkNotNull(authorityTransition(chain)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.objectHash(saved.getValue("authority").toString()) == hash) { "C_ROLLBACK_DETECTED" }
        check(authorityHash(chain) in listOf(hash, saved.getValue("previous_hash").jsonPrimitive.content)) { "C_ROLLBACK_DETECTED" }
        return sharedPrefs
            .edit()
            .putString("authority.$chain.hash", hash)
            .remove("authority.$chain.transition")
            .commit() &&
            authorityHash(chain) == hash
    }

    internal fun transparencyPin(chainId: String): JsonObject? =
        sharedPrefs
            .getString("transparency.$chainId", null)
            ?.let { Json.decodeFromString<JsonObject>(it) }

    /** Latest signed relay head whose complete transparency suffix passed native verification. */
    internal fun verifiedTransparencyHead(chainId: String): JsonObject? =
        sharedPrefs
            .getString("transparency.verified_head.$chainId", null)
            ?.let { Json.decodeFromString<JsonObject>(it) }

    /** Last fully verified page within one signed target, used for O(unverified-suffix) restart. */
    internal fun transparencyProgress(chainId: String): JsonObject? =
        sharedPrefs
            .getString("transparency.progress.$chainId", null)
            ?.let { Json.decodeFromString<JsonObject>(it) }

    /** Durable restart point for re-reading a previously verified historical transparency range. */
    internal fun transparencyRecoveryProgress(chainId: String): JsonObject? =
        sharedPrefs
            .getString("transparency.recovery.progress.$chainId", null)
            ?.let { Json.decodeFromString<JsonObject>(it) }

    internal fun retainTransparencyRecoveryProgress(
        chainId: String,
        progress: JsonObject,
    ): Boolean {
        val head = progress["target_head"] as? JsonObject ?: error("C_TRANSPARENCY_INVALID")
        val anchor = progress["anchor"] as? JsonObject ?: error("C_TRANSPARENCY_INVALID")
        val headIndex = head["entry_index"]?.jsonPrimitive?.long ?: error("C_TRANSPARENCY_INVALID")
        val anchorIndex = anchor["index"]?.jsonPrimitive?.long ?: error("C_TRANSPARENCY_INVALID")
        check(
            head["type"]?.jsonPrimitive?.content == "PALLASYNC-TRANSPARENCY-HEAD-v3" &&
                head["chain_id"]?.jsonPrimitive?.content == chainId &&
                anchor["transparency_id"] == head["transparency_id"] &&
                anchorIndex in 0L..headIndex &&
                (anchorIndex != headIndex || anchor["hash"] == head["transparency_hash"]),
        ) { "C_TRANSPARENCY_INVALID" }
        val previous = transparencyRecoveryProgress(chainId)
        if (previous != null && previous["target_head"] == head) {
            val previousAnchor = previous["anchor"] as? JsonObject ?: error("C_TRANSPARENCY_INVALID")
            val previousIndex = previousAnchor["index"]?.jsonPrimitive?.long ?: error("C_TRANSPARENCY_INVALID")
            check(previousIndex <= anchorIndex) { "C_ROLLBACK_DETECTED" }
            if (previousIndex == anchorIndex) check(previousAnchor == anchor) { "C_TRANSPARENCY_INVALID" }
        } else {
            check(previous == null || recoveryProgressComplete(previous)) { "C_TRANSPARENCY_INVALID" }
        }
        return sharedPrefs.edit().putString("transparency.recovery.progress.$chainId", progress.toString()).commit() &&
            transparencyRecoveryProgress(chainId) == progress
    }

    internal fun clearTransparencyRecoveryProgress(
        chainId: String,
        head: JsonObject,
    ): Boolean {
        val progress = transparencyRecoveryProgress(chainId) ?: return true
        check(progress["target_head"] == head && recoveryProgressComplete(progress)) { "C_TRANSPARENCY_INVALID" }
        return sharedPrefs.edit().remove("transparency.recovery.progress.$chainId").commit() &&
            transparencyRecoveryProgress(chainId) == null
    }

    private fun recoveryProgressComplete(progress: JsonObject): Boolean {
        val head = progress["target_head"] as? JsonObject ?: return false
        val anchor = progress["anchor"] as? JsonObject ?: return false
        return anchor["index"] == head["entry_index"] && anchor["hash"] == head["transparency_hash"]
    }

    internal fun retainTransparencyProgress(
        chainId: String,
        progress: JsonObject,
    ): Boolean {
        val target = progress["target_head"] as? JsonObject ?: error("C_TRANSPARENCY_INVALID")
        val anchor = progress["anchor"] as? JsonObject ?: error("C_TRANSPARENCY_INVALID")
        val targetIndex = target["entry_index"]?.jsonPrimitive?.long ?: error("C_TRANSPARENCY_INVALID")
        val anchorIndex = anchor["index"]?.jsonPrimitive?.long ?: error("C_TRANSPARENCY_INVALID")
        check(
            target["type"]?.jsonPrimitive?.content == "PALLASYNC-TRANSPARENCY-HEAD-v3" &&
                target["chain_id"]?.jsonPrimitive?.content == chainId &&
                anchor["transparency_id"] == target["transparency_id"] &&
                anchorIndex in 0L..targetIndex &&
                (anchorIndex != targetIndex || anchor["hash"] == target["transparency_hash"]),
        ) { "C_TRANSPARENCY_INVALID" }
        val previous = transparencyProgress(chainId)
        if (previous != null && previous["target_head"] == target) {
            check(
                previous
                    .getValue("anchor")
                    .jsonObject
                    .getValue("index")
                    .jsonPrimitive.long <=
                    anchor.getValue("index").jsonPrimitive.long,
            ) { "C_ROLLBACK_DETECTED" }
            if (previous.getValue("anchor").jsonObject.getValue("index") == anchor.getValue("index")) {
                check(previous.getValue("anchor") == anchor) { "C_TRANSPARENCY_INVALID" }
            }
        } else {
            check(previous == null) { "C_TRANSPARENCY_INVALID" }
        }
        return sharedPrefs.edit().putString("transparency.progress.$chainId", progress.toString()).commit() &&
            transparencyProgress(chainId) == progress
    }

    /** Call only after the head signature and every transparency entry through its index were verified. */
    internal fun retainVerifiedTransparencyHead(
        chainId: String,
        head: JsonObject,
    ): Boolean {
        check(
            head["type"]?.jsonPrimitive?.content == "PALLASYNC-TRANSPARENCY-HEAD-v3" &&
                head["chain_id"]?.jsonPrimitive?.content == chainId,
        ) { "C_TRANSPARENCY_INVALID" }
        val previous = verifiedTransparencyHead(chainId)
        if (previous != null) {
            check(
                previous["transparency_id"] == head["transparency_id"] &&
                    previous["log_id"] == head["log_id"] &&
                    previous.getValue("entry_index").jsonPrimitive.long <= head.getValue("entry_index").jsonPrimitive.long &&
                    previous.getValue("min_available_seq").jsonPrimitive.long <= head.getValue("min_available_seq").jsonPrimitive.long,
            ) { "C_ROLLBACK_DETECTED" }
            if (previous.getValue("entry_index") == head.getValue("entry_index")) {
                check(previous == head) { "C_TRANSPARENCY_INVALID" }
            }
        }
        return sharedPrefs
            .edit()
            .putString("transparency.verified_head.$chainId", head.toString())
            .remove("transparency.progress.$chainId")
            .commit() &&
            verifiedTransparencyHead(chainId) == head
    }

    internal fun reseedSourceTransparencyPin(
        chain: String,
        sourceAuthorityHash: String,
    ): JsonObject? =
        sharedPrefs
            .getString("reseed.source.transparency.$chain.$sourceAuthorityHash", null)
            ?.let { Json.decodeFromString<JsonObject>(it) }

    /** A source checkpoint is trusted only when its hash was preserved during authenticated reseed promotion. */
    internal fun hasTrustedReseedSourceCheckpoint(
        chain: String,
        sourceAuthorityHash: String,
    ): Boolean = reseedSourceTransparencyPin(chain, sourceAuthorityHash) != null

    /** Retain an old verified cut while device identity replacement replays unresolved intents. */
    internal fun retainSourceTransparencyPin(
        chain: String,
        sourceAuthorityHash: String,
        anchor: JsonObject,
    ): Boolean {
        val key = "reseed.source.transparency.$chain.$sourceAuthorityHash"
        val previous = sharedPrefs.getString(key, null)
        check(previous == null || Json.decodeFromString<JsonObject>(previous) == anchor) { "C_ROLLBACK_DETECTED" }
        return sharedPrefs.edit().putString(key, anchor.toString()).commit() &&
            reseedSourceTransparencyPin(chain, sourceAuthorityHash) == anchor
    }

    internal fun pinInitialTransparency(
        chainId: String,
        anchor: JsonObject,
    ): Boolean {
        val previous = transparencyPin(chainId)
        check(previous == null || previous == anchor) { "C_ROLLBACK_DETECTED" }
        if (previous != null) return true
        return sharedPrefs
            .edit()
            .putString("transparency.$chainId", anchor.toString())
            .remove("transparency.verified_head.$chainId")
            .commit()
    }

    internal fun publishTransparencyPin(
        chainId: String,
        anchor: JsonObject,
    ): Boolean {
        val previous = checkNotNull(transparencyPin(chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(
            previous.getValue("transparency_id") == anchor.getValue("transparency_id") &&
                previous.getValue("index").jsonPrimitive.long <= anchor.getValue("index").jsonPrimitive.long,
        ) { "C_ROLLBACK_DETECTED" }
        if (previous.getValue("index") == anchor.getValue("index")) check(previous == anchor) { "C_TRANSPARENCY_INVALID" }
        return sharedPrefs.edit().putString("transparency.$chainId", anchor.toString()).commit() && transparencyPin(chainId) == anchor
    }

    fun saveDeviceId(deviceId: String) {
        sharedPrefs.edit().putString("device_id", deviceId).apply()
    }

    internal fun boundDeviceId(chainId: String): String? = sharedPrefs.getString("identity.$chainId", null)

    /** Call only after the exact signed enrollment acceptance has been verified. */
    internal fun pinInitialDeviceId(
        chainId: String,
        deviceId: String,
    ): Boolean {
        val previous = boundDeviceId(chainId)
        check(previous == null || previous == deviceId) { "C_DEVICE_EQUIVOCATION" }
        return sharedPrefs.edit().putString("identity.$chainId", deviceId).commit() && boundDeviceId(chainId) == deviceId
    }

    /** Missing immutable enrollment identity pauses work; it never generates a replacement (§7.4). */
    fun getDeviceId(): String? =
        sharedPrefs.getString("device_id", null)?.takeIf { id ->
            runCatching {
                UUID.fromString(id).let { it.version() == UUID_VERSION && it.variant() == RFC_UUID_VARIANT && it.toString() == id }
            }.getOrDefault(false)
        }

    fun saveEpochKey(epochKeyBase64Url: String) {
        sharedPrefs.edit().putString("epoch_key", epochKeyBase64Url).apply()
    }

    fun getEpochKey(): String? = sharedPrefs.getString("epoch_key", null)

    /** §12.1: only verified epoch secrets enter OS-protected storage; never root or recovery input. */
    internal fun retainEpochSecret(
        chain: String,
        epoch: Long,
        secret: ByteArray,
    ): Boolean =
        try {
            check(secret.size == KEY_BYTES && epoch in 0..MAX_EPOCH) { "C_SCHEMA_VIOLATION" }
            val key = "epoch.$chain.$epoch.secret"
            check(!sharedPrefs.getBoolean("epoch.$chain.$epoch.expired", false)) { "C_MISSING_DEPENDENCY" }
            val encoded =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(secret)
            val previous = sharedPrefs.getString(key, null)
            check(previous == null || previous == encoded) { "C_COMMITMENT_MISMATCH" }
            sharedPrefs.edit().putString(key, encoded).commit() && sharedPrefs.getString(key, null) == encoded
        } finally {
            secret.fill(0)
        }

    /** §19.4: replace an existing epoch secret only after a root-authorized envelope was verified. */
    internal fun replaceEpochMaterial(
        chain: String,
        epoch: Long,
        material: PallaSyncKeySnapshot,
        secret: ByteArray,
    ): Boolean =
        try {
            check(secret.size == KEY_BYTES && epoch in 0..MAX_EPOCH) { "C_SCHEMA_VIOLATION" }
            check(material.chainId == chain && material.epoch == epoch) { "C_COMMITMENT_MISMATCH" }
            val secretKey = "epoch.$chain.$epoch.secret"
            check(!sharedPrefs.getBoolean("epoch.$chain.$epoch.expired", false)) { "C_MISSING_DEPENDENCY" }
            val isActive =
                sharedPrefs.getString("active_chain_id", null) == chain &&
                    sharedPrefs.getLong("key_epoch_number", -1L) == epoch
            val previous =
                sharedPrefs.getString(secretKey, null)
                    ?: sharedPrefs.getString("epoch_secret", null).takeIf { isActive }
                    ?: error("C_MISSING_DEPENDENCY")
            val encoded =
                java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(secret)
            val editor = sharedPrefs.edit().putString(secretKey, encoded)
            if (isActive) {
                editor
                    .putString("epoch_secret", encoded)
                    .putString("epoch_key", material.encryptionKeyBase64Url)
                    .putString("collection_tag_key", material.collectionTagKeyBase64Url)
                    .putString("device_meta_key", material.deviceMetaKeyBase64Url)
            }
            val committed = editor.commit()
            committed && sharedPrefs.getString(secretKey, null) == encoded &&
                (!isActive || sharedPrefs.getString("epoch_secret", null) == encoded) &&
                (previous == encoded || sharedPrefs.getString(secretKey, null) != previous)
        } finally {
            secret.fill(0)
        }

    /** Delete only retained historical secrets after §13.5's complete-state handoff. */
    internal fun expireRetainedEpochSecrets(
        chain: String,
        epochs: Collection<Long>,
    ): Boolean {
        check(sharedPrefs.getString("active_chain_id", null) == chain) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val activeEpoch = sharedPrefs.getLong("key_epoch_number", -1L)
        val expired = epochs.distinct().sorted()
        check(expired.all { it in 0 until activeEpoch }) { "C_EPOCH_FORK" }
        if (expired.isEmpty()) return true
        val editor = sharedPrefs.edit()
        expired.forEach { epoch ->
            editor.remove("epoch.$chain.$epoch.secret")
            editor.putBoolean("epoch.$chain.$epoch.expired", true)
        }
        return editor.commit() &&
            expired.all { epoch ->
                sharedPrefs.getString("epoch.$chain.$epoch.secret", null) == null &&
                    sharedPrefs.getBoolean("epoch.$chain.$epoch.expired", false)
            }
    }

    internal fun expiredEpochs(chain: String): Set<Long> =
        sharedPrefs.all.keys
            .mapNotNull { key ->
                val prefix = "epoch.$chain."
                if (!key.startsWith(prefix) || !key.endsWith(".expired")) return@mapNotNull null
                key
                    .removePrefix(prefix)
                    .removeSuffix(".expired")
                    .toLongOrNull()
                    ?.takeIf { sharedPrefs.getBoolean(key, false) }
            }.toSet()

    internal fun keysForEpoch(
        keys: PallaSyncKeySnapshot,
        epoch: Long,
    ): PallaSyncKeySnapshot? {
        val chain = checkNotNull(keys.chainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        if (sharedPrefs.getBoolean("epoch.$chain.$epoch.expired", false)) return null
        val retained = sharedPrefs.getString("epoch.$chain.$epoch.secret", null)
        return when {
            retained != null -> keys.copy(epoch = epoch, epochSecretBase64Url = retained)
            keys.epoch == epoch && keys.epochSecretBase64Url.isNotEmpty() -> keys
            else -> null
        }
    }

    fun saveDeviceMetaKey(deviceMetaKeyBase64Url: String) {
        sharedPrefs.edit().putString("device_meta_key", deviceMetaKeyBase64Url).apply()
    }

    fun getDeviceMetaKey(): String? = sharedPrefs.getString("device_meta_key", null)

    /**
     * Commits all active-chain key material in one encrypted preference edit.
     * Mnemonic is never persisted per §5.5.
     */
    fun saveActiveChainKeys(snapshot: PallaSyncKeySnapshot): Boolean {
        require(snapshot.encryptionKeyBase64Url.isNotBlank()) { "encryption key is blank" }
        require(snapshot.signingKeyBase64Url.isNotBlank()) { "signing key is blank" }
        require(snapshot.publicKeyBase64Url.isNotBlank()) { "public key is blank" }

        val editor =
            sharedPrefs
                .edit()
                .remove("seed_phrase") // Explicitly purge any legacy stored mnemonic
                .putString("epoch_key", snapshot.encryptionKeyBase64Url)
                .putString("epoch_secret", snapshot.epochSecretBase64Url)
                .putString("collection_tag_key", snapshot.collectionTagKeyBase64Url)
                .putLong("key_epoch_number", snapshot.epoch)
                .putString("device_private_seed", snapshot.signingKeyBase64Url)
                .putString("device_sign_public", snapshot.publicKeyBase64Url)
        if (snapshot.kexPrivateKeyBase64Url.isNotBlank()) {
            editor.putString("device_hpke_private", snapshot.kexPrivateKeyBase64Url)
        }
        if (snapshot.kexPublicKeyBase64Url.isNotBlank()) {
            editor.putString("device_hpke_public", snapshot.kexPublicKeyBase64Url)
        }
        if (snapshot.deviceMetaKeyBase64Url.isNotBlank()) {
            editor.putString("device_meta_key", snapshot.deviceMetaKeyBase64Url)
        }
        if (snapshot.chainId.isNullOrBlank()) {
            editor.remove("active_chain_id")
        } else {
            editor.putString("active_chain_id", snapshot.chainId)
        }
        snapshot.deviceId?.let { editor.putString("device_id", it) }
        return editor.commit()
    }

    fun getActiveChainKeys(): PallaSyncKeySnapshot? {
        val encryptionKey = getEpochKey()?.takeIf { it.isNotBlank() } ?: return null
        val signingKey = getDevicePrivateKey()?.takeIf { it.isNotBlank() } ?: return null
        val publicKey = getDeviceSignPublicKey()?.takeIf { it.isNotBlank() } ?: return null
        return PallaSyncKeySnapshot(
            chainId = sharedPrefs.getString("active_chain_id", null),
            deviceId = getDeviceId(),
            encryptionKeyBase64Url = encryptionKey,
            signingKeyBase64Url = signingKey,
            publicKeyBase64Url = publicKey,
            kexPrivateKeyBase64Url = getDeviceHpkePrivateKey().orEmpty(),
            kexPublicKeyBase64Url = getDeviceHpkePublicKey().orEmpty(),
            deviceMetaKeyBase64Url = getDeviceMetaKey().orEmpty(),
            collectionTagKeyBase64Url = sharedPrefs.getString("collection_tag_key", "").orEmpty(),
            epochSecretBase64Url = sharedPrefs.getString("epoch_secret", "").orEmpty(),
            epoch = sharedPrefs.getLong("key_epoch_number", 0),
        )
    }

    /** Stages a candidate without overwriting the currently active chain keys. */
    fun savePendingChainKeys(
        snapshot: PallaSyncKeySnapshot,
        genesisRequest: String? = null,
        genesisOrigin: String? = null,
    ): Boolean {
        require(!snapshot.chainId.isNullOrBlank()) { "pending chain ID is blank" }
        require(!snapshot.deviceId.isNullOrBlank()) { "C_SCHEMA_VIOLATION" }
        val existing = getPendingChainKeys()
        check(existing == null || existing == snapshot) { "C_MISSING_DEPENDENCY" }
        val editor = sharedPrefs.edit()
        if (genesisRequest != null) {
            val origin = checkNotNull(genesisOrigin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val previous = pendingGenesisRequest(snapshot.chainId, origin)
            check(previous == null || previous == genesisRequest) { "C_RECORD_ID_CONFLICT" }
            editor.putString("pending_genesis_request", genesisRequest)
            editor.putString("pending_genesis_chain", snapshot.chainId)
            editor.putString("pending_genesis_origin", origin)
        }
        return editor
            .putString("pending_chain_id", snapshot.chainId)
            .putString("pending_device_id", snapshot.deviceId)
            .putString("pending_epoch_key", snapshot.encryptionKeyBase64Url)
            .putString("pending_signing_key", snapshot.signingKeyBase64Url)
            .putString("pending_public_key", snapshot.publicKeyBase64Url)
            .putString("pending_kex_private", snapshot.kexPrivateKeyBase64Url)
            .putString("pending_kex_public", snapshot.kexPublicKeyBase64Url)
            .putString("pending_meta_key", snapshot.deviceMetaKeyBase64Url)
            .putString("pending_epoch_secret", snapshot.epochSecretBase64Url)
            .putString("pending_tag_key", snapshot.collectionTagKeyBase64Url)
            .putLong("pending_epoch_number", snapshot.epoch)
            .commit()
    }

    /** Persist a candidate reseed identity separately from ordinary enrollment candidates. */
    internal fun savePendingReseed(
        snapshot: PallaSyncKeySnapshot,
        publicBundleJson: String,
        targetOrigin: String,
    ): Boolean {
        require(!snapshot.chainId.isNullOrBlank() && !snapshot.deviceId.isNullOrBlank()) { "C_SCHEMA_VIOLATION" }
        require(publicBundleJson.isNotBlank()) { "C_SCHEMA_VIOLATION" }
        val normalized = PallaSyncUrls.normalize(targetOrigin)
        check(normalized is PallaSyncHttpResult.Success && PallaSyncUrls.extractOrigin(normalized.value) == targetOrigin) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        resumePallaSyncPreparedReseed(publicBundleJson, snapshot).use { prepared ->
            check(prepared.keySnapshot() == snapshot) { "C_COMMITMENT_MISMATCH" }
        }
        val existing = pendingReseed()
        check(
            existing == null ||
                (existing.keys == snapshot && existing.publicBundleJson == publicBundleJson && existing.targetOrigin == targetOrigin),
        ) { "C_IMPORT_CONFLICT" }
        val editor =
            sharedPrefs
                .edit()
                .putString("reseed_pending_chain_id", snapshot.chainId)
                .putString("reseed_pending_device_id", snapshot.deviceId)
                .putString("reseed_pending_epoch_key", snapshot.encryptionKeyBase64Url)
                .putString("reseed_pending_signing_key", snapshot.signingKeyBase64Url)
                .putString("reseed_pending_public_key", snapshot.publicKeyBase64Url)
                .putString("reseed_pending_kex_private", snapshot.kexPrivateKeyBase64Url)
                .putString("reseed_pending_kex_public", snapshot.kexPublicKeyBase64Url)
                .putString("reseed_pending_meta_key", snapshot.deviceMetaKeyBase64Url)
                .putString("reseed_pending_epoch_secret", snapshot.epochSecretBase64Url)
                .putString("reseed_pending_tag_key", snapshot.collectionTagKeyBase64Url)
                .putLong("reseed_pending_epoch_number", snapshot.epoch)
                .putString("reseed_pending_bundle", publicBundleJson)
                .putString("reseed_pending_origin", targetOrigin)
        return editor.commit() && pendingReseed() == PallaSyncPendingReseed(snapshot, publicBundleJson, targetOrigin)
    }

    internal fun pendingReseed(): PallaSyncPendingReseed? {
        val chain = sharedPrefs.getString("reseed_pending_chain_id", null)?.takeIf(String::isNotBlank) ?: return null
        val keys =
            PallaSyncKeySnapshot(
                chainId = chain,
                deviceId = sharedPrefs.getString("reseed_pending_device_id", null)?.takeIf(String::isNotBlank) ?: return null,
                encryptionKeyBase64Url = sharedPrefs.getString("reseed_pending_epoch_key", null)?.takeIf(String::isNotBlank) ?: return null,
                signingKeyBase64Url = sharedPrefs.getString("reseed_pending_signing_key", null)?.takeIf(String::isNotBlank) ?: return null,
                publicKeyBase64Url = sharedPrefs.getString("reseed_pending_public_key", null)?.takeIf(String::isNotBlank) ?: return null,
                kexPrivateKeyBase64Url =
                    sharedPrefs.getString("reseed_pending_kex_private", null)?.takeIf(String::isNotBlank) ?: return null,
                kexPublicKeyBase64Url = sharedPrefs.getString("reseed_pending_kex_public", null)?.takeIf(String::isNotBlank) ?: return null,
                deviceMetaKeyBase64Url = sharedPrefs.getString("reseed_pending_meta_key", null)?.takeIf(String::isNotBlank) ?: return null,
                epochSecretBase64Url =
                    sharedPrefs
                        .getString(
                            "reseed_pending_epoch_secret",
                            null,
                        )?.takeIf(String::isNotBlank) ?: return null,
                collectionTagKeyBase64Url =
                    sharedPrefs
                        .getString(
                            "reseed_pending_tag_key",
                            null,
                        )?.takeIf(String::isNotBlank) ?: return null,
                epoch = sharedPrefs.getLong("reseed_pending_epoch_number", -1).takeIf { it >= 0 } ?: return null,
            )
        return PallaSyncPendingReseed(
            keys,
            sharedPrefs.getString("reseed_pending_bundle", null)?.takeIf(String::isNotBlank) ?: return null,
            sharedPrefs.getString("reseed_pending_origin", null)?.takeIf(String::isNotBlank) ?: return null,
        )
    }

    internal fun clearPendingReseed(): Boolean =
        sharedPrefs
            .edit()
            .remove("reseed_pending_chain_id")
            .remove("reseed_pending_device_id")
            .remove("reseed_pending_epoch_key")
            .remove("reseed_pending_signing_key")
            .remove("reseed_pending_public_key")
            .remove("reseed_pending_kex_private")
            .remove("reseed_pending_kex_public")
            .remove("reseed_pending_meta_key")
            .remove("reseed_pending_epoch_secret")
            .remove("reseed_pending_tag_key")
            .remove("reseed_pending_epoch_number")
            .remove("reseed_pending_bundle")
            .remove("reseed_pending_origin")
            .commit() && pendingReseed() == null

    /** Promote a fully acknowledged §17.5 identity while retaining the prior identity for recovery. */
    internal fun promotePendingReseed(
        chain: String,
        sourceDeviceId: String,
        sourceAuthorityHash: String,
        targetAuthorityHash: String,
        targetAnchor: JsonObject,
    ): Boolean {
        val pending = pendingReseed()
        val sourceAnchor = transparencyPin(chain)
        return if (pending == null) {
            isReseedAlreadyPromoted(chain, targetAuthorityHash, targetAnchor)
        } else if (sourceAnchor != null && isPendingReseedPromotionValid(pending, chain, sourceDeviceId, sourceAuthorityHash)) {
            writeReseedPromotion(pending, chain, sourceAuthorityHash, targetAuthorityHash, targetAnchor, sourceAnchor)
        } else {
            false
        }
    }

    private fun isReseedAlreadyPromoted(
        chain: String,
        targetAuthorityHash: String,
        targetAnchor: JsonObject,
    ): Boolean {
        val active = getActiveChainKeys()
        val deviceId = active?.deviceId
        return active != null && deviceId != null && active.chainId == chain && deviceId == boundDeviceId(chain) &&
            authorityHash(chain) == targetAuthorityHash && transparencyPin(chain) == targetAnchor
    }

    private fun isPendingReseedPromotionValid(
        pending: PallaSyncPendingReseed,
        chain: String,
        sourceDeviceId: String,
        sourceAuthorityHash: String,
    ): Boolean =
        pending.keys.chainId == chain && boundDeviceId(chain) == sourceDeviceId &&
            authorityHash(chain) == sourceAuthorityHash && archiveActiveChainKeys(pending.keys)

    private fun writeReseedPromotion(
        pending: PallaSyncPendingReseed,
        chain: String,
        sourceAuthorityHash: String,
        targetAuthorityHash: String,
        targetAnchor: JsonObject,
        sourceAnchor: JsonObject,
    ): Boolean {
        val targetDeviceId = pending.keys.deviceId ?: return false
        val editor =
            sharedPrefs
                .edit()
                .putString("active_chain_id", chain)
                .putString("device_id", targetDeviceId)
                .putString("identity.$chain", targetDeviceId)
                .putString("authority.$chain.hash", targetAuthorityHash)
                .putString("transparency.$chain", targetAnchor.toString())
                .remove("transparency.verified_head.$chain")
                .remove("transparency.progress.$chain")
                .putString("epoch_key", pending.keys.encryptionKeyBase64Url)
                .putString("epoch_secret", pending.keys.epochSecretBase64Url)
                .putString("collection_tag_key", pending.keys.collectionTagKeyBase64Url)
                .putLong("key_epoch_number", pending.keys.epoch)
                .putString("device_private_seed", pending.keys.signingKeyBase64Url)
                .putString("device_sign_public", pending.keys.publicKeyBase64Url)
                .putString("device_hpke_private", pending.keys.kexPrivateKeyBase64Url)
                .putString("device_hpke_public", pending.keys.kexPublicKeyBase64Url)
                .putString("device_meta_key", pending.keys.deviceMetaKeyBase64Url)
        editor.putString("reseed.source.transparency.$chain.$sourceAuthorityHash", sourceAnchor.toString())
        clearPendingReseedFrom(editor)
        return editor.commit() &&
            getActiveChainKeys() == pending.keys && boundDeviceId(chain) == targetDeviceId &&
            authorityHash(chain) == targetAuthorityHash && transparencyPin(chain) == targetAnchor && pendingReseed() == null
    }

    private fun clearPendingReseedFrom(editor: android.content.SharedPreferences.Editor) {
        listOf(
            "reseed_pending_chain_id",
            "reseed_pending_device_id",
            "reseed_pending_epoch_key",
            "reseed_pending_signing_key",
            "reseed_pending_public_key",
            "reseed_pending_kex_private",
            "reseed_pending_kex_public",
            "reseed_pending_meta_key",
            "reseed_pending_epoch_secret",
            "reseed_pending_tag_key",
            "reseed_pending_epoch_number",
            "reseed_pending_bundle",
            "reseed_pending_origin",
        ).forEach(editor::remove)
    }

    fun getPendingChainKeys(): PallaSyncKeySnapshot? {
        val chainId =
            sharedPrefs.getString("pending_chain_id", null)?.takeIf { it.isNotBlank() }
                ?: return null
        return PallaSyncKeySnapshot(
            chainId = chainId,
            deviceId = sharedPrefs.getString("pending_device_id", null),
            encryptionKeyBase64Url =
                sharedPrefs
                    .getString("pending_epoch_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            signingKeyBase64Url =
                sharedPrefs
                    .getString("pending_signing_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            publicKeyBase64Url =
                sharedPrefs
                    .getString("pending_public_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            kexPrivateKeyBase64Url = sharedPrefs.getString("pending_kex_private", "").orEmpty(),
            kexPublicKeyBase64Url = sharedPrefs.getString("pending_kex_public", "").orEmpty(),
            deviceMetaKeyBase64Url = sharedPrefs.getString("pending_meta_key", "").orEmpty(),
            collectionTagKeyBase64Url = sharedPrefs.getString("pending_tag_key", "").orEmpty(),
            epochSecretBase64Url = sharedPrefs.getString("pending_epoch_secret", "").orEmpty(),
            epoch = sharedPrefs.getLong("pending_epoch_number", 0),
        )
    }

    /** Contains signed public objects/ciphertext only; never a token or root input. */
    internal fun pendingGenesisRequest(
        chainId: String,
        origin: String,
    ): String? {
        if (getPendingChainKeys()?.chainId != chainId || sharedPrefs.getString("pending_genesis_chain", null) != chainId) return null
        check(sharedPrefs.getString("pending_genesis_origin", null) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return sharedPrefs.getString("pending_genesis_request", null)
    }

    internal fun savePendingEnrollment(
        snapshot: PallaSyncKeySnapshot,
        origin: String,
        body: String,
        ack: String,
        peerContext: String? = null,
    ): Boolean {
        check(savePendingChainKeys(snapshot) && getPendingChainKeys() == snapshot) { "C_LOCAL_STORAGE_FULL" }
        val previous = pendingEnrollment(snapshot.chainId!!, origin)
        check(previous == null || previous == (body to ack)) { "C_RECORD_ID_CONFLICT" }
        if (peerContext != null) {
            val previousContext = sharedPrefs.getString("pending_peer_context", null)
            check(previousContext == null || previousContext == peerContext) { "C_RECORD_ID_CONFLICT" }
        }
        return sharedPrefs
            .edit()
            .putString("pending_enrollment_origin", origin)
            .putString("pending_enrollment_body", body)
            .putString("pending_enrollment_ack", ack)
            .apply { if (peerContext != null) putString("pending_peer_context", peerContext) }
            .commit() &&
            pendingEnrollment(snapshot.chainId, origin) == (body to ack)
    }

    internal fun pendingPeerContext(
        chain: String,
        origin: String,
    ): String? {
        if (getPendingChainKeys()?.chainId != chain) return null
        check(sharedPrefs.getString("pending_enrollment_origin", null) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return sharedPrefs.getString("pending_peer_context", null)
    }

    internal fun pendingEnrollment(
        chain: String,
        origin: String,
    ): Pair<String, String>? {
        val body = sharedPrefs.getString("pending_enrollment_body", null)
        if (getPendingChainKeys()?.chainId != chain || body == null) {
            return null
        }
        check(sharedPrefs.getString("pending_enrollment_origin", null) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return body to checkNotNull(sharedPrefs.getString("pending_enrollment_ack", null)) { "C_MISSING_DEPENDENCY" }
    }

    /** Promotes a previously durable candidate after Room activation succeeds. */
    fun promotePendingChainKeys(): Boolean {
        val pending = getPendingChainKeys() ?: return false
        val deviceId = pending.deviceId
        return if (deviceId == null || boundDeviceId(checkNotNull(pending.chainId)) != deviceId || !archiveActiveChainKeys(pending)) {
            false
        } else {
            sharedPrefs
                .edit()
                .putString("active_chain_id", pending.chainId)
                .putString("device_id", deviceId)
                .putString("epoch_key", pending.encryptionKeyBase64Url)
                .putString("epoch_secret", pending.epochSecretBase64Url)
                .putString("collection_tag_key", pending.collectionTagKeyBase64Url)
                .putLong("key_epoch_number", pending.epoch)
                .putString("device_private_seed", pending.signingKeyBase64Url)
                .putString("device_sign_public", pending.publicKeyBase64Url)
                .putString("device_hpke_private", pending.kexPrivateKeyBase64Url)
                .putString("device_hpke_public", pending.kexPublicKeyBase64Url)
                .putString("device_meta_key", pending.deviceMetaKeyBase64Url)
                .remove("pending_chain_id")
                .remove("pending_device_id")
                .remove("pending_seed_phrase")
                .remove("pending_epoch_key")
                .remove("pending_signing_key")
                .remove("pending_public_key")
                .remove("pending_kex_private")
                .remove("pending_kex_public")
                .remove("pending_meta_key")
                .remove("pending_epoch_secret")
                .remove("pending_tag_key")
                .remove("pending_epoch_number")
                .remove("pending_genesis_request")
                .remove("pending_genesis_chain")
                .remove("pending_genesis_origin")
                .remove("pending_enrollment_origin")
                .remove("pending_enrollment_body")
                .remove("pending_enrollment_ack")
                .remove("pending_peer_context")
                .commit()
        }
    }

    /** Promote only after the replacement's signed enrollment, envelope readback and key ack verify. */
    @Suppress("ReturnCount", "ComplexCondition")
    internal fun promotePendingReplacementKeys(
        chain: String,
        oldDeviceId: String,
        newDeviceId: String,
        sourceAuthorityHash: String,
    ): Boolean {
        val pending = getPendingChainKeys() ?: return false
        val old = getActiveChainKeys() ?: return false
        if (pending.chainId != chain || pending.deviceId != newDeviceId || old.chainId != chain || old.deviceId != oldDeviceId) return false
        if (boundDeviceId(chain) != oldDeviceId || !archiveActiveChainKeys(pending)) return false
        val editor =
            sharedPrefs
                .edit()
                .putString("identity.$chain", newDeviceId)
                .putString("active_chain_id", chain)
                .putString("device_id", newDeviceId)
                .putString("epoch_key", pending.encryptionKeyBase64Url)
                .putString("epoch_secret", pending.epochSecretBase64Url)
                .putString("collection_tag_key", pending.collectionTagKeyBase64Url)
                .putLong("key_epoch_number", pending.epoch)
                .putString("device_private_seed", pending.signingKeyBase64Url)
                .putString("device_sign_public", pending.publicKeyBase64Url)
                .putString("device_hpke_private", pending.kexPrivateKeyBase64Url)
                .putString("device_hpke_public", pending.kexPublicKeyBase64Url)
                .putString("device_meta_key", pending.deviceMetaKeyBase64Url)
                .putString("replacement_pending_old.$chain", oldDeviceId)
                .putString("replacement_pending_source.$chain", sourceAuthorityHash)
        clearPendingChainKeysFrom(editor)
        return editor.commit() && boundDeviceId(chain) == newDeviceId && getActiveChainKeys() == pending && getPendingChainKeys() == null
    }

    internal fun pendingReplacementOldDevice(chain: String): String? = sharedPrefs.getString("replacement_pending_old.$chain", null)

    internal fun pendingReplacementSourceAuthorityHash(chain: String): String? =
        sharedPrefs.getString("replacement_pending_source.$chain", null)

    internal fun finishReplacement(chain: String): Boolean =
        sharedPrefs
            .edit()
            .remove("replacement_pending_old.$chain")
            .remove("replacement_pending_source.$chain")
            .commit() && pendingReplacementOldDevice(chain) == null && pendingReplacementSourceAuthorityHash(chain) == null

    private fun clearPendingChainKeysFrom(editor: SharedPreferences.Editor) {
        listOf(
            "pending_chain_id",
            "pending_device_id",
            "pending_seed_phrase",
            "pending_epoch_key",
            "pending_signing_key",
            "pending_public_key",
            "pending_kex_private",
            "pending_kex_public",
            "pending_meta_key",
            "pending_epoch_secret",
            "pending_tag_key",
            "pending_epoch_number",
            "pending_genesis_request",
            "pending_genesis_chain",
            "pending_genesis_origin",
            "pending_enrollment_origin",
            "pending_enrollment_body",
            "pending_enrollment_ack",
            "pending_peer_context",
        ).forEach(editor::remove)
    }

    /** Preserve the previous identity and keys until a verified migration resolves its outbox. */
    private fun archiveActiveChainKeys(candidate: PallaSyncKeySnapshot): Boolean {
        val previous = getActiveChainKeys()
        return when {
            previous == null -> sharedPrefs.getString("active_chain_id", null) == null
            previous.chainId == candidate.chainId && previous.deviceId == candidate.deviceId -> true
            previous.chainId == null || previous.deviceId == null -> false
            else -> persistArchivedKeys(previous)
        }
    }

    private fun persistArchivedKeys(previous: PallaSyncKeySnapshot): Boolean {
        val chain = checkNotNull(previous.chainId)
        val device = checkNotNull(previous.deviceId)
        val prefix = "archive.$chain.$device."
        val committed =
            sharedPrefs
                .edit()
                .putString(prefix + "record_key", previous.encryptionKeyBase64Url)
                .putString(prefix + "signing_key", previous.signingKeyBase64Url)
                .putString(prefix + "public_key", previous.publicKeyBase64Url)
                .putString(prefix + "kex_private_key", previous.kexPrivateKeyBase64Url)
                .putString(prefix + "kex_public_key", previous.kexPublicKeyBase64Url)
                .putString(prefix + "device_meta_key", previous.deviceMetaKeyBase64Url)
                .putString(prefix + "epoch_secret", previous.epochSecretBase64Url)
                .putString(prefix + "tag_key", previous.collectionTagKeyBase64Url)
                .putLong(prefix + "epoch_number", previous.epoch)
                .putString("archive.$chain.identity", device)
                .commit()
        return committed && getArchivedChainKeys(chain) == previous
    }

    // Missing a field must fail closed; no partial identity/key bundle may be returned.
    @Suppress("ReturnCount")
    internal fun getArchivedChainKeys(chainId: String): PallaSyncKeySnapshot? {
        val id = sharedPrefs.getString("archive.$chainId.identity", null) ?: return null
        val prefix = "archive.$chainId.$id."
        val record = sharedPrefs.getString(prefix + "record_key", null) ?: return null
        val signing = sharedPrefs.getString(prefix + "signing_key", null) ?: return null
        val public = sharedPrefs.getString(prefix + "public_key", null) ?: return null
        return PallaSyncKeySnapshot(
            chainId = chainId,
            deviceId = id,
            encryptionKeyBase64Url = record,
            signingKeyBase64Url = signing,
            publicKeyBase64Url = public,
            kexPrivateKeyBase64Url = sharedPrefs.getString(prefix + "kex_private_key", "").orEmpty(),
            kexPublicKeyBase64Url = sharedPrefs.getString(prefix + "kex_public_key", "").orEmpty(),
            deviceMetaKeyBase64Url = sharedPrefs.getString(prefix + "device_meta_key", "").orEmpty(),
            collectionTagKeyBase64Url = sharedPrefs.getString(prefix + "tag_key", "").orEmpty(),
            epochSecretBase64Url = sharedPrefs.getString(prefix + "epoch_secret", "").orEmpty(),
            epoch = sharedPrefs.getLong(prefix + "epoch_number", 0),
        )
    }

    fun clearPendingChainKeys(): Boolean =
        sharedPrefs
            .edit()
            .remove("pending_chain_id")
            .remove("pending_device_id")
            .remove("pending_seed_phrase")
            .remove("pending_epoch_key")
            .remove("pending_signing_key")
            .remove("pending_public_key")
            .remove("pending_kex_private")
            .remove("pending_kex_public")
            .remove("pending_meta_key")
            .remove("pending_epoch_secret")
            .remove("pending_tag_key")
            .remove("pending_epoch_number")
            .remove("pending_genesis_request")
            .remove("pending_genesis_chain")
            .remove("pending_genesis_origin")
            .remove("pending_peer_context")
            .commit()

    fun clearActiveChainKeys(): Boolean =
        sharedPrefs
            .edit()
            .remove("seed_phrase")
            .remove("epoch_key")
            .remove("epoch_secret")
            .remove("collection_tag_key")
            .remove("key_epoch_number")
            .remove("device_private_seed")
            .remove("device_sign_public")
            .remove("device_hpke_private")
            .remove("device_hpke_public")
            .remove("device_meta_key")
            .remove("active_chain_id")
            .commit()

    /** Remove secret material and trust pins only for the chain whose signed proof was retained. */
    internal fun clearVerifiedDeletedChain(chainId: String): Boolean {
        val editor = sharedPrefs.edit()
        if (sharedPrefs.getString("active_chain_id", null) == chainId) {
            listOf(
                "seed_phrase",
                "epoch_key",
                "epoch_secret",
                "collection_tag_key",
                "key_epoch_number",
                "device_private_seed",
                "device_sign_public",
                "device_hpke_private",
                "device_hpke_public",
                "device_meta_key",
                "active_chain_id",
                "device_certificate",
            ).forEach(editor::remove)
        }
        if (sharedPrefs.getString("pending_chain_id", null) == chainId) {
            sharedPrefs.all.keys
                .filter { it.startsWith("pending_") }
                .forEach(editor::remove)
        }
        listOf(
            "authority.$chainId.hash",
            "authority.$chainId.transition",
            "transparency.$chainId",
            "transparency.verified_head.$chainId",
            "transparency.progress.$chainId",
            "independent_checkpoint_comparison.$chainId",
            "identity.$chainId",
        ).forEach(editor::remove)
        sharedPrefs.all.keys
            .filter { it.startsWith("archive.$chainId.") }
            .forEach(editor::remove)
        return editor.commit() && sharedPrefs.getString("active_chain_id", null) != chainId &&
            sharedPrefs.getString("authority.$chainId.hash", null) == null
    }

    fun clearAllKeys() {
        val deviceId = sharedPrefs.getString("device_id", null)
        val editor = sharedPrefs.edit().clear()
        if (deviceId != null) {
            editor.putString("device_id", deviceId)
        }
        editor.commit()
    }
}

internal data class PallaSyncPendingReseed(
    val keys: PallaSyncKeySnapshot,
    val publicBundleJson: String,
    val targetOrigin: String,
)
