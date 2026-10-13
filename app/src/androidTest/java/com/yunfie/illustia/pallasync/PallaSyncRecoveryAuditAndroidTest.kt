package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64
import java.util.UUID

/** Actual Android JNI/Room/protected preferences. Failure injection reopens stores; it is not a process-kill test. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryAuditAndroidTest {
    @Test
    fun uncertainRoomHandoffRetainsOneAuditOccurrenceAndOriginalTime() =
        runBlocking<Unit> {
            ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use {
                PallaSyncCore.setSecretForeground(true)
                val context = ApplicationProvider.getApplicationContext<Context>()
                val prefs = protectedPrefs(context)
                assertTrue("Test-owned public preferences must be empty", prefs.all.isEmpty())
                val db = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                try {
                    val recovery = PallaSyncRecoverySetStore(prefs)
                    val entry = prepare(recovery)
                    val chain =
                        entry.manifest
                            .getValue("chain_id")
                            .toString()
                            .trim('"')
                    val store = PallaSyncLocalStore(context, db)
                    store.dao.updateChainState(
                        ChainStateEntity(chain, 0, 0, "{}", logId = UUID.randomUUID().toString(), writeState = "integrity_paused"),
                    )
                    val keys =
                        PallaSyncKeySnapshot(
                            chain,
                            UUID.randomUUID().toString(),
                            encryptionKeyBase64Url = encode(ByteArray(32) { 2 }),
                            signingKeyBase64Url = encode(ByteArray(32) { 1 }),
                            publicKeyBase64Url = "public-test",
                        )
                    val author =
                        PallaSyncLocalAuthor(store, PallaSyncAuthorJournal(ProtectedJournal(prefs)), epochTag = {
                            _,
                            _,
                            _,
                            ->
                            null
                        })
                    val intents = PallaSyncLocalIntents(store, author) { _, events -> events }
                    val event = recoveryAudit(entry, "recovery_ceremony_started", "started").copy(createdAtMs = 42)
                    recovery.auditQueue.enqueue(PallaSyncRecoveryAuditQueue.Pending(chain, event))
                    assertTrue(
                        runCatching {
                            recovery.auditQueue.drain(chain) {
                                intents.stageRecoveryAudit(chain, it)
                                error("Injected after Room commit")
                            }
                        }.isFailure,
                    )
                    assertEquals(event, PallaSyncRecoveryAuditQueue(protectedPrefs(context)).pending(chain).single().event)
                    PallaSyncRecoveryAuditQueue(protectedPrefs(context)).drain(chain) {
                        intents.stageRecoveryAudit(chain, it)
                        true
                    }
                    assertTrue(recovery.auditQueue.pending(chain).isEmpty())
                    store.dao.updateChainState(checkNotNull(store.dao.getChainState(chain)).copy(writeState = "active"))
                    intents.resume(chain, keys)
                    intents.stageRecoveryAudit(chain, event)
                    intents.resume(chain, keys)
                    val record = store.dao.queuedBatch(chain, 200).single()
                    assertEquals(1L, store.dao.getChainState(chain)?.authorSeq)
                    PallaSyncDeviceSession.begin(keys).use { session ->
                        val inner =
                            Json.decodeFromString<PallaSyncInnerRecord>(
                                PallaSyncCore.decryptOwnPreparedRecord(session.handle, record.eventJson),
                            )
                        assertEquals(event.entityId, inner.operations.single().entityId)
                        assertEquals(42L, inner.operations.single().createdAtMs)
                        assertEquals(event.body, inner.operations.single().body)
                        assertFalse(
                            inner.operations
                                .single()
                                .body
                                .toString()
                                .contains("encrypted_blob"),
                        )
                    }
                } finally {
                    db.close()
                    assertTrue(prefs.edit().clear().commit())
                    PallaSyncCore.setSecretForeground(false)
                }
            }
        }

    private fun protectedPrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            "pallasync-recovery-audit-native-test",
            PallaSyncPlatformKeys.master(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun prepare(store: PallaSyncRecoverySetStore): PallaSyncRecoverySetStore.Entry {
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        return PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
            val holders =
                JsonArray(
                    List(3) {
                        buildJsonObject {
                            put("holder_kind", "offline")
                            put("holder_device_id", JsonNull)
                            put("holder_instance_id", UUID.randomUUID().toString())
                        }
                    },
                )
            val prepared =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.prepareRecoverySet(
                            root.handle,
                            root.operation.wire,
                            0,
                            2,
                            holders.toString(),
                            System.currentTimeMillis(),
                        ),
                    ).jsonObject
            store.create(
                prepared.getValue("manifest").toString(),
                prepared.getValue("status_chain").toString(),
                root.identity.adminPublicKey,
            )
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private class ProtectedJournal(
        private val prefs: SharedPreferences,
    ) : PallaSyncAuthorJournal.Storage {
        override fun read(key: String): String? = prefs.getString("test-author.$key", null)

        override fun commit(
            values: Map<String, String>,
            removed: Set<String>,
        ): Boolean {
            val editor = prefs.edit()
            values.forEach { (key, value) -> editor.putString("test-author.$key", value) }
            removed.forEach { editor.remove("test-author.$it") }
            return editor.commit()
        }
    }
}
