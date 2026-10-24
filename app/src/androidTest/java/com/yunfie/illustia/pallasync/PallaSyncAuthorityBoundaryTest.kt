package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android OS wrapping + JNI + Room; public Python-signed fixture only. */
@RunWith(AndroidJUnit4::class)
class PallaSyncAuthorityBoundaryTest {
    @Test
    fun eachAuthorityPublicationBoundaryRecoversTheSameAuthenticatedHead() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val fixture =
                InstrumentationRegistry.getInstrumentation().context.assets.open("pallasync/python-genesis-acceptance.json").use {
                    Json.parseToJsonElement(it.reader(Charsets.UTF_8).readText()).jsonObject
                }
            val checked =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.validateGenesisAcceptance(
                            fixture.getValue("created").toString().toByteArray(),
                            fixture.getValue("genesis").toString(),
                            fixture.getValue("identity").toString(),
                            fixture.getValue("origin").jsonPrimitive.content,
                            fixture.getValue("relay_public_key").jsonPrimitive.content,
                        ),
                    ).jsonObject
            val before = checked.getValue("authority").jsonObject
            val entry =
                fixture
                    .getValue("record_upload")
                    .jsonObject
                    .getValue("accepted")
                    .jsonArray
                    .single()
                    .jsonObject
                    .getValue("receipt")
            val receipt =
                fixture
                    .getValue("record_upload")
                    .jsonObject
                    .getValue("upload_receipts")
                    .jsonArray
                    .single()
            val after =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.advanceAuthorityHistory(before.toString(), entry.toString(), receipt.toString()),
                    ).jsonObject
            val chain =
                fixture
                    .getValue("identity")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content
            val previousHash = checkNotNull(PallaSyncCore.objectHash(before.toString()))
            val nextHash = checkNotNull(PallaSyncCore.objectHash(after.toString()))
            val keys = PallaSyncKeystore(context)
            for (boundary in PallaSyncAuthorityCommit.Boundary.entries) {
                keys.clearAllKeys()
                val database = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                val store = PallaSyncLocalStore(context, database)
                try {
                    store.dao.updateChainState(ChainStateEntity(chain, 0, 0, "{}", writeState = "active"))
                    store.protocolDao().commitAuthority(
                        AuthorityHistoryEntity(chain, "checkpoint", previousHash, 0, before.toString(), "current_checkpoint"),
                    )
                    assertTrue(keys.pinInitialAuthority(chain, previousHash))
                    val failing = PallaSyncAuthorityCommit(store, keys) { if (it == boundary) error("Injected authority interruption") }
                    assertTrue(runCatching { failing.install(chain, previousHash, after) }.isFailure)
                    val reopened = PallaSyncKeystore(context)
                    if (boundary == PallaSyncAuthorityCommit.Boundary.ROOM_COMMITTED) {
                        assertEquals(previousHash, reopened.authorityHash(chain))
                        assertEquals(after.toString(), store.protocolDao().currentAuthority(chain))
                    }
                    PallaSyncAuthorityCommit(store, reopened).recover(chain)
                    assertEquals(nextHash, reopened.authorityHash(chain))
                    assertEquals(after.toString(), store.protocolDao().currentAuthority(chain))
                    assertEquals(null, reopened.authorityTransition(chain))
                } finally {
                    database.close()
                    keys.clearAllKeys()
                }
            }
        }
}
