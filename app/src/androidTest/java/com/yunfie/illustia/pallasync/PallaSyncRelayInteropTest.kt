package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.QuarantineEvidenceEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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

/** Actual coordinator/JNI/Room/Keystore -> isolated HTTPS relay -> independent Python receiver.
 * Run only with the disposable harness arguments; absence is a skipped test, never a passing IT.
 */
@RunWith(AndroidJUnit4::class)
class PallaSyncRelayInteropTest {
    @Test
    fun mnemonicEnrollmentAndInitialSyncUseTheRealRelay() =
        runBlocking<Unit> {
            val args = InstrumentationRegistry.getArguments()
            val origin = args.getString("relay_origin")
            assumeTrue("Disposable relay arguments are required", origin?.startsWith("https://127.0.0.1:") == true)
            val client = testTransport(checkNotNull(args.getString("relay_ca_base64")))
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val keys = PallaSyncKeystore(context)
            keys.clearAllKeys()
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
            val settings =
                com.yunfie.illustia.settings
                    .SettingsStore(context)
            settings.setPallaSyncEnabledFromCoordinator(false)
            settings.writeSyncedCollections(
                com.yunfie.illustia.settings
                    .SyncedCollectionsSnapshot(searchHistory = listOf("Python interop")),
            )
            val policy = PallaSyncPolicyGate(context, client)
            val store = PallaSyncLocalStore(context, database)
            val requestedFault = args.getString("rebase_fault")?.takeIf { it.isNotEmpty() }
            var faultInjected = false
            val coordinator =
                PalleriaSyncCoordinator(client, context, scope, policy, store) {
                    if (!faultInjected && requestedFault == it.name) {
                        faultInjected = true
                        error("Injected durable rebase boundary")
                    }
                }
            try {
                val selected = checkNotNull(origin)
                policy.chooseSource(selected, "relay_full_replay", true)
                policy.confirmKey(policy.inspectRelay(selected), true)
                policy.accept(policy.currentReview(selected), true)
                PallaSyncCore.setSecretForeground(true)
                val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
                assertTrue(
                    PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"),
                    coordinator.joinChain(
                        phrase,
                        "Android integration endpoint",
                        selected,
                        bootstrapReview = { review ->
                            assertEquals(selected, review.relayOrigin)
                            assertTrue(review.chainId.isNotBlank())
                            assertTrue(review.adminPublicKey.isNotBlank())
                            assertTrue(review.logId.isNotBlank())
                            assertTrue(review.headSeq >= 0)
                            assertTrue(review.headHash.isNotBlank())
                            assertTrue(review.parametersHash.isNotBlank())
                            assertTrue(review.epochHash.isNotBlank())
                            true
                        },
                    ),
                )
                coordinator.stopBackgroundSync()
                val state = checkNotNull(store.dao.getActiveChainState())
                val active = checkNotNull(keys.getActiveChainKeys())
                var activeDeviceId = active.deviceId
                var replacedDeviceId: String? = null
                assertEquals("active", state.writeState)
                assertEquals(active.deviceId, keys.boundDeviceId(state.chainId))
                assertEquals(null, keys.getPendingChainKeys())
                assertTrue(state.initialPullCompleted)
                assertTrue(state.verifiedSeq >= 9 && state.materializedSeq >= 9)
                assertTrue(
                    checkNotNull(store.protocolDao().entity(state.chainId, SEARCH_HISTORY_SCHEMA_V3, "Python interop")).winner.lamport >
                        1_000_000L,
                )
                exerciseMissingDependencyQuarantineRetry(coordinator, store, state.chainId)
                exerciseLocalArtifactBoundary(coordinator, store, state.chainId)
                exerciseBoundOrigin(client, policy, state.chainId, selected)
                args.getString("peer_transfer_id")?.let { transferId ->
                    PallaSyncPeerRelayBoundary(context, store, keys, policy, client).exercise(selected.toHttpUrl(), transferId)
                }
                exercisePausedEdits(coordinator, client, context, scope, policy, store, settings, state.chainId, active.deviceId)
                if (args.getString("replace_device") == "true") {
                    val pendingQuery = "Replacement pending"
                    assertTrue(
                        coordinator.enqueueDataEvent(
                            SEARCH_HISTORY_SCHEMA_V3,
                            pendingQuery,
                            "upsert",
                            buildJsonObject { put("query", pendingQuery) },
                        ),
                    )
                    replacedDeviceId = active.deviceId
                    assertTrue(
                        PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"),
                        coordinator.replaceCurrentDevice(phrase),
                    )
                    val replacement = checkNotNull(keys.getActiveChainKeys())
                    activeDeviceId = replacement.deviceId
                    assertTrue(replacement.deviceId != replacedDeviceId)
                    assertEquals(1, replacement.epoch)
                    assertEquals(null, keys.pendingReplacementOldDevice(state.chainId))
                    assertEquals(1L, store.dao.getChainState(state.chainId)?.authorSeq)
                    val authority = checkNotNull(store.protocolDao().currentAuthority(state.chainId))
                    val devices =
                        Json
                            .parseToJsonElement(authority)
                            .jsonObject
                            .getValue("devices")
                            .jsonObject
                    assertEquals(
                        "null",
                        devices
                            .getValue(checkNotNull(replacement.deviceId))
                            .jsonObject
                            .getValue("revoked_seq")
                            .jsonPrimitive.content,
                    )
                    assertTrue(
                        devices
                            .getValue(checkNotNull(replacedDeviceId))
                            .jsonObject
                            .getValue("revoked_seq")
                            .jsonPrimitive.content != "null",
                    )
                    assertTrue(store.dao.pendingOutboxCount(state.chainId) > 0L)
                }
                assertTrue(
                    coordinator.enqueueDataEvent(
                        SEARCH_HISTORY_SCHEMA_V3,
                        "Android interop",
                        "upsert",
                        buildJsonObject {
                            put("query", "Android interop")
                        },
                    ),
                )
                assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
                if (args.getString("rotate_epoch") == "true" && args.getString("replace_device") != "true") {
                    exerciseEpochRotation(
                        coordinator,
                        keys,
                        store,
                        context,
                        phrase,
                        state.chainId,
                        requestedFault != null,
                    ) { faultInjected }
                }
                assertTrue(store.dao.queuedBatch(state.chainId, PALLASYNC_PAGE_SIZE).isEmpty())
                val applied = checkNotNull(store.dao.getChainState(state.chainId))
                assertEquals(applied.downloadedSeq, applied.verifiedSeq)
                assertEquals(applied.verifiedSeq, applied.materializedSeq)
                assertEquals(null, keys.authorityTransition(state.chainId))
                assertEquals(
                    keys.authorityHash(state.chainId),
                    PallaSyncCore.objectHash(checkNotNull(store.protocolDao().currentAuthority(state.chainId))),
                )
                InstrumentationRegistry.getInstrumentation().sendStatus(
                    0,
                    Bundle().apply {
                        putString("pallasync_device_id", activeDeviceId)
                        replacedDeviceId?.let { putString("pallasync_replaced_old_device", it) }
                        putString("pallasync_local_boundary", "verified")
                        putString("pallasync_bound_origin", "verified")
                        args.getString("peer_transfer_id")?.let { putString("pallasync_peer_approval", "verified") }
                    },
                )
            } finally {
                coordinator.stopBackgroundSync()
                scope.cancel()
                database.close()
                keys.clearAllKeys()
                context
                    .getSharedPreferences("pallasync_epoch_journal", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                PallaSyncCore.endSecretSessions()
            }
        }

    private suspend fun exerciseLocalArtifactBoundary(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        chain: String,
    ) {
        val before = checkNotNull(store.dao.getChainState(chain))
        val outbox = store.dao.queuedBatch(chain, 200)
        val journal = store.protocolDao().lastMigrationIndex(PallaSyncLocalIntents.journalId(chain))
        val artifacts =
            listOf(
                "PALLASYNC-RECOVERY-BLOB-v3",
                "PALLASYNC-RECOVERY-SET-v3",
                "PALLASYNC-RECOVERY-STATUS-v3",
                "PALLASYNC-RECOVERY-SHARE-v3",
                "PALLASYNC-RECOVERY-PACKAGE-v3",
                "PALLASYNC-OFFLINE-SHARE-KEY-v3",
                "PALLASYNC-RECOVERY-SESSION-v3",
                "PALLASYNC-RECOVERY-TRANSFER-v3",
            ).map { kind -> buildJsonObject { put("nested", buildJsonObject { put("type", kind) }) } } +
                buildJsonObject {
                    put("mnemonic", "public fixture only")
                    put("passphrase", "")
                } +
                buildJsonObject {
                    val encoded =
                        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                            "{\"type\":\"PALLASYNC-RECOVERY-PACKAGE-v3\"}".toByteArray(Charsets.UTF_8),
                        )
                    put("file", "PSR3:$encoded")
                }
        for (body in artifacts) {
            var persisted = false
            val failure =
                runCatching {
                    coordinator.enqueueDataEventsThen(
                        listOf(
                            PallaSyncPendingEvent(
                                SEARCH_HISTORY_SCHEMA_V3,
                                "boundary fixture",
                                "upsert",
                                buildJsonObject { put("query", "boundary fixture") },
                            ),
                            PallaSyncPendingEvent(SEARCH_HISTORY_SCHEMA_V3, "forbidden fixture", "upsert", body),
                        ),
                    ) { persisted = true }
                }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals("C_SCHEMA_VIOLATION", failure?.message)
            assertFalse(persisted)
            assertEquals(before.authorSeq, store.dao.getChainState(chain)?.authorSeq)
            assertEquals(outbox, store.dao.queuedBatch(chain, 200))
            assertEquals(journal, store.protocolDao().lastMigrationIndex(PallaSyncLocalIntents.journalId(chain)))
        }
    }

    private suspend fun exerciseMissingDependencyQuarantineRetry(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        chain: String,
    ) {
        val event = checkNotNull(store.protocolDao().firstAcceptedEvent(chain))
        val originalHash = checkNotNull(PallaSyncCore.objectHash(event.signedJson))
        assertEquals(event.objectHash, originalHash)
        val inbox = checkNotNull(store.dao.getInboxRecord(chain, event.recordId))
        assertEquals("applied", inbox.status)
        store.snapshotDatabase().openHelper.writableDatabase.execSQL(
            "UPDATE pallasync_inbox SET status='quarantined',quarantine_reason='C_MISSING_DEPENDENCY' WHERE chain_id=? AND record_id=?",
            arrayOf(chain, event.recordId),
        )
        store.protocolDao().quarantine(
            QuarantineEvidenceEntity(chain, event.objectHash, "C_MISSING_DEPENDENCY", event.signedJson, "unresolved"),
        )

        assertTrue(
            PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"),
            coordinator.retryMissingDependencyQuarantine(event.objectHash),
        )
        assertEquals("applied", store.dao.getInboxRecord(chain, event.recordId)?.status)
        val resolved = checkNotNull(store.protocolDao().quarantineEvidence(chain, event.objectHash))
        assertEquals(event.signedJson, resolved.evidenceJson)
        assertEquals("resolved", resolved.resolutionState)
    }

    private fun exerciseBoundOrigin(
        client: OkHttpClient,
        policy: PallaSyncPolicyGate,
        chain: String,
        selected: String,
    ) {
        val other = "https://different.invalid"
        val response =
            PallaSyncRemoteService(client, policy).executeUnit(
                okhttp3.Request
                    .Builder()
                    .url(
                        "$other/pallasync/v3/chains/$chain/log/head",
                    ).header("Accept", PALLASYNC_MEDIA_TYPE)
                    .get()
                    .build(),
            )
        assertEquals(PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED"), response)
        val changed = runCatching { policy.bindChainOrigin(chain, other) }.exceptionOrNull()
        assertEquals("C_BOOTSTRAP_TRUST_REQUIRED", changed?.message)
        policy.requireChainOrigin(chain, selected)
    }

    private suspend fun exercisePausedEdits(
        coordinator: PalleriaSyncCoordinator,
        client: OkHttpClient,
        context: Context,
        scope: CoroutineScope,
        policy: PallaSyncPolicyGate,
        store: PallaSyncLocalStore,
        settings: com.yunfie.illustia.settings.SettingsStore,
        chain: String,
        device: String?,
    ) {
        val before = checkNotNull(store.dao.getChainState(chain))
        val query = "Android paused interop"
        settings.setPallaSyncEnabledFromCoordinator(false)
        coordinator.enqueueDataEventsThen(
            listOf(PallaSyncPendingEvent(SEARCH_HISTORY_SCHEMA_V3, query, "upsert", buildJsonObject { put("query", query) })),
        ) {
            settings.writeSyncedCollections(
                com.yunfie.illustia.settings
                    .SyncedCollectionsSnapshot(searchHistory = listOf(query)),
            )
        }
        assertEquals(before.authorSeq, store.dao.getChainState(chain)?.authorSeq)
        assertEquals(1L, coordinator.getPendingLocalEditCount())
        assertTrue(store.dao.queuedBatch(chain, 200).isEmpty())
        PallaSyncProjection(context).publish(chain, store)
        assertEquals(listOf(query), settings.readSyncedCollections().searchHistory)
        PallaSyncCore.endSecretSessions()
        val reopened = PalleriaSyncCoordinator(client, context, scope, policy, store)
        assertFalse(reopened.recoverInterruptedActivation())
        assertFalse(settings.read().pallaSyncEnabled)
        assertEquals(1L, reopened.getPendingLocalEditCount())
        PallaSyncCore.setSecretForeground(true)
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), reopened.resumeBoundChain(startWorker = false))
        assertEquals(device, PallaSyncKeystore(context).boundDeviceId(chain))
        assertEquals(before.authorSeq + 1, store.dao.getChainState(chain)?.authorSeq)
        assertEquals(0L, reopened.getPendingLocalEditCount())
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
    }

    private suspend fun exerciseEpochRotation(
        coordinator: PalleriaSyncCoordinator,
        keys: PallaSyncKeystore,
        store: PallaSyncLocalStore,
        context: Context,
        phrase: String,
        chain: String,
        interrupt: Boolean,
        faultInjected: () -> Boolean,
    ) {
        val previous = checkNotNull(keys.getActiveChainKeys())
        val committed = checkNotNull(store.dao.getChainState(chain))
        val acceptedId =
            checkNotNull(
                store.protocolDao().author(chain, committed.generation, checkNotNull(previous.deviceId), committed.authorSeq),
            ).recordId
        val accepted = checkNotNull(store.protocolDao().event(chain, acceptedId))
        assertTrue(accepted.relaySeq != null && accepted.receiptJson != null)
        val heldAccepted =
            store.dao.insertOutboxEvent(
                OutboxEntity(
                    chainId = chain,
                    deviceSeq = committed.authorSeq,
                    eventJson = accepted.signedJson,
                    status = "awaiting_reconciliation",
                    recordId = accepted.recordId,
                    objectHash = accepted.objectHash,
                    receiptJson = accepted.receiptJson,
                ),
            )
        val staleQueries = listOf("Android stale epoch first", "Android stale epoch second")
        for (query in staleQueries) {
            assertTrue(coordinator.enqueueDataEvent(SEARCH_HISTORY_SCHEMA_V3, query, "upsert", buildJsonObject { put("query", query) }))
        }
        val sources = store.dao.queuedBatch(chain, PALLASYNC_PAGE_SIZE)
        assertEquals(2, sources.size)
        val lastSequence = checkNotNull(store.dao.getChainState(chain)).authorSeq
        val rotated = coordinator.rotateEpoch(phrase)
        if (interrupt) {
            assertTrue("The named durable boundary was reached", faultInjected())
            assertEquals(false, rotated)
            assertEquals("epoch_rebase_required", store.dao.getChainState(chain)?.writeState)
            PallaSyncCore.endSecretSessions()
            val reopened = PallaSyncKeystore(context)
            PallaSyncOutboxRebase(
                store,
                PallaSyncLocalAuthor(context, store),
                reopened,
            ).resume(chain, checkNotNull(reopened.getActiveChainKeys()))
        } else {
            assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), rotated)
        }
        val next = checkNotNull(keys.getActiveChainKeys())
        val state = checkNotNull(store.dao.getChainState(chain))
        assertEquals(1L, next.epoch)
        assertEquals(1L, state.keyEpoch)
        assertEquals("active", state.writeState)
        assertEquals(previous.deviceId, next.deviceId)
        assertEquals(previous.epochSecretBase64Url, checkNotNull(keys.keysForEpoch(next, 0)).epochSecretBase64Url)
        assertTrue(previous.epochSecretBase64Url != next.epochSecretBase64Url)
        assertEquals(null, PallaSyncEpochJournal(context).pending(chain))
        assertEquals(null, store.dao.outboxEvent(heldAccepted))
        assertEquals(accepted, store.protocolDao().event(chain, acceptedId))
        val replacements = store.dao.queuedBatch(chain, PALLASYNC_PAGE_SIZE)
        assertEquals(2, replacements.size)
        assertEquals(lastSequence + 2, state.authorSeq)
        for ((index, source) in sources.withIndex()) {
            assertEquals(null, store.dao.outboxEvent(source.id))
            val retained = checkNotNull(store.protocolDao().event(chain, checkNotNull(source.recordId)))
            assertEquals("abandoned_unaccepted", retained.validationState)
            assertEquals(null, retained.relaySeq)
            assertEquals(null, retained.receiptJson)
            assertEquals(source.eventJson, retained.signedJson)
            val replacement = Json.decodeFromString<PallaSyncWireRecord>(replacements[index].eventJson)
            val original = Json.decodeFromString<PallaSyncWireRecord>(source.eventJson)
            assertEquals(1L, replacement.epoch)
            assertTrue(replacement.recordId != original.recordId && replacement.payloadNonce != original.payloadNonce)
            PallaSyncDeviceSession.begin(next).use {
                val inner =
                    Json.decodeFromString<PallaSyncInnerRecord>(
                        PallaSyncCore.decryptOwnPreparedRecord(it.handle, replacements[index].eventJson),
                    )
                assertEquals(staleQueries[index], inner.operations.single().entityId)
                assertTrue(inner.deviceSeq > lastSequence)
                assertEquals(if (index == 0) sources.last().objectHash else replacements[index - 1].objectHash, inner.prevRecordHash)
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("pallasync_rebase_sources", JsonArray(sources.map { Json.parseToJsonElement(it.eventJson) }).toString())
            },
        )
        assertTrue(
            coordinator.enqueueDataEvent(
                SEARCH_HISTORY_SCHEMA_V3,
                "Android rotated interop",
                "upsert",
                buildJsonObject {
                    put("query", "Android rotated interop")
                },
            ),
        )
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
    }

    private fun testTransport(encoded: String): OkHttpClient {
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(Base64.decode(encoded, Base64.DEFAULT).inputStream())
        val trust =
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null)
                setCertificateEntry("disposable-test", certificate)
            }
        val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }.trustManagers
        val manager = managers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(manager), null) }
        // Hostname verification remains enabled for the ephemeral certificate's loopback SAN.
        return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, manager).build()
    }
}
