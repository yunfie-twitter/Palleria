package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.util.UUID

private val configurationPhrase = (List(23) { "abandon" } + "art").joinToString(" ")

/** Public boundary and actual native ownership tests; synthetic domains do not prove physical independence. */
@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryConfigurationTest {
    private fun configuration(
        total: Int = 3,
        threshold: Int = 2,
    ): PallaSyncRecoveryConfiguration =
        PallaSyncRecoveryConfiguration(
            threshold,
            List(total) { PallaSyncRecoveryConfiguration.Holder(UUID.randomUUID().toString(), null) },
            independentHoldersConfirmed = true,
            deviceOnlyRiskConfirmed = false,
        )

    @Test
    fun exactPublicBindingsRequireConsentAndIndependentIdsAcrossAllValidBounds() {
        listOf(3 to 2, 4 to 2, 4 to 3, 5 to 3, 255 to 254).forEach { (total, threshold) ->
            val result = Json.parseToJsonElement(configuration(total, threshold).holdersJson()).jsonArray
            result.size shouldBe total
            result.forEach {
                it.jsonObject.keys shouldBe setOf("holder_kind", "holder_device_id", "holder_instance_id")
                it.jsonObject
                    .getValue("holder_kind")
                    .jsonPrimitive.content shouldBe "offline"
            }
        }
        listOf(3 to 1, 3 to 3, 2 to 3, 256 to 2).forEach { (total, threshold) ->
            shouldThrow<IllegalStateException> { configuration(total, threshold).holdersJson() }.message shouldBe
                "C_RECOVERY_PARAMETER_INVALID"
        }
        val valid = configuration()
        shouldThrow<IllegalStateException> { valid.copy(independentHoldersConfirmed = false).holdersJson() }.message shouldBe
            "C_RECOVERY_AUTH_REQUIRED"
        shouldThrow<IllegalStateException> { valid.copy(holders = List(3) { valid.holders[0] }).holdersJson() }.message shouldBe
            "C_RECOVERY_SHARE_ALREADY_USED"
        val deviceOnly = valid.copy(holders = valid.holders.map { it.copy(deviceId = UUID.randomUUID().toString()) })
        shouldThrow<IllegalStateException> { deviceOnly.holdersJson() }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
        Json.parseToJsonElement(deviceOnly.copy(deviceOnlyRiskConfirmed = true).holdersJson()).jsonArray.size shouldBe 3
        shouldThrow<IllegalStateException> {
            valid.copy(holders = valid.holders.map { it.copy(instanceId = "1-1-1-1-1") }).holdersJson()
        }.message shouldBe "C_RECOVERY_PARAMETER_INVALID"
    }

    @Test
    fun scopedPreparationMatchesPinnedIdentityAndCancelPreservesOnlyIncompletePublicState() {
        PallaSyncCore.setSecretForeground(true)
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("setup-owner-unit", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PallaSyncRecoverySetStore(prefs)
        val identity =
            PallaSyncRootSession.beginText(configurationPhrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use {
                it.identity
            }
        val chain = PallaSyncRecoveryChainContext(identity.chainId, identity.adminPublicKey, 7, UUID.randomUUID().toString())
        val owner = PallaSyncRecoverySetupOwner(store)
        owner.prepare(chain, configuration(), configurationPhrase, "", 1)
        owner.usePrepared {
            it.evidence.manifest
                .getValue("generation")
                .jsonPrimitive.content shouldBe "7"
            it.evidence.state shouldBe "prepared"
            shouldThrow<IllegalStateException> { it.activate(2) }.message shouldBe "C_RECOVERY_ROTATION_INCOMPLETE"
        }
        owner.close()
        checkNotNull(store.selected()).interrupted shouldBe true
        shouldThrow<IllegalStateException> { owner.usePrepared { it.beginTransfer(1) } }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
        val cancelled = PallaSyncRecoverySetupOwner(store)
        cancelled.close()
        shouldThrow<IllegalStateException> { cancelled.prepare(chain, configuration(), configurationPhrase, "", 3) }.message shouldBe
            "C_RECOVERY_AUTH_REQUIRED"
        PallaSyncRecoverySetupOwner(store).use { wrongRoot ->
            shouldThrow<IllegalStateException> { wrongRoot.prepare(chain, configuration(), configurationPhrase, "wrong", 4) }
        }
        store.all().size shouldBe 1
    }

    @Test
    fun localControlFilesPreserveOriginalBytesRejectInvalidUtf8AndHandleZeroLengthReads() {
        val original = "{\"type\":\"PALLASYNC-RECOVERY-SESSION-v3\",\"version\":\"3.0\"}"
        val bytes = original.toByteArray()
        val output = ByteArrayOutputStream()
        PallaSyncRecoveryLocalFiles.write(output, original)
        output.toByteArray().contentEquals(bytes) shouldBe true
        val zeroRead =
            object : ByteArrayInputStream(bytes) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = 0
            }
        PallaSyncRecoveryLocalFiles.read(zeroRead) shouldBe original
        shouldThrow<CharacterCodingException> { PallaSyncRecoveryLocalFiles.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28))) }
        shouldThrow<IllegalStateException> {
            PallaSyncRecoveryLocalFiles.read(ByteArrayInputStream(ByteArray(4_194_305)))
        }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        val untouched = ByteArrayOutputStream()
        shouldThrow<IllegalStateException> { PallaSyncRecoveryLocalFiles.write(untouched, "x".repeat(4_194_305)) }
        untouched.size() shouldBe 0
    }
}
