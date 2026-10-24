package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Two-phase retained transparency-anchor interop on the isolated HTTPS relay (§§11.5, 17.9). */
@RunWith(AndroidJUnit4::class)
class PallaSyncTransparencyAnchorInteropTest {
    @Test
    @PallaSyncHarnessOnly
    fun retainedVerifiedAnchorStartsTheNextSuffixRange() =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val origin = args.getString("relay_origin")
            assumeTrue("Disposable relay arguments are required", origin?.startsWith("https://127.0.0.1:") == true)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val client = testTransport(checkNotNull(args.getString("relay_ca_base64")))
            val selected = checkNotNull(origin)
            val databaseName = "pallasync-transparency-anchor-resume-test.db"
            val afterAppend = args.getString("transparency_anchor_resume_after_append") == "true"
            if (!afterAppend) {
                context.deleteDatabase(databaseName)
                // Reset before opening EncryptedSharedPreferences: clearing its raw file after
                // construction would discard the keysets while this process kept using them.
                context
                    .getSharedPreferences("pallasync_policy", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                context
                    .getSharedPreferences("pallasync_epoch_journal", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
            }
            val database = Room.databaseBuilder(context, PallaSyncDatabase::class.java, databaseName).build()
            val keys = PallaSyncKeystore(context)
            val policy = PallaSyncPolicyGate(context, client)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                if (!afterAppend) {
                    keys.clearAllKeys()
                    SettingsStore(context).apply {
                        setPallaSyncEnabledFromCoordinator(false)
                        writeSyncedCollections(SyncedCollectionsSnapshot(searchHistory = listOf("Python interop")))
                    }
                    policy.chooseSource(selected, "relay_full_replay", true)
                    policy.confirmKey(policy.inspectRelay(selected), true)
                    policy.accept(policy.currentReview(selected), true)
                    PallaSyncCore.setSecretForeground(true)
                    val coordinator = PalleriaSyncCoordinator(client, context, scope, policy, PallaSyncLocalStore(context, database))
                    val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
                    val joined = coordinator.joinChain(phrase, "Transparency anchor interop", selected, bootstrapReview = { true })
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), joined)
                    coordinator.stopBackgroundSync()
                    scope.coroutineContext.job.cancelAndJoin()
                    val state = checkNotNull(database.pallaSyncDao().getActiveChainState())
                    val head = checkNotNull(keys.verifiedTransparencyHead(state.chainId))
                    val index = head.getValue("entry_index").jsonPrimitive.long
                    assertTrue("The initial verified anchor must be non-zero", index > 0)
                    report("pallasync_transparency_anchor_resume_baseline", index.toString())
                    report("pallasync_transparency_anchor_resume_baseline_hash", head.getValue("transparency_hash").jsonPrimitive.content)
                    return@runBlocking
                }

                val localStore = PallaSyncLocalStore(context, database)
                val state = checkNotNull(database.pallaSyncDao().getActiveChainState())
                val authorityText = checkNotNull(localStore.protocolDao().currentAuthority(state.chainId))
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val authorityPin = authority.getValue("pin").jsonObject
                assertEquals("The saved chain origin changed", origin, authorityPin.getValue("origin").jsonPrimitive.content)
                assertEquals(
                    "The saved relay key binding changed",
                    authorityPin.getValue("public_key").jsonPrimitive.content,
                    policy.trustedRelayKey(selected),
                )
                assertEquals("The saved authority hash changed", PallaSyncCore.objectHash(authorityText), keys.authorityHash(state.chainId))
                val beforeIndex =
                    checkNotNull(keys.verifiedTransparencyHead(state.chainId))
                        .getValue("entry_index")
                        .jsonPrimitive.long
                assertEquals(args.getString("transparency_anchor_before")?.toLong(), beforeIndex)
                client
                    .newCall(
                        Request
                            .Builder()
                            .url("$selected/__test__/clear-transparency-observations")
                            .post(ByteArray(0).toRequestBody(null))
                            .build(),
                    ).execute()
                    .use { assertEquals(200, it.code) }
                PallaSyncCore.setSecretForeground(true)
                val coordinator = PalleriaSyncCoordinator(client, context, scope, policy, localStore)
                assertEquals("The configured server changed after restart", selected, coordinator.getServerUrl())
                val resumed = coordinator.resumeBoundChain(startWorker = false)
                assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), resumed)
                val remainingMs = policy.retry.remaining(PallaSyncUrls.extractOrigin(selected.toHttpUrl()))
                if (remainingMs > 0) delay(remainingMs + 10)
                if (args.getString("transparency_anchor_cancel_resume") == "true") {
                    cancelAfterFirstSuffixPage(coordinator, keys, state.chainId, beforeIndex)
                }
                val syncSucceeded = coordinator.syncNow()
                assertTrue(
                    "sync=$syncSucceeded logs=${PalleriaSyncCoordinator.syncLogs.value.joinToString("\n")}",
                    syncSucceeded,
                )
                val afterHead = checkNotNull(keys.verifiedTransparencyHead(state.chainId))
                val afterIndex = afterHead.getValue("entry_index").jsonPrimitive.long
                assertTrue("The independently appended signed suffix was not verified", afterIndex > beforeIndex)
                val status = checkNotNull(coordinator.getClientStatus())
                assertEquals(afterIndex, status.verifiedTransparencyIndex)
                assertEquals(afterIndex, status.observedTransparencyTargetIndex)
                report("pallasync_transparency_anchor_resume", "$beforeIndex:$afterIndex")
                report("pallasync_transparency_anchor_resume_hash", afterHead.getValue("transparency_hash").jsonPrimitive.content)
            } finally {
                scope.coroutineContext.job.cancelAndJoin()
                database.close()
            }
        }

    private suspend fun cancelAfterFirstSuffixPage(
        coordinator: PalleriaSyncCoordinator,
        keys: PallaSyncKeystore,
        chain: String,
        beforeIndex: Long,
    ) = coroutineScope {
        val checkpointReached = CompletableDeferred<Unit>()
        val firstIndex = beforeIndex + 1
        val targetIndex = beforeIndex + SUFFIX_RECORDS
        val before = checkNotNull(coordinator.getClientStatus())
        val comparisonBefore = before.lastConsistencyProbeAtMs
        coordinator.setTransparencyProbeAfterCheckpointForTest {
            val status = checkNotNull(coordinator.getClientStatus())
            assertEquals(firstIndex, status.verifiedTransparencyIndex)
            assertEquals(targetIndex, status.observedTransparencyTargetIndex)
            assertEquals(before.downloadedSeq, status.downloadedSeq)
            assertEquals(before.verifiedSeq, status.verifiedSeq)
            assertEquals(before.materializedSeq, status.materializedSeq)
            assertEquals(before.writeState, status.writeState)
            checkpointReached.complete(Unit)
            awaitCancellation()
        }
        val pending = async(Dispatchers.IO) { coordinator.syncNow() }
        try {
            withTimeout(CHECKPOINT_TIMEOUT_MS) { checkpointReached.await() }
            pending.cancelAndJoin()
            assertTrue(pending.isCancelled)
            assertEquals(firstIndex, checkNotNull(keys.transparencyPin(chain)).getValue("index").jsonPrimitive.long)
            assertEquals(beforeIndex, checkNotNull(keys.verifiedTransparencyHead(chain)).getValue("entry_index").jsonPrimitive.long)
            val progress = checkNotNull(keys.transparencyProgress(chain))
            assertEquals(
                firstIndex,
                progress
                    .getValue("anchor")
                    .jsonObject
                    .getValue("index")
                    .jsonPrimitive.long,
            )
            assertEquals(
                targetIndex,
                progress
                    .getValue("target_head")
                    .jsonObject
                    .getValue("entry_index")
                    .jsonPrimitive.long,
            )
            assertEquals(comparisonBefore, checkNotNull(coordinator.getClientStatus()).lastConsistencyProbeAtMs)
            assertFalse(
                "Cancellation must not become a schema/protocol error",
                PalleriaSyncCoordinator.syncLogs.value.any { it.contains("C_SCHEMA_VIOLATION") || it.contains("Manual sync failed") },
            )
            report("pallasync_transparency_anchor_cancel", "$firstIndex:$targetIndex")
        } finally {
            pending.cancelAndJoin()
            coordinator.setTransparencyProbeAfterCheckpointForTest {}
        }
    }

    private fun report(
        key: String,
        value: String,
    ) {
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString(key, value) })
        println("$key=$value")
    }

    private fun testTransport(encoded: String): OkHttpClient {
        val certificates =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificates(Base64.decode(encoded, Base64.DEFAULT).inputStream())
        val trust =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                certificates.forEachIndexed { index, certificate -> setCertificateEntry("disposable-test-$index", certificate) }
            }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }.trustManagers
        val manager = managers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
        return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, manager).build()
    }

    private companion object {
        const val SUFFIX_RECORDS = 3L
        const val CHECKPOINT_TIMEOUT_MS = 120_000L
    }
}
