package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncKeystoreEpochRepairTest {
    @Test
    fun `verified root replacement updates retained and active epoch secret atomically`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val snapshot =
            PallaSyncKeySnapshot(
                chainId = CHAIN,
                deviceId = DEVICE,
                encryptionKeyBase64Url = "old-record-key",
                signingKeyBase64Url = "signing-key",
                publicKeyBase64Url = "public-key",
                kexPrivateKeyBase64Url = "kex-private",
                kexPublicKeyBase64Url = "kex-public",
                deviceMetaKeyBase64Url = "old-device-meta-key",
                collectionTagKeyBase64Url = "old-tag-key",
                epochSecretBase64Url = "old-secret",
                epoch = 5,
            )
        keystore.saveActiveChainKeys(snapshot) shouldBe true
        val replacementSecret = ByteArray(32) { 0x5a }
        val replacement =
            snapshot.copy(
                encryptionKeyBase64Url = "new-record-key",
                deviceMetaKeyBase64Url = "new-device-meta-key",
                collectionTagKeyBase64Url = "new-tag-key",
                epochSecretBase64Url = "new-secret",
            )

        keystore.replaceEpochMaterial(CHAIN, 5, replacement, replacementSecret) shouldBe true
        replacementSecret.all { it == 0.toByte() } shouldBe true
        val expectedSecret =
            java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(ByteArray(32) { 0x5a })
        keystore.keysForEpoch(checkNotNull(keystore.getActiveChainKeys()), 5)?.epochSecretBase64Url shouldBe
            expectedSecret
        keystore.getActiveChainKeys()?.encryptionKeyBase64Url shouldBe "new-record-key"
        keystore.getActiveChainKeys()?.collectionTagKeyBase64Url shouldBe "new-tag-key"
        keystore.getActiveChainKeys()?.deviceMetaKeyBase64Url shouldBe "new-device-meta-key"
    }

    private companion object {
        const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val DEVICE = "11111111-1111-4111-8111-111111111111"
    }
}
