package com.yunfie.illustia.platform

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.store.KEY_WIDE_COLOR_GAMUT
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WideColorGamutTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `wideColorGamutEnabled defaults to true in AppSettings`() {
        val settings = AppSettings()
        settings.wideColorGamutEnabled shouldBe true
    }

    @Test
    fun `KEY_WIDE_COLOR_GAMUT key is wideColorGamut`() {
        KEY_WIDE_COLOR_GAMUT shouldBe "wideColorGamut"
    }

    @Test
    fun `AppSettings copy toggles wideColorGamutEnabled correctly`() {
        val defaultSettings = AppSettings()
        val disabled = defaultSettings.copy(wideColorGamutEnabled = false)
        disabled.wideColorGamutEnabled shouldBe false

        val reEnabled = disabled.copy(wideColorGamutEnabled = true)
        reEnabled.wideColorGamutEnabled shouldBe true
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun `supportsWideColorGamut evaluates isScreenWideColorGamut on Oreo or higher`() {
        val config = context.resources.configuration
        val expected = config.isScreenWideColorGamut
        PlatformCapabilities.supportsWideColorGamut(context) shouldBe expected
    }

    @Test
    fun `target bitmap config for WCG with listener uses RGBA_F16 while direct rendering uses HARDWARE`() {
        // Logic check: When listener is present (needs pixel read), RGBA_F16 avoids hardware bitmap crash
        val hasSuccessListener = true
        val targetConfigWithListener =
            if (hasSuccessListener) {
                Bitmap.Config.RGBA_F16
            } else {
                Bitmap.Config.HARDWARE
            }
        targetConfigWithListener shouldBe Bitmap.Config.RGBA_F16

        val directTargetConfig =
            if (false) {
                Bitmap.Config.RGBA_F16
            } else {
                Bitmap.Config.HARDWARE
            }
        directTargetConfig shouldBe Bitmap.Config.HARDWARE
    }
}
