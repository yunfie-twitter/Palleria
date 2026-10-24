package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §11.2/§19.3: publish full-log record outcomes to the same durable inbox used by recovery UI. */
internal class PallaSyncLogRecordDisposition(
    private val store: PallaSyncLocalStore,
) {
    /** Caller verified the complete log range/authority and owns its effects/progress transaction. */
    suspend fun retain(
        row: LogEntryEntity,
        entry: JsonObject,
        code: String?,
    ) {
        if (entry.getValue("kind").jsonPrimitive.content != "record") return
        val record = entry.getValue("object").jsonObject
        val recordId = record.getValue("record_id").jsonPrimitive.content
        val raw = record.toString()
        val previous = store.dao.getInboxRecord(row.chainId, recordId)
        if (previous != null) {
            val objectHash = checkNotNull(PallaSyncCore.objectHash(raw)) { "C_SCHEMA_VIOLATION" }
            check(PallaSyncCore.objectHash(previous.rawRecordJson) == objectHash) {
                "C_RECORD_ID_CONFLICT"
            }
        }
        val original = previous?.rawRecordJson ?: raw
        val status = if (code == null) PallaSyncInboxEntity.STATUS_APPLIED else validationState(code)
        if (previous == null) {
            store.dao.insertInboxRecords(listOf(PallaSyncInboxEntity(row.chainId, recordId, row.relaySeq, status, original, code)))
        } else {
            check(store.dao.retainLogInboxDisposition(row.chainId, recordId, original, row.relaySeq, status, code) == 1) {
                "C_ROLLBACK_DETECTED"
            }
        }
    }
}
