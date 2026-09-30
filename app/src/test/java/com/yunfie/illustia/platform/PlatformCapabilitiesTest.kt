package com.yunfie.illustia.platform

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlatformCapabilitiesTest {
    @After
    fun tearDown() {
        PlatformCapabilities.setPerformanceTierForTesting(null)
    }

    @Test
    fun `API 24 selects every required fallback`() {
        PlatformCapabilities.forSdk(24).apply {
            supportsStorageStats.shouldBeFalse()
            supportsDetailedStorageStats.shouldBeFalse()
            supportsAnimatedImageDecoder.shouldBeFalse()
            supportsClipboardClear.shouldBeFalse()
            supportsScopedMediaStore.shouldBeFalse()
            supportsDynamicColor.shouldBeFalse()
            supportsPlatformLocaleManager.shouldBeFalse()
            supportsRecentsScreenshotControl.shouldBeFalse()
            supportsPredictiveBack.shouldBeFalse()
            supportsRefreshRateHint.shouldBeFalse()
            supportsAdaptiveRefreshRate.shouldBeFalse()
            supportsActivityHandoff.shouldBeFalse()
            supportsWidgetPreview.shouldBeFalse()
            supportsRemoteViewsSharedElement.shouldBeFalse()
            supportsVibratorManager.shouldBeFalse()
            supportsVibrationComposition.shouldBeFalse()
            supportsPredefinedVibrationEffect.shouldBeFalse()
            supportsVibrationEffect.shouldBeFalse()
            requiresLegacyStoragePermission.shouldBeTrue()
        }
    }

    @Test
    fun `capabilities switch at their documented API boundaries`() {
        PlatformCapabilities.forSdk(25).supportsStorageStats.shouldBeFalse()
        PlatformCapabilities.forSdk(26).supportsStorageStats.shouldBeTrue()
        PlatformCapabilities.forSdk(27).supportsClipboardClear.shouldBeFalse()
        PlatformCapabilities.forSdk(28).supportsClipboardClear.shouldBeTrue()
        PlatformCapabilities.forSdk(28).supportsScopedMediaStore.shouldBeFalse()
        PlatformCapabilities.forSdk(29).supportsScopedMediaStore.shouldBeTrue()
        PlatformCapabilities.forSdk(30).supportsVibrationComposition.shouldBeTrue()
        PlatformCapabilities.forSdk(31).supportsDynamicColor.shouldBeTrue()
        PlatformCapabilities.forSdk(33).supportsPlatformLocaleManager.shouldBeTrue()
        PlatformCapabilities.forSdk(35).supportsWidgetPreview.shouldBeTrue()
        PlatformCapabilities.forSdk(36).supportsAdaptiveRefreshRate.shouldBeTrue()
        PlatformCapabilities.forSdk(37).supportsActivityHandoff.shouldBeTrue()
    }

    @Test
    fun `resolvePerformanceTier classifies Snapdragon 620 tier devices as LOW`() {
        // Snapdragon 620 profile: 3 GB RAM, 6 cores, 192 MB memory class
        val tier =
            PlatformCapabilities.resolvePerformanceTier(
                totalMemBytes = 3_221_225_472L,
                isLowRamDevice = false,
                memoryClassMb = 192,
                cores = 6,
            )
        tier shouldBe DevicePerformanceTier.LOW
    }

    @Test
    fun `resolvePerformanceTier classifies low RAM or low core count as LOW`() {
        // 2 GB RAM, 4 cores
        PlatformCapabilities.resolvePerformanceTier(
            totalMemBytes = 2_147_483_648L,
            isLowRamDevice = true,
            memoryClassMb = 128,
            cores = 4,
        ) shouldBe DevicePerformanceTier.LOW

        // 4 cores even with 8 GB RAM
        PlatformCapabilities.resolvePerformanceTier(
            totalMemBytes = 8_589_934_592L,
            isLowRamDevice = false,
            memoryClassMb = 256,
            cores = 4,
        ) shouldBe DevicePerformanceTier.LOW
    }

    @Test
    fun `resolvePerformanceTier classifies mid range devices as MEDIUM`() {
        // 6 GB RAM, 8 cores
        PlatformCapabilities.resolvePerformanceTier(
            totalMemBytes = 6_442_450_944L,
            isLowRamDevice = false,
            memoryClassMb = 256,
            cores = 8,
        ) shouldBe DevicePerformanceTier.MEDIUM

        // 8 GB RAM but 6 cores
        PlatformCapabilities.resolvePerformanceTier(
            totalMemBytes = 8_589_934_592L,
            isLowRamDevice = false,
            memoryClassMb = 256,
            cores = 6,
        ) shouldBe DevicePerformanceTier.MEDIUM
    }

    @Test
    fun `resolvePerformanceTier classifies flagship devices as HIGH`() {
        // 12 GB RAM, 8 cores, 512 MB memory class
        PlatformCapabilities.resolvePerformanceTier(
            totalMemBytes = 12_884_901_888L,
            isLowRamDevice = false,
            memoryClassMb = 512,
            cores = 8,
        ) shouldBe DevicePerformanceTier.HIGH
    }

    @Test
    fun `low tier recommendations adapt correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PlatformCapabilities.setPerformanceTierForTesting(DevicePerformanceTier.LOW)

        PlatformCapabilities.isLowRamDevice(context).shouldBeTrue()
        PlatformCapabilities.isLowSpecDevice(context).shouldBeTrue()
        PlatformCapabilities.recommendedBitmapConfig(context) shouldBe Bitmap.Config.RGB_565
        PlatformCapabilities.maxImageDecodeDimension(context) shouldBe 1080
        PlatformCapabilities.recommendedPrefetchItemCount(context) shouldBe 2
        PlatformCapabilities.supportsRichAnimations(context).shouldBeFalse()
        PlatformCapabilities.recommendedDataStoreDebounceMs(context) shouldBe 1200L
        PlatformCapabilities.supportsHardwareBlur(context).shouldBeFalse()
    }

    @Test
    fun `medium and high tier recommendations adapt correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        PlatformCapabilities.setPerformanceTierForTesting(DevicePerformanceTier.MEDIUM)
        PlatformCapabilities.isLowRamDevice(context).shouldBeFalse()
        PlatformCapabilities.isLowSpecDevice(context).shouldBeFalse()
        PlatformCapabilities.recommendedBitmapConfig(context) shouldBe Bitmap.Config.ARGB_8888
        PlatformCapabilities.maxImageDecodeDimension(context) shouldBe 1536
        PlatformCapabilities.recommendedPrefetchItemCount(context) shouldBe 4
        PlatformCapabilities.supportsRichAnimations(context).shouldBeTrue()
        PlatformCapabilities.recommendedDataStoreDebounceMs(context) shouldBe 500L

        PlatformCapabilities.setPerformanceTierForTesting(DevicePerformanceTier.HIGH)
        PlatformCapabilities.recommendedBitmapConfig(context) shouldBe Bitmap.Config.ARGB_8888
        PlatformCapabilities.maxImageDecodeDimension(context) shouldBe 2560
        PlatformCapabilities.recommendedPrefetchItemCount(context) shouldBe 6
        PlatformCapabilities.supportsRichAnimations(context).shouldBeTrue()
        PlatformCapabilities.recommendedDataStoreDebounceMs(context) shouldBe 500L
    }
}
