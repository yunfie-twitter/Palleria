package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** §12.1/§13.7: protected public journal closes the Room/OS-pin crash boundary. */
internal class PallaSyncAuthorityCommit(
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val fault: (Boundary) -> Unit = {},
) {
    enum class Boundary { STAGED, ROOM_COMMITTED, PIN_COMMITTED }

    suspend fun recover(chain: String) {
        val saved = keys.authorityTransition(chain) ?: return
        val authority = saved.getValue("authority").jsonObject
        val previous = saved.getValue("previous_hash").jsonPrimitive.content
        val current = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.objectHash(current) in listOf(previous, hash(authority))) { "C_ROLLBACK_DETECTED" }
        commitRoom(chain, authority)
        check(keys.finishAuthorityTransition(chain, hash(authority))) { "C_LOCAL_STORAGE_FULL" }
    }

    suspend fun install(
        chain: String,
        previous: String,
        authority: JsonObject,
    ) {
        check(PallaSyncCore.objectHash(checkNotNull(store.protocolDao().currentAuthority(chain))) == previous) { "C_ROLLBACK_DETECTED" }
        check(keys.stageAuthorityTransition(chain, previous, authority)) { "C_LOCAL_STORAGE_FULL" }
        fault(Boundary.STAGED)
        commitRoom(chain, authority)
        fault(Boundary.ROOM_COMMITTED)
        check(keys.finishAuthorityTransition(chain, hash(authority))) { "C_LOCAL_STORAGE_FULL" }
        fault(Boundary.PIN_COMMITTED)
    }

    private suspend fun commitRoom(
        chain: String,
        authority: JsonObject,
    ) = store.transaction {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        val parameters = authority.getValue("parameters").jsonObject
        val epoch =
            authority
                .getValue("epochs")
                .jsonObject.values
                .maxBy {
                    it.jsonObject
                        .getValue("record")
                        .jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long
                }.jsonObject
                .getValue("record")
                .jsonObject
        val generation = parameters.getValue("generation").jsonPrimitive.long
        val number = epoch.getValue("epoch").jsonPrimitive.long
        check(generation == state.generation) { "C_MISSING_DEPENDENCY" }
        store.protocolDao().commitAuthority(
            AuthorityHistoryEntity(chain, "checkpoint", hash(authority), generation, authority.toString(), "current_checkpoint"),
        )
        store.dao.updateChainState(
            state.copy(
                parametersHash = hash(parameters),
                epochHash = hash(epoch),
                writeState =
                    if (number == state.keyEpoch ||
                        state.writeState in setOf("integrity_paused", "deletion_unverified", "archived", "device_revoked")
                    ) {
                        state.writeState
                    } else {
                        "epoch_pending_keys"
                    },
            ),
        )
    }

    private fun hash(value: JsonObject): String = checkNotNull(PallaSyncCore.objectHash(value.toString())) { "C_SCHEMA_VIOLATION" }
}
