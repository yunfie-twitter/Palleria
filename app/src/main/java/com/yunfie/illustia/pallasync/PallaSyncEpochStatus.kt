package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Local public status for an explicit foreground action; never relay-visible fields. */
@Serializable
data class PallaSyncEpochStatus(
    val chainId: String,
    val deviceId: String,
    val knownEpoch: Long,
    val storedEpoch: Long,
    val writeState: String,
    val scheduledRotationMs: Long,
    val rotationDue: Boolean,
    val deviceRotationAllowed: Boolean,
    val approvedDeviceIds: List<String>,
)

internal class PallaSyncEpochStatusReader(
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val now: (String) -> Long,
) {
    /** §13.6: record due state without starting a root session or stopping ordinary writes. */
    suspend fun read(chain: String): PallaSyncEpochStatus {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        val raw = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.objectHash(raw) == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        val authority = Json.parseToJsonElement(raw).jsonObject
        val epochs = authority.getValue("epochs").jsonObject
        val latest =
            epochs.values
                .maxBy {
                    it.jsonObject
                        .getValue("record")
                        .jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long
                }.jsonObject
        val epoch = latest.getValue("record").jsonObject
        val epochHash = checkNotNull(PallaSyncCore.objectHash(epoch.toString())) { "C_SCHEMA_VIOLATION" }
        check(state.epochHash == epochHash) { "C_EPOCH_FORK" }
        val policy =
            authority
                .getValue("parameters")
                .jsonObject
                .getValue("policy")
                .jsonObject
        val interval = policy.getValue("scheduled_rotation_ms").jsonPrimitive.long
        val committed = latest.getValue("committed_at_ms").jsonPrimitive.long
        val timestamp =
            now(
                authority
                    .getValue("pin")
                    .jsonObject
                    .getValue("origin")
                    .jsonPrimitive.content,
            )
        val status =
            PallaSyncEpochStatus(
                chain,
                checkNotNull(keys.boundDeviceId(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" },
                epoch.getValue("epoch").jsonPrimitive.long,
                state.keyEpoch,
                state.writeState,
                interval,
                interval > 0 && timestamp >= committed && timestamp - committed >= interval,
                policy.getValue("allow_device_rotation").jsonPrimitive.content == "true",
                approvedIds(authority, state.generation),
            )
        if (status.rotationDue) {
            store.transaction {
                val id = "rotation-due:$chain:$epochHash"
                if (store.protocolDao().migrationChunk(id, 0) == null) {
                    store.protocolDao().putMigrationChunk(
                        MigrationJournalEntity(id, 0, epochHash, epochHash, chain, "rotation_due", Json.encodeToString(status)),
                    )
                }
            }
        }
        return status
    }

    private fun approvedIds(
        authority: JsonObject,
        generation: Long,
    ) = authority
        .getValue("devices")
        .jsonObject
        .filterValues {
            val admission = it.jsonObject
            admission.getValue("revoked_seq").jsonPrimitive.content == "null" &&
                admission
                    .getValue("record")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long == generation
        }.keys
        .sorted()
}
