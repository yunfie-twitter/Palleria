package com.yunfie.illustia.pallasync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android/JNI/Keystore boundary; this does not certify recovery UI or hardware StrongBox. */
@RunWith(AndroidJUnit4::class)
class PallaSyncDeviceBoundaryTest {
    private val phrase = (List(23) { "abandon" } + "art").joinToString(" ")

    @Test
    fun nativeStrictParserAndCancellationDoNotReviveAuthority() {
        PallaSyncCore.setSecretForeground(true)
        val handle = PallaSyncCore.beginRootSession(phrase.toByteArray(), byteArrayOf(), "enrollment", null, null)
        assertTrue(handle > 0)
        assertTrue(PallaSyncCore.rootIdentity(handle).contains("ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"))
        assertEquals(null, PallaSyncCore.objectHash("{\"generation\":-0}"))
        assertEquals(null, PallaSyncCore.objectHash("{\"same\":1,\"same\":2}"))
        PallaSyncCore.setSecretForeground(false)
        assertTrue(runCatching { PallaSyncCore.rootIdentity(handle) }.isFailure)
        PallaSyncCore.setSecretForeground(true)
        assertTrue(runCatching { PallaSyncCore.rootIdentity(handle) }.isFailure)
        PallaSyncCore.endSecretSessions()
    }

    @Test
    fun durablePendingIdentityIsReadBackBeforePromotion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(PallaSyncPlatformKeys.master(context).isKeyStoreBacked)
        assertFalse(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        val store = PallaSyncKeystore(context)
        store.clearAllKeys()
        val identity = "11111111-1111-4111-8111-111111111111"
        val candidate =
            PallaSyncKeySnapshot(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                deviceId = identity,
                encryptionKeyBase64Url = "public-fixture-record-key",
                signingKeyBase64Url = "public-fixture-device-key",
                publicKeyBase64Url = "public-fixture-verification-key",
            )
        assertTrue(store.savePendingChainKeys(candidate))
        assertEquals(candidate, PallaSyncKeystore(context).getPendingChainKeys())
        assertFalse(store.promotePendingChainKeys())
        assertTrue(store.pinInitialDeviceId(checkNotNull(candidate.chainId), identity))
        assertTrue(store.promotePendingChainKeys())
        val reopened = PallaSyncKeystore(context)
        assertEquals(identity, reopened.getDeviceId())
        assertEquals(candidate, reopened.getActiveChainKeys())
        assertEquals(null, reopened.getPendingChainKeys())
        store.clearAllKeys()
    }
}
