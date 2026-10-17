package com.yunfie.illustia.pallasync

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.io.File
import java.util.UUID

/** Disposable test-only physical-file substitute. Public signed controls/ciphertext only.
 * Explicit test confirmations exercise crypto/REST; they do not certify a human-operated peer UI.
 */
internal class PallaSyncPeerRelayBoundary(
    private val context: Context,
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val policy: PallaSyncPolicyGate,
    client: OkHttpClient,
) {
    private val journal = PallaSyncPeerJournal(context)
    private val approver =
        PallaSyncPeerApprover(
            store,
            policy,
            keys,
            journal,
            PallaSyncPeerRelay(store, PallaSyncRemoteService(client, policy), PallaSyncCryptoService(policy)),
        )

    suspend fun exercise(
        base: HttpUrl,
        id: String,
    ) {
        val parsed = UUID.fromString(id)
        check(parsed.version() == 4 && parsed.variant() == 2) { "Invalid disposable transfer ID" }
        val root = File(context.filesDir, "pallasync-peer-test").canonicalFile
        val directory = File(root, id).canonicalFile
        check(directory.parentFile == root && directory.mkdirs()) { "Invalid or reused disposable transfer directory" }
        val offered = approver.offer(base, explicitlyRequested = true)
        var saved = offered
        approver.begin(base, offered).use { session ->
            try {
                write(directory, "offer.json", offered.offer.toString())
                val item =
                    withTimeout(TRANSFER_TIMEOUT_MS) {
                        var found = approver.pendingRequest(base, saved)
                        while (found == null) {
                            delay(POLL_MS)
                            found = approver.pendingRequest(base, saved)
                        }
                        found
                    }
                val (bound, visual) = approver.bind(saved, session, item, policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)))
                saved = bound
                // The independently computed Python value arrives over the test's local file channel.
                write(directory, "approver-sas.txt", visual)
                val independent = read(directory, "subject-sas.txt")
                assertEquals(visual, independent)
                saved = approver.confirmedTransfer(base, saved, session, independent, confirmed = true)
                write(directory, "certificate.json", checkNotNull(saved.certificate).toString())
                write(directory, "envelope.json", checkNotNull(saved.envelope).toString())
                val body = read(directory, "approval.json")
                saved = approver.approve(base, saved, session, body)
                assertEquals(PallaSyncPeerJournal.Stage.ACCEPTED, saved.stage)
                assertNotNull(saved.acceptance)
                val reopened = PallaSyncPeerJournal(context).read(saved.requestId)
                assertEquals(saved, reopened)
                write(directory, "acceptance.json", checkNotNull(reopened?.acceptance).toString())
                assertEquals(
                    "verified",
                    Json
                        .parseToJsonElement(read(directory, "completed.json"))
                        .jsonObject
                        .getValue("status")
                        .jsonPrimitive.content,
                )
            } finally {
                // A submitted/accepted approval retains its exact evidence even when this test is interrupted.
                val latest = checkNotNull(journal.read(saved.requestId))
                if (latest.stage !in setOf(PallaSyncPeerJournal.Stage.SUBMITTED, PallaSyncPeerJournal.Stage.ACCEPTED)) {
                    approver.cancel(latest, session)
                }
            }
        }
        check(directory.parentFile == root && root.parentFile == context.filesDir.canonicalFile)
        check(directory.deleteRecursively()) { "Disposable public transfer cleanup failed" }
    }

    private fun write(
        directory: File,
        name: String,
        value: String,
    ) {
        val temporary = File(directory, "$name.pending")
        temporary.writeText(value, Charsets.UTF_8)
        check(temporary.renameTo(File(directory, name))) { "Disposable public transfer commit failed" }
    }

    private suspend fun read(
        directory: File,
        name: String,
    ): String =
        withTimeout(TRANSFER_TIMEOUT_MS) {
            val file = File(directory, name)
            while (!file.exists()) delay(POLL_MS)
            check(file.length() <= MAX_PUBLIC_BYTES) { "Disposable public transfer exceeds its bound" }
            file.readText(Charsets.UTF_8)
        }

    private companion object {
        const val POLL_MS = 500L
        const val TRANSFER_TIMEOUT_MS = 240_000L
        const val MAX_PUBLIC_BYTES = 8_388_608L
    }
}
