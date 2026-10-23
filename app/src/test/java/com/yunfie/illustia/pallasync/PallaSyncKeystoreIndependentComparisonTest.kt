package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28])
class PallaSyncKeystoreIndependentComparisonTest {
    @Test
    fun `independent checkpoint comparison persists per chain and is removed with verified deletion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pallasync_keystore", Context.MODE_PRIVATE)
        preferences.edit().clear().commit() shouldBe true
        val keystore = PallaSyncKeystore(preferences)
        val comparison =
            PallaSyncIndependentCheckpointComparison(
                checkedAtMs = 1_800_000_000_000,
                peerDeviceId = "11111111-1111-4111-8111-111111111111",
                dataSequence = 72,
                transparencyIndex = 91,
            )

        keystore.independentCheckpointComparison(CHAIN) shouldBe null
        keystore.retainIndependentCheckpointComparison(CHAIN, comparison) shouldBe true
        PallaSyncKeystore(preferences).independentCheckpointComparison(CHAIN) shouldBe comparison
        PallaSyncKeystore(preferences).independentCheckpointComparison(OTHER_CHAIN) shouldBe null

        keystore.clearVerifiedDeletedChain(CHAIN) shouldBe true
        keystore.independentCheckpointComparison(CHAIN) shouldBe null
    }

    private companion object {
        const val CHAIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val OTHER_CHAIN = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
