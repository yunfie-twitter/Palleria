package com.yunfie.illustia.pallasync

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryPublicStatusTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun store(name: String): PallaSyncRecoverySetStore {
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return PallaSyncRecoverySetStore(prefs)
    }

    private fun holder(): PallaSyncRecoverySlot.Entry {
        val value =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        return PallaSyncRecoverySlot.Entry(value, "public-test", "public-test", "public-test")
    }

    @Test
    fun freshRootSignsExactRetirementAndPublicMediaKeepsSignaturesWithoutPackages() {
        PallaSyncCore.setSecretForeground(true)
        val store = store("recovery-public-retirement-unit")
        val entry = store.observeHolder(holder())
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        val retired =
            PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use {
                signRecoveryRetirement(it, store, entry)
            }
        retired.state shouldBe "retired"
        retired.ordinaryRecoveryAvailable shouldBe false
        val output = ByteArrayOutputStream()
        PallaSyncRecoveryPublicStatusMedia.write(output, retired)
        val publicStatuses = PallaSyncRecoveryPublicStatusMedia.read(ByteArrayInputStream(output.toByteArray()), entry)
        Json.parseToJsonElement(publicStatuses) shouldBe retired.statuses
        publicStatuses.contains("encrypted_share") shouldBe false
        publicStatuses.contains("encrypted_blob") shouldBe false
        shouldThrow<IllegalStateException> {
            PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use {
                signRecoveryRetirement(it, store, entry)
            }
        }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        store.read(entry.id) shouldBe retired
    }

    @Test
    fun statusMediaRejectsMalformedUtf8PackagesAndHostedUrisBeforeWriting() {
        val entry = store("recovery-public-media-unit").observeHolder(holder())
        shouldThrow<CharacterCodingException> {
            PallaSyncRecoveryPublicStatusMedia.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)), entry)
        }
        shouldThrow<IllegalStateException> {
            PallaSyncRecoveryPublicStatusMedia.read(ByteArrayInputStream(holder().packageJson.toByteArray()), entry)
        }
        val output = ByteArrayOutputStream()
        shouldThrow<IllegalStateException> {
            PallaSyncRecoveryPublicStatusMedia.write(
                output,
                entry.copy(statusesJson = JsonArray(listOf(JsonObject(emptyMap()))).toString()),
            )
        }
        output.size() shouldBe 0
        shouldThrow<IllegalStateException> {
            PallaSyncRecoveryPublicStatusMedia.write(context, Uri.parse("content://com.example.cloud/doc/status.json"), entry)
        }.message shouldBe "C_RECOVERY_STORAGE_UNSAFE"
    }

    @Test
    fun signedForkIsRetainedForQuarantineRatherThanDiscardedByFileParsing() {
        val store = store("recovery-public-status-fork-unit")
        val entry = store.observeHolder(holder())
        val fork =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-retired-status.json"))
                .use { Json.parseToJsonElement(it.readBytes().toString(Charsets.UTF_8)).jsonObject }
        val competing = JsonArray(listOf(entry.statuses.first(), fork)).toString()
        val parsed = PallaSyncRecoveryPublicStatusMedia.read(ByteArrayInputStream(competing.toByteArray()), entry)
        shouldThrow<IllegalStateException> { store.updateStatus(entry, parsed, requireLocalActivation = false) }
            .message shouldBe "C_RECOVERY_SHARE_INVALID"
        checkNotNull(store.read(entry.id)).conflictingStatuses shouldBe parsed
        checkNotNull(store.read(entry.id)).ordinaryRecoveryAvailable shouldBe false
    }

    @Test
    fun multipleSignedForksSurviveReopenAndReplayWithoutOverwritingOriginalEvidence() {
        val name = "recovery-public-multiple-forks-unit"
        val store = store(name)
        val initial = store.observeHolder(holder())
        val forks = recoveryStatusForks(holder().packageJson)
        (forks + forks.first()).forEach { statuses ->
            val expected = checkNotNull(store.read(initial.id))
            shouldThrow<IllegalStateException> { store.updateStatus(expected, statuses, requireLocalActivation = false) }
                .message shouldBe "C_RECOVERY_SHARE_INVALID"
        }
        val reopened = PallaSyncRecoverySetStore(context.getSharedPreferences(name, Context.MODE_PRIVATE))
        val retained = reopened.all().single()
        retained.statusesJson shouldBe initial.statusesJson
        retained.conflictingStatuses shouldBe forks.first()
        retained.forkEvidence shouldBe forks
        retained.ordinaryRecoveryAvailable shouldBe false
        shouldThrow<IllegalStateException> { reopened.updateStatus(retained, "[{}]", requireLocalActivation = false) }
        reopened.read(initial.id) shouldBe retained
        val observed =
            reopened.observeHolder(
                holder().copy(conflictingStatusChain = forks.first(), additionalConflictingStatusChains = forks.drop(1)),
            )
        observed.forkEvidence shouldBe forks
    }
}
