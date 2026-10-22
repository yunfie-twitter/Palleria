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
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
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
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
            val reseedProcessDeath = args.getString("reseed_process_death") == "true"
            val records413ProcessDeath = args.getString("records_413_process_death") == "true"
            val records413ResumeAfterDeath = args.getString("records_413_resume_after_death") == "true"
            val processDeathMode = reseedProcessDeath || records413ProcessDeath
            val resumeAfterDeath = args.getString("reseed_resume_after_death") == "true" || records413ResumeAfterDeath
            val processDeathDatabase =
                if (records413ProcessDeath) {
                    "pallasync-413-process-death-test.db"
                } else {
                    "pallasync-reseed-process-death-test.db"
                }
            if (processDeathMode && !resumeAfterDeath) {
                context.deleteDatabase(processDeathDatabase)
            }
            val database =
                if (processDeathMode) {
                    Room.databaseBuilder(context, PallaSyncDatabase::class.java, processDeathDatabase).build()
                } else {
                    Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
                }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val keys = PallaSyncKeystore(context)
            val reseedTargetOrigin = args.getString("reseed_target_origin")
            InstrumentationRegistry.getInstrumentation().sendStatus(
                0,
                Bundle().apply { putString("pallasync_reseed_target_argument", reseedTargetOrigin ?: "missing") },
            )
            if (!resumeAfterDeath) {
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
            }
            val settings =
                com.yunfie.illustia.settings
                    .SettingsStore(context)
            if (!resumeAfterDeath) {
                settings.setPallaSyncEnabledFromCoordinator(false)
                settings.writeSyncedCollections(
                    com.yunfie.illustia.settings
                        .SyncedCollectionsSnapshot(searchHistory = listOf("Python interop")),
                )
            }
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
                if (records413ResumeAfterDeath) {
                    check(records413ProcessDeath) { "C_MISSING_DEPENDENCY" }
                    val state = checkNotNull(store.dao.getActiveChainState())
                    assertEquals("active", state.writeState)
                    assertEquals(0L, state.generation)
                    val selectedOrigin = PallaSyncUrls.extractOrigin(checkNotNull(origin).toHttpUrl())
                    assertEquals(2, policy.retry.recordBatchLimit(selectedOrigin, state.chainId, 10))
                    PallaSyncCore.setSecretForeground(true)
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.resumeBoundChain(startWorker = false))
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
                    assertEquals(0L, store.dao.pendingOutboxCount(state.chainId))
                    assertEquals(2, policy.retry.recordBatchLimit(selectedOrigin, state.chainId, 10))
                    (0 until 4).forEach { index ->
                        assertNotNull(
                            store.protocolDao().entity(
                                state.chainId,
                                SEARCH_HISTORY_SCHEMA_V3,
                                "Android 413 split $index",
                            ),
                        )
                    }
                    exerciseLocalArtifactBoundary(coordinator, store, state.chainId)
                    exerciseBoundOrigin(client, policy, state.chainId, selectedOrigin)
                    exercisePausedEdits(
                        coordinator,
                        client,
                        context,
                        scope,
                        policy,
                        store,
                        settings,
                        state.chainId,
                        checkNotNull(keys.getActiveChainKeys()).deviceId,
                    )
                    assertTrue(
                        coordinator.enqueueDataEvent(
                            SEARCH_HISTORY_SCHEMA_V3,
                            "Android interop",
                            "upsert",
                            buildJsonObject { put("query", "Android interop") },
                        ),
                    )
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
                    InstrumentationRegistry.getInstrumentation().sendStatus(
                        0,
                        Bundle().apply {
                            putString("pallasync_records_413_process_death", "resumed_and_drained")
                            putString("pallasync_device_id", checkNotNull(keys.getActiveChainKeys()).deviceId.orEmpty())
                            putString("pallasync_local_boundary", "verified")
                            putString("pallasync_bound_origin", "verified")
                        },
                    )
                    println("pallasync_records_413_process_death=resumed_and_drained")
                    println("pallasync_device_id=${checkNotNull(keys.getActiveChainKeys()).deviceId}")
                    return@runBlocking
                }
                if (resumeAfterDeath) {
                    check(processDeathMode && reseedTargetOrigin != null) { "C_MISSING_DEPENDENCY" }
                    val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
                    val before = checkNotNull(store.dao.getActiveChainState())
                    check(before.generation == 1L || before.writeState == "epoch_rebase_required") { "C_ROLLBACK_DETECTED" }
                    PallaSyncCore.setSecretForeground(true)
                    val resumed = coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "")
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), resumed is PallaSyncHttpResult.Success)
                    val migrated = checkNotNull(store.dao.getActiveChainState())
                    val migratedKeys = checkNotNull(keys.getActiveChainKeys())
                    val authority = Json.parseToJsonElement(checkNotNull(store.protocolDao().currentAuthority(migrated.chainId))).jsonObject
                    assertEquals(1L, migrated.generation)
                    assertEquals(
                        reseedTargetOrigin,
                        authority
                            .getValue("pin")
                            .jsonObject
                            .getValue("origin")
                            .jsonPrimitive.content,
                    )
                    assertEquals(migratedKeys.deviceId, keys.boundDeviceId(migrated.chainId))
                    assertTrue(coordinator.canAuditReseedSource())
                    val sourceAudit = coordinator.auditReseedSource()
                    if (args.getString("reseed_expect_late_source_write") == "true") {
                        assertNotNull(sourceAudit)
                        assertEquals(1L, checkNotNull(sourceAudit).entryCount)
                        assertEquals(checkNotNull(sourceAudit).firstSequence, checkNotNull(sourceAudit).lastSequence)
                    } else {
                        assertEquals(null, sourceAudit)
                    }
                    InstrumentationRegistry.getInstrumentation().sendStatus(
                        0,
                        Bundle().apply {
                            putString("pallasync_reseed_device_id", migratedKeys.deviceId)
                            putString("pallasync_reseed_generation", migrated.generation.toString())
                            putString("pallasync_reseed_source_audit", if (sourceAudit == null) "clean" else "unimported")
                            sourceAudit?.let { putString("pallasync_reseed_unimported_count", it.entryCount.toString()) }
                            putString("reseed_resumed_after_process_death", "true")
                        },
                    )
                    println("reseed_resumed_after_process_death=true")
                    println("pallasync_reseed_device_id=${migratedKeys.deviceId}")
                    println("pallasync_reseed_generation=${migrated.generation}")
                    println("pallasync_reseed_source_audit=${if (sourceAudit == null) "clean" else "unimported"}")
                    sourceAudit?.let { println("pallasync_reseed_unimported_count=${it.entryCount}") }
                    return@runBlocking
                }
                val selected = checkNotNull(origin)
                policy.chooseSource(selected, "relay_full_replay", true)
                policy.confirmKey(policy.inspectRelay(selected), true)
                policy.accept(policy.currentReview(selected), true)
                PallaSyncCore.setSecretForeground(true)
                val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
                val joined =
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
                    )
                assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), joined)
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
                if (args.getString("records_413_split") == "true" || records413ProcessDeath) {
                    val recoveryEvents =
                        (0 until 4).map { index ->
                            val query = "Android 413 split $index"
                            PallaSyncPendingEvent(
                                SEARCH_HISTORY_SCHEMA_V3,
                                query,
                                "upsert",
                                buildJsonObject { put("query", query) },
                            )
                        }
                    assertTrue(coordinator.enqueueDataEvents(recoveryEvents))
                    val armRequest =
                        Request
                            .Builder()
                            .url(
                                if (records413ProcessDeath) {
                                    "$selected/__test__/arm-records-413-process-death"
                                } else {
                                    "$selected/__test__/arm-records-413"
                                },
                            )
                            .post(ByteArray(0).toRequestBody(null))
                            .build()
                    client.newCall(armRequest).execute().use { response ->
                        assertEquals(200, response.code)
                        assertEquals("{\"armed\":true}", response.body.string())
                    }
                    if (records413ProcessDeath) {
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply { putString("pallasync_records_413_process_death", "waiting_for_held_replacement") },
                        )
                        println("pallasync_records_413_process_death=first_replacement_held")
                        coordinator.syncNow()
                        error("The held record replacement unexpectedly returned")
                    } else {
                        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), coordinator.syncNow())
                        assertEquals(0L, store.dao.pendingOutboxCount(state.chainId))
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply { putString("pallasync_records_413_split", "passed") },
                        )
                        println("pallasync_records_413_split=passed")
                    }
                }
                val replacingDevice = args.getString("replace_device") == "true"
                if (!replacingDevice) {
                    exerciseCompleteArchiveRoundTrip(coordinator, store, phrase, state.chainId)
                    exerciseMissingDependencyQuarantineRetry(coordinator, store, state.chainId)
                    exerciseLocalArtifactBoundary(coordinator, store, state.chainId)
                    exerciseBoundOrigin(client, policy, state.chainId, selected)
                }
                args.getString("peer_transfer_id")?.let { transferId ->
                    if (!replacingDevice) {
                        PallaSyncPeerRelayBoundary(context, store, keys, policy, client).exercise(selected.toHttpUrl(), transferId)
                    }
                }
                exercisePausedEdits(coordinator, client, context, scope, policy, store, settings, state.chainId, active.deviceId)
                if (replacingDevice) {
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
                    val replaced = coordinator.replaceCurrentDevice(phrase)
                    if (requestedFault != null) {
                        assertFalse(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), replaced)
                        assertTrue("Replacement boundary fault was not reached", faultInjected)
                        val resumed = coordinator.replaceCurrentDevice(phrase)
                        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), resumed)
                    } else {
                        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), replaced)
                    }
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
                if (reseedTargetOrigin != null) {
                    policy.chooseSource(reseedTargetOrigin, "authenticated_export", explicitlyConfirmed = true)
                    policy.confirmKey(policy.inspectRelay(reseedTargetOrigin), explicitlyConfirmed = true)
                    policy.accept(policy.currentReview(reseedTargetOrigin), explicitlyConfirmed = true)
                    val sourceAuthorityHash = checkNotNull(keys.authorityHash(state.chainId))
                    val sourceWinnerId =
                        checkNotNull(store.protocolDao().entity(state.chainId, SEARCH_HISTORY_SCHEMA_V3, "Python interop")).winner.recordId
                    val staged = coordinator.stageReseedHistory(reseedTargetOrigin, phrase, "")
                    assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), staged is PallaSyncHttpResult.Success)
                    if (args.getString("reseed_expect_staging_source_write") == "true") {
                        val cutover = runCatching { coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "") }
                        assertTrue("Source write during snapshot staging unexpectedly cut over", cutover.isFailure)
                        val preserved = checkNotNull(store.dao.getActiveChainState())
                        assertEquals(0L, preserved.generation)
                        assertEquals(state.logId, preserved.logId)
                        assertTrue(
                            "Source was not retained in a writable or explicitly pending state: ${preserved.writeState}",
                            preserved.writeState in setOf("active", "epoch_rebase_required"),
                        )
                        assertNotNull(keys.getActiveChainKeys())
                        assertEquals(sourceAuthorityHash, keys.authorityHash(state.chainId))
                        assertTrue(
                            sourceWinnerId.contentEquals(
                                checkNotNull(
                                    store.protocolDao().entity(state.chainId, SEARCH_HISTORY_SCHEMA_V3, "Python interop"),
                                ).winner.recordId,
                            ),
                        )
                        assertTrue("Could not download the racing source record after the rejected cutover", coordinator.syncNow())
                        val caughtUp = checkNotNull(store.dao.getChainState(state.chainId))
                        assertEquals(caughtUp.downloadedSeq, caughtUp.materializedSeq)
                        assertTrue(caughtUp.materializedSeq > state.materializedSeq)
                        assertNotNull(
                            store.protocolDao().entity(
                                state.chainId,
                                SEARCH_HISTORY_SCHEMA_V3,
                                "Python source race before capture",
                            ),
                        )
                        val retryStage = coordinator.stageReseedHistory(reseedTargetOrigin, phrase, "")
                        assertTrue(
                            "Could not restage after incorporating source write: ${PalleriaSyncCoordinator.syncLogs.value.joinToString(
                                "\n",
                            )}",
                            retryStage is PallaSyncHttpResult.Success,
                        )
                        val retryCommit = coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "")
                        assertTrue(
                            "Could not finish reseed after rebuilding the snapshot: ${PalleriaSyncCoordinator.syncLogs.value.joinToString(
                                "\n",
                            )}",
                            retryCommit is PallaSyncHttpResult.Success,
                        )
                        val migrated = checkNotNull(store.dao.getActiveChainState())
                        assertEquals(1L, migrated.generation)
                        val migratedKeys = checkNotNull(keys.getActiveChainKeys())
                        assertEquals(migratedKeys.deviceId, keys.boundDeviceId(state.chainId))
                        assertTrue(coordinator.canAuditReseedSource())
                        assertEquals(null, coordinator.auditReseedSource())
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply {
                                putString("pallasync_reseed_staging_race_rejected", "true")
                                putString("pallasync_reseed_staging_race_recovered", "true")
                                putString("pallasync_reseed_device_id", migratedKeys.deviceId)
                                putString("pallasync_reseed_generation", migrated.generation.toString())
                                putString("pallasync_reseed_source_audit", "clean")
                                putString("pallasync_reseed_staging_race_error", cutover.exceptionOrNull()?.message ?: "missing")
                            },
                        )
                        println("pallasync_reseed_staging_race_rejected=true")
                        println("pallasync_reseed_staging_race_recovered=true")
                        println("pallasync_reseed_device_id=${migratedKeys.deviceId}")
                        println("pallasync_reseed_generation=${migrated.generation}")
                        println("pallasync_reseed_source_audit=clean")
                        return@runBlocking
                    }
                    if (args.getString("reseed_expect_split_view") == "true") {
                        val cutover = runCatching { coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "") }
                        assertTrue("Signed split view unexpectedly allowed reseed cutover", cutover.isFailure)
                        val preserved = checkNotNull(store.dao.getActiveChainState())
                        assertEquals(0L, preserved.generation)
                        assertTrue(
                            "Source writes were not suspended: ${preserved.writeState}",
                            preserved.writeState in setOf("integrity_paused", "epoch_rebase_required"),
                        )
                        assertEquals(state.logId, preserved.logId)
                        assertNotNull(keys.getActiveChainKeys())
                        val evidenceRows = store.protocolDao().unresolvedQuarantinePage(state.chainId, "", 50)
                        val splitEvidence =
                            evidenceRows.firstOrNull {
                                it.reason in
                                    setOf("C_ROLLBACK_DETECTED", "C_TRANSPARENCY_INVALID", "C_LOG_HASH_MISMATCH")
                            }
                        assertNotNull("Signed contradictory transparency head was not quarantined", splitEvidence)
                        val retained =
                            checkNotNull(store.protocolDao().quarantineEvidence(state.chainId, checkNotNull(splitEvidence).objectHash))
                        assertTrue(retained.evidenceJson.contains("PALLASYNC-TRANSPARENCY-HEAD-v3"))
                        assertEquals(sourceAuthorityHash, keys.authorityHash(state.chainId))
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply {
                                putString("pallasync_reseed_split_view_rejected", "true")
                                putString("pallasync_reseed_split_view_evidence", "retained")
                                putString("pallasync_reseed_split_view_writes", preserved.writeState)
                                putString("pallasync_reseed_split_view_error", cutover.exceptionOrNull()?.message ?: "missing")
                            },
                        )
                        println("pallasync_reseed_split_view_rejected=true")
                        println("pallasync_reseed_split_view_evidence=retained")
                        println("pallasync_reseed_split_view_writes=${preserved.writeState}")
                        return@runBlocking
                    }
                    val requestedFault = args.getString("rebase_fault")?.takeIf { it.isNotEmpty() }
                    if (requestedFault != null) {
                        val firstCommit = runCatching { coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "") }
                        assertTrue("Reseed cutover fault was not injected: $requestedFault", faultInjected)
                        assertTrue("Reseed cutover fault did not interrupt commit", firstCommit.isFailure)
                        val sourceAuthority =
                            Json
                                .parseToJsonElement(
                                    checkNotNull(store.protocolDao().archivedAuthority(state.chainId, 0)).signedJson,
                                ).jsonObject
                        val sourceHash = PallaSyncCore.objectHash(sourceAuthority.toString())
                        val targetHash = PallaSyncCore.objectHash(checkNotNull(store.protocolDao().currentAuthority(state.chainId)))
                        val interruptedState = checkNotNull(store.dao.getChainState(state.chainId))
                        val interruptedJournal = checkNotNull(store.snapshotDatabase().reseedJournalDao().get(state.chainId))
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply {
                                putString("reseed_fault_injected", faultInjected.toString())
                                putString("reseed_fault_phase", interruptedJournal.phase.toString())
                                putString("reseed_fault_generation", interruptedState.generation.toString())
                                putString("reseed_fault_pinned_source", (keys.authorityHash(state.chainId) == sourceHash).toString())
                                putString("reseed_fault_pinned_target", (keys.authorityHash(state.chainId) == targetHash).toString())
                                putString(
                                    "reseed_fault_active_is_archived",
                                    (
                                        keys.getActiveChainKeys()?.deviceId ==
                                            keys.getArchivedChainKeys(state.chainId)?.deviceId
                                    ).toString(),
                                )
                            },
                        )
                        if (processDeathMode) {
                            InstrumentationRegistry.getInstrumentation().sendStatus(
                                0,
                                Bundle().apply { putString("reseed_process_death_phase_one", "ready") },
                            )
                            System.err.flush()
                            Thread.sleep(500)
                            android.os.Process.killProcess(android.os.Process.myPid())
                            error("Process termination did not occur")
                        }
                        val resumed = runCatching { coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "") }
                        if (resumed.isFailure) {
                            InstrumentationRegistry.getInstrumentation().sendStatus(
                                0,
                                Bundle().apply { putString("reseed_resume_error", resumed.exceptionOrNull()?.message) },
                            )
                        }
                        assertTrue(
                            PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"),
                            resumed.getOrThrow() is PallaSyncHttpResult.Success,
                        )
                    } else {
                        val committed = coordinator.commitStagedReseedHistory(reseedTargetOrigin, phrase, "")
                        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), committed is PallaSyncHttpResult.Success)
                    }
                    val migrated = checkNotNull(store.dao.getActiveChainState())
                    val migratedKeys = checkNotNull(keys.getActiveChainKeys())
                    val migratedAuthority =
                        Json
                            .parseToJsonElement(
                                checkNotNull(store.protocolDao().currentAuthority(migrated.chainId)),
                            ).jsonObject
                    assertEquals(1, migrated.generation)
                    assertEquals(
                        reseedTargetOrigin,
                        migratedAuthority
                            .getValue("pin")
                            .jsonObject
                            .getValue("origin")
                            .jsonPrimitive.content,
                    )
                    assertEquals(1, migratedKeys.epoch)
                    assertEquals(migratedKeys.deviceId, keys.boundDeviceId(migrated.chainId))
                    if (args.getString("reseed_archive_probe") == "true") {
                        probeGenerationOneArchive(coordinator, store, phrase, state.chainId)
                        InstrumentationRegistry.getInstrumentation().sendStatus(
                            0,
                            Bundle().apply {
                                putString("pallasync_gen1_archive_probe", "complete_archive_restored")
                                putString("pallasync_reseed_device_id", migratedKeys.deviceId)
                                putString("pallasync_reseed_generation", migrated.generation.toString())
                                putString("pallasync_reseed_source_audit", "clean")
                            },
                        )
                        println("pallasync_reseed_generation=${migrated.generation}")
                        println("pallasync_reseed_source_audit=clean")
                        return@runBlocking
                    }
                    assertTrue(coordinator.canAuditReseedSource())
                    val sourceAudit = coordinator.auditReseedSource()
                    if (args.getString("reseed_expect_pre_capture_source_write") == "true") {
                        assertNotNull(sourceAudit)
                        assertEquals(1L, checkNotNull(sourceAudit).entryCount)
                        assertEquals(checkNotNull(sourceAudit).firstSequence, checkNotNull(sourceAudit).lastSequence)
                    } else {
                        assertEquals(null, sourceAudit)
                    }
                    InstrumentationRegistry.getInstrumentation().sendStatus(
                        0,
                        Bundle().apply {
                            putString("pallasync_reseed_device_id", migratedKeys.deviceId)
                            putString("pallasync_reseed_generation", migrated.generation.toString())
                            putString("pallasync_reseed_source_audit", if (sourceAudit == null) "clean" else "unimported")
                            sourceAudit?.let { putString("pallasync_reseed_unimported_count", it.entryCount.toString()) }
                        },
                    )
                    println("pallasync_reseed_device_id=${migratedKeys.deviceId}")
                    println("pallasync_reseed_generation=${migrated.generation}")
                    println("pallasync_reseed_log_id=${migrated.logId}")
                    println("pallasync_reseed_source_audit=${if (sourceAudit == null) "clean" else "unimported"}")
                    sourceAudit?.let { println("pallasync_reseed_unimported_count=${it.entryCount}") }
                }
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
                if (args.getString("delete_chain") == "true") {
                    val lostResponse = args.getString("delete_response_lost") == "true"
                    val firstDeletion = coordinator.requestChainDeletion(phrase)
                    if (lostResponse) {
                        assertEquals(PallaSyncDeletionRequestResult.PENDING_EVIDENCE, firstDeletion)
                        assertNotNull(store.dao.getChainState(state.chainId))
                        kotlinx.coroutines.delay(1_500)
                    }
                    val deletion = if (lostResponse) coordinator.requestChainDeletion(phrase) else firstDeletion
                    assertEquals(
                        PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"),
                        PallaSyncDeletionRequestResult.PROOF_READY_FOR_EXPORT,
                        deletion,
                    )
                    val proof = checkNotNull(coordinator.pendingDeletionProof())
                    val proofFile = java.io.File.createTempFile("pallasync-delete-proof-", ".json", context.cacheDir)
                    val bundleFile = java.io.File.createTempFile("pallasync-delete-bundle-", ".json", context.cacheDir)
                    try {
                        proofFile.writeText(proof, Charsets.UTF_8)
                        val proofReadback = proofFile.readText(Charsets.UTF_8)
                        assertEquals(proof, proofReadback)
                        val archive = checkNotNull(coordinator.pendingDeletionEvidenceArchive())
                        bundleFile.writeText(archive, Charsets.UTF_8)
                        val archiveReadback = PallaSyncDeletionEvidenceArchive.parseStrictObject(bundleFile.readText(Charsets.UTF_8))
                        assertEquals(setOf("delete_receipt", "evidence_bundle"), archiveReadback.keys)
                        assertTrue(coordinator.completeDeletionAfterExport(proofReadback, explicitlyConfirmedOfflineCopy = true))
                        assertEquals(null, store.dao.getChainState(state.chainId))
                        assertNotNull(store.deletionEvidenceDao().latestReceiptRevision(state.chainId, checkNotNull(state.logId)))
                    } finally {
                        proofFile.delete()
                        bundleFile.delete()
                    }
                }
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

    private suspend fun exerciseCompleteArchiveRoundTrip(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        mnemonic: String,
        chain: String,
    ) {
        val archive = ByteArrayOutputStream()
        val expectedSource = coordinator.exportRelayArchiveWithRecovery(archive, mnemonic, "")
        val bytes = archive.toByteArray()
        val verified = coordinator.verifyRelayArchive(ByteArrayInputStream(bytes), expectedSource)
        assertTrue("Archive was not marked complete", verified.complete)
        assertTrue("Archive omitted recovery", verified.includeRecovery)
        assertEquals(chain, verified.chainId)

        // Model the durable state left by a new device whose authenticated enrollment
        // checkpoint exists but whose first data pull never committed. The complete
        // archive must bootstrap from its verified snapshot and replay its retained suffix.
        val beforeRestore = checkNotNull(store.dao.getChainState(chain))
        val dataZero =
            Json
                .parseToJsonElement(checkNotNull(verified.historyCutJson))
                .jsonObject
                .getValue("data_zero")
                .jsonPrimitive.content
        store.dao.updateChainState(
            beforeRestore.copy(
                lastRelaySeq = 0,
                downloadedSeq = 0,
                verifiedSeq = 0,
                materializedSeq = 0,
                verifiedHash = dataZero,
                initialPullCompleted = false,
            ),
        )

        coordinator.restoreRelayArchive(ByteArrayInputStream(bytes), expectedSource, mnemonic, "")
        val state = checkNotNull(store.dao.getChainState(chain))
        assertTrue(state.initialPullCompleted)
        assertEquals(state.verifiedSeq, state.materializedSeq)
        assertNotNull(store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "Python interop"))
        println("pallasync_complete_archive_round_trip=verified")
    }

    private suspend fun probeGenerationOneArchive(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        mnemonic: String,
        chain: String,
    ) {
        val archive = ByteArrayOutputStream()
        val expectedSource = coordinator.exportRelayArchiveWithRecovery(archive, mnemonic, "")
        val bytes = archive.toByteArray()
        val lines =
            bytes
                .toString(Charsets.UTF_8)
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
        val header = Json.parseToJsonElement(lines.first()).jsonObject
        assertEquals(chain, header.getValue("chain_id").jsonPrimitive.content)
        assertEquals(1L, header.getValue("generation").jsonPrimitive.long)
        val kinds =
            lines
                .drop(1)
                .mapNotNull { line ->
                    Json
                        .parseToJsonElement(line)
                        .jsonObject["kind"]
                        ?.jsonPrimitive
                        ?.content
                }.groupingBy { it }
                .eachCount()
                .toSortedMap()
        val verification =
            runCatching {
                coordinator.verifyRelayArchive(ByteArrayInputStream(bytes), expectedSource)
            }
        println("pallasync_gen1_archive_bytes=${bytes.size}")
        println("pallasync_gen1_archive_kinds=$kinds")
        val verificationResult = if (verification.isSuccess) "PASS" else verification.exceptionOrNull()?.message ?: "unknown"
        println("pallasync_gen1_archive_verify=$verificationResult")
        val verified = verification.getOrThrow()
        val verifiedAuthority = Json.parseToJsonElement(checkNotNull(verified.authorityJson)).jsonObject
        val verifiedParameters = verifiedAuthority.getValue("parameters").jsonObject
        val verifiedPin = verifiedAuthority.getValue("pin").jsonObject
        val verifiedHistoryCut = Json.parseToJsonElement(checkNotNull(verified.historyCutJson)).jsonObject
        val verifiedHead = verifiedHistoryCut.getValue("head").jsonObject
        assertEquals(1L, verifiedParameters.getValue("generation").jsonPrimitive.long)
        assertEquals(verified.headSeq, verifiedAuthority.getValue("sequence").jsonPrimitive.long)
        assertEquals(verified.headHash, verifiedAuthority.getValue("head_hash").jsonPrimitive.content)
        assertEquals(verified.logId, verifiedPin.getValue("log_id").jsonPrimitive.content)
        assertEquals(verified.headSeq, verifiedHead.getValue("head_seq").jsonPrimitive.long)
        assertEquals(verified.headHash, verifiedHead.getValue("head_hash").jsonPrimitive.content)
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply {
                putString("pallasync_gen1_archive_bytes", bytes.size.toString())
                putString("pallasync_gen1_archive_kinds", kinds.toString())
                putString("pallasync_gen1_archive_authority_replay", "verified_generation_transition")
                putString("pallasync_gen1_archive_verify", verificationResult)
            },
        )
        assertTrue("Generation 1 archive was not complete", verified.complete)
        assertEquals(1L, verified.generation)
        assertTrue(verified.includeRecovery)
        coordinator.restoreRelayArchive(ByteArrayInputStream(bytes), expectedSource, mnemonic, "")
        val restored = checkNotNull(store.dao.getChainState(chain))
        assertEquals(1L, restored.generation)
        assertEquals(verified.logId, restored.logId)
        assertEquals(verified.headSeq, restored.verifiedSeq)
        assertEquals(restored.verifiedSeq, restored.materializedSeq)
        assertNotNull(store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "Python interop"))
        println("pallasync_gen1_archive_restore=verified")
    }

    private suspend fun exerciseMissingDependencyQuarantineRetry(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        chain: String,
    ) {
        val event = checkNotNull(store.protocolDao().firstAcceptedEvent(chain))
        val originalHash = checkNotNull(PallaSyncCore.objectHash(event.signedJson))
        assertEquals(event.objectHash, originalHash)
        val inbox =
            store.dao.getInboxRecord(chain, event.recordId)
                ?: PallaSyncInboxEntity
                    .applied(chain, event.recordId, checkNotNull(event.relaySeq), event.signedJson)
                    .also { store.dao.insertInboxRecords(listOf(it)) }
        assertEquals("applied", inbox.status)
        store.snapshotDatabase().openHelper.writableDatabase.execSQL(
            "UPDATE pallasync_inbox SET status='quarantined',quarantine_reason='C_MISSING_DEPENDENCY' WHERE chain_id=? AND record_id=?",
            arrayOf(chain, event.recordId),
        )
        store.protocolDao().quarantine(
            QuarantineEvidenceEntity(chain, event.objectHash, "C_MISSING_DEPENDENCY", event.signedJson, "unresolved"),
        )

        val retryMissingDependency = coordinator.retryQuarantinedRecordEvidence(event.objectHash)
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), retryMissingDependency)
        assertEquals("applied", store.dao.getInboxRecord(chain, event.recordId)?.status)
        val resolved = checkNotNull(store.protocolDao().quarantineEvidence(chain, event.objectHash))
        assertEquals(event.signedJson, resolved.evidenceJson)
        assertEquals("resolved", resolved.resolutionState)

        // A separate authority proof gap has a distinct recovery reason. Re-stage the same
        // immutable accepted object as unproven and require the retry to fetch and validate its
        // exact signed receipt/transparency evidence before materializing it again.
        store.snapshotDatabase().openHelper.writableDatabase.execSQL(
            "UPDATE ps_quarantine SET reason='C_ACCEPTANCE_UNPROVEN',resolution_state='unresolved' WHERE chain_id=? AND object_hash=?",
            arrayOf(chain, event.objectHash),
        )
        store.snapshotDatabase().openHelper.writableDatabase.execSQL(
            "UPDATE pallasync_inbox SET status='quarantined',quarantine_reason='C_ACCEPTANCE_UNPROVEN' WHERE chain_id=? AND record_id=?",
            arrayOf(chain, event.recordId),
        )
        val retryAcceptanceProof = coordinator.retryQuarantinedRecordEvidence(event.objectHash)
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.joinToString("\n"), retryAcceptanceProof)
        assertEquals("applied", store.dao.getInboxRecord(chain, event.recordId)?.status)
        val acceptanceResolved = checkNotNull(store.protocolDao().quarantineEvidence(chain, event.objectHash))
        assertEquals("C_ACCEPTANCE_UNPROVEN", acceptanceResolved.reason)
        assertEquals(event.signedJson, acceptanceResolved.evidenceJson)
        assertEquals("resolved", acceptanceResolved.resolutionState)
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
        // Hostname verification remains enabled for the ephemeral certificate's loopback SAN.
        return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, manager).build()
    }
}
