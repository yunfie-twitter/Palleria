package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncLogRecordDispositionTest {
    @Test
    fun `full log unsupported record reaches bounded recovery selection and retains original bytes`() =
        withStore { store ->
            val formatted = "{ \"record_id\": \"record\", \"encrypted_payload\": \"original\" }"
            val original = PallaSyncInboxEntity("chain", "record", 7, "pending_dependency", formatted, "C_MISSING_DEPENDENCY", 123)
            store.dao.insertInboxRecords(listOf(original))
            val entry = Json.parseToJsonElement(row.signedJson).jsonObject
            store.transaction { PallaSyncLogRecordDisposition(store).retain(row, entry, "C_UNSUPPORTED_SCHEMA") }
            val saved = original.copy(status = "unsupported_collection", quarantineReason = "C_UNSUPPORTED_SCHEMA")
            store.dao.unsupportedInboxPage("chain", 7, -1, "", 50) shouldBe listOf(saved)
            store.transaction { PallaSyncLogRecordDisposition(store).retain(row, entry, null) }
            store.dao.getInboxRecord("chain", "record") shouldBe saved.copy(status = "applied", quarantineReason = null)
        }

    @Test
    fun `verified repaired placement preserves the original value and receipt time`() =
        withStore { store ->
            val raw = "{\"record_id\":\"record\",\"encrypted_payload\":\"original\"}"
            val original = PallaSyncInboxEntity("chain", "record", 2, "unsupported_collection", raw, "C_UNSUPPORTED_SCHEMA", 123)
            store.dao.insertInboxRecords(listOf(original))
            store.transaction {
                PallaSyncLogRecordDisposition(store).retain(row, Json.parseToJsonElement(row.signedJson).jsonObject, "C_UNSUPPORTED_SCHEMA")
            }
            store.dao.getInboxRecord("chain", "record") shouldBe original.copy(relaySeq = 7)
        }

    @Test
    fun `outer commit failure rolls back newly retained disposition`() =
        withStore { store ->
            val entry = Json.parseToJsonElement(row.signedJson).jsonObject
            shouldThrow<IllegalStateException> {
                store.transaction {
                    PallaSyncLogRecordDisposition(store).retain(row, entry, "C_UNSUPPORTED_SCHEMA")
                    error("injected commit failure")
                }
            }
            store.dao.getInboxRecord("chain", "record") shouldBe null
        }

    @Test
    fun `a conflicting immutable value cannot be replaced by the full log outcome`() =
        withStore { store ->
            val original =
                PallaSyncInboxEntity("chain", "record", 7, "applied", "{\"record_id\":\"record\",\"encrypted_payload\":\"different\"}")
            store.dao.insertInboxRecords(listOf(original))
            shouldThrow<IllegalStateException> {
                store.transaction {
                    PallaSyncLogRecordDisposition(
                        store,
                    ).retain(row, Json.parseToJsonElement(row.signedJson).jsonObject, "C_UNSUPPORTED_SCHEMA")
                }
            }.message shouldBe "C_RECORD_ID_CONFLICT"
            store.dao.getInboxRecord("chain", "record") shouldBe original
        }

    private fun withStore(block: suspend (PallaSyncLocalStore) -> Unit) =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                block(PallaSyncLocalStore(context, database))
            } finally {
                database.close()
            }
        }

    private val row =
        LogEntryEntity(
            "chain",
            "log",
            7,
            "entry-hash",
            "{\"kind\":\"record\",\"object\":{\"record_id\":\"record\",\"encrypted_payload\":\"original\"}}",
            "{}",
            "retained",
        )
}
