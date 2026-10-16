package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
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

    private companion object {
        const val CHAIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val LOG = "11111111-1111-4111-8111-111111111111"
        const val TRANSPARENCY = "22222222-2222-4222-8222-222222222222"
    }
}
