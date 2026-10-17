package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncKeystoreTransparencyHeadTest {
    @Test
    fun `verified signed head persists monotone minimum across keystore reopen`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val first = head(index = 8, minimum = 1, hash = "A")
        keystore.retainVerifiedTransparencyHead(CHAIN, first) shouldBe true
        val moved = head(index = 9, minimum = 43, hash = "B")
        keystore.retainVerifiedTransparencyHead(CHAIN, moved) shouldBe true
        PallaSyncKeystore(preferences).verifiedTransparencyHead(CHAIN) shouldBe moved

        shouldThrow<IllegalStateException> {
            keystore.retainVerifiedTransparencyHead(CHAIN, head(index = 10, minimum = 1, hash = "C"))
        }.message shouldBe "C_ROLLBACK_DETECTED"
        shouldThrow<IllegalStateException> {
            keystore.retainVerifiedTransparencyHead(CHAIN, head(index = 9, minimum = 43, hash = "C"))
        }.message shouldBe "C_TRANSPARENCY_INVALID"
    }

    @Test
    fun `repeating initial pin does not discard an already verified head`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val anchor =
            buildJsonObject {
                put("transparency_id", TRANSPARENCY)
                put("index", 8)
                put("hash", "A")
            }
        keystore.pinInitialTransparency(CHAIN, anchor) shouldBe true
        val head = head(index = 8, minimum = 1, hash = "A")
        keystore.retainVerifiedTransparencyHead(CHAIN, head) shouldBe true
        keystore.pinInitialTransparency(CHAIN, anchor) shouldBe true
        keystore.verifiedTransparencyHead(CHAIN) shouldBe head
    }

    @Test
    fun `page resume checkpoint is monotone and cleared with completed signed head`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val firstAnchor = anchor(index = 8, hash = "A")
        keystore.pinInitialTransparency(CHAIN, firstAnchor) shouldBe true
        val target = head(index = 11, minimum = 1, hash = "D")
        val firstProgress = progress(target, anchor(index = 9, hash = "B"))
        keystore.publishTransparencyPin(CHAIN, firstProgress.getValue("anchor").jsonObject) shouldBe true
        keystore.retainTransparencyProgress(CHAIN, firstProgress) shouldBe true
        PallaSyncKeystore(preferences).transparencyProgress(CHAIN) shouldBe firstProgress

        val secondProgress = progress(target, anchor(index = 10, hash = "C"))
        keystore.publishTransparencyPin(CHAIN, secondProgress.getValue("anchor").jsonObject) shouldBe true
        keystore.retainTransparencyProgress(CHAIN, secondProgress) shouldBe true
        shouldThrow<IllegalStateException> {
            keystore.retainTransparencyProgress(CHAIN, firstProgress)
        }.message shouldBe "C_ROLLBACK_DETECTED"

        val finalAnchor = anchor(index = 11, hash = "D")
        keystore.publishTransparencyPin(CHAIN, finalAnchor) shouldBe true
        keystore.retainTransparencyProgress(CHAIN, progress(target, finalAnchor)) shouldBe true
        keystore.retainVerifiedTransparencyHead(CHAIN, target) shouldBe true
        keystore.transparencyProgress(CHAIN) shouldBe null
    }

    @Test
    fun `replacement promotion archives old identity and switches keys atomically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val old = snapshot(OLD_DEVICE, "old")
        val replacement = snapshot(NEW_DEVICE, "new")
        keystore.pinInitialDeviceId(CHAIN, OLD_DEVICE) shouldBe true
        keystore.saveActiveChainKeys(old) shouldBe true
        keystore.savePendingChainKeys(replacement) shouldBe true

        keystore.promotePendingReplacementKeys(CHAIN, OLD_DEVICE, NEW_DEVICE, "source-authority") shouldBe true

        keystore.boundDeviceId(CHAIN) shouldBe NEW_DEVICE
        keystore.getActiveChainKeys() shouldBe replacement
        keystore.getArchivedChainKeys(CHAIN) shouldBe old
        keystore.pendingReplacementOldDevice(CHAIN) shouldBe OLD_DEVICE
        keystore.pendingReplacementSourceAuthorityHash(CHAIN) shouldBe "source-authority"
        keystore.getPendingChainKeys() shouldBe null
    }

    @Test
    fun `replacement promotion rejects a mismatched old identity without rebinding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val old = snapshot(OLD_DEVICE, "old")
        val replacement = snapshot(NEW_DEVICE, "new")
        keystore.pinInitialDeviceId(CHAIN, OLD_DEVICE) shouldBe true
        keystore.saveActiveChainKeys(old) shouldBe true
        keystore.savePendingChainKeys(replacement) shouldBe true

        keystore.promotePendingReplacementKeys(CHAIN, NEW_DEVICE, OLD_DEVICE, "source-authority") shouldBe false

        keystore.boundDeviceId(CHAIN) shouldBe OLD_DEVICE
        keystore.getActiveChainKeys() shouldBe old
        keystore.getPendingChainKeys() shouldBe replacement
    }

    private fun snapshot(
        deviceId: String,
        prefix: String,
    ) = PallaSyncKeySnapshot(
        chainId = CHAIN,
        deviceId = deviceId,
        encryptionKeyBase64Url = "${prefix}_record",
        signingKeyBase64Url = "${prefix}_signing",
        publicKeyBase64Url = "${prefix}_public",
        kexPrivateKeyBase64Url = "${prefix}_kex_private",
        kexPublicKeyBase64Url = "${prefix}_kex_public",
        deviceMetaKeyBase64Url = "${prefix}_metadata",
        collectionTagKeyBase64Url = "${prefix}_tag",
        epochSecretBase64Url = "${prefix}_epoch_secret",
        epoch = 3,
    )

    private fun head(
        index: Long,
        minimum: Long,
        hash: String,
    ) = buildJsonObject {
        put("type", "PALLASYNC-TRANSPARENCY-HEAD-v3")
        put("chain_id", CHAIN)
        put("log_id", LOG)
        put("transparency_id", TRANSPARENCY)
        put("entry_index", index)
        put("transparency_hash", hash)
        put("min_available_seq", minimum)
    }

    private fun anchor(
        index: Long,
        hash: String,
    ) = buildJsonObject {
        put("transparency_id", TRANSPARENCY)
        put("index", index)
        put("hash", hash)
    }

    private fun progress(
        target: kotlinx.serialization.json.JsonObject,
        anchor: kotlinx.serialization.json.JsonObject,
    ) = buildJsonObject {
        put("target_head", target)
        put("anchor", anchor)
        put("observed_data_head_hash", "E")
        put("observed_minimum", 1)
    }

    private companion object {
        const val CHAIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val LOG = "11111111-1111-4111-8111-111111111111"
        const val TRANSPARENCY = "22222222-2222-4222-8222-222222222222"
        const val OLD_DEVICE = "33333333-3333-4333-8333-333333333333"
        const val NEW_DEVICE = "44444444-4444-4444-8444-444444444444"
    }
}
