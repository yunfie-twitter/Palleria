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
        // Snapdragon 620 profile: 3 GB RAM, 6 cores, 192 MB memory class, OpenGL ES 3.1, msm8956
        val profile =
            DeviceHardwareProfile(
                totalMemBytes = 3_221_225_472L,
                isLowRamDevice = false,
                memoryClassMb = 192,
                largeMemoryClassMb = 192,
                cores = 6,
                glEsVersion = 0x00030001, // ES 3.1
                is64Bit = true,
                socOrHardware = "qcom msm8956",
            )
        PlatformCapabilities.resolvePerformanceTier(profile) shouldBe DevicePerformanceTier.LOW
    }

    @Test
    fun `resolvePerformanceTier classifies 32bit or sub-ES32 devices as LOW`() {
        // 32-bit device even with 4 GB RAM and 8 cores
        PlatformCapabilities.resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = 4_294_967_296L,
                isLowRamDevice = false,
                memoryClassMb = 256,
                largeMemoryClassMb = 384,
                cores = 8,
                is64Bit = false,
            ),
        ) shouldBe DevicePerformanceTier.LOW

        // OpenGL ES 3.0 / 3.1 GPU
        PlatformCapabilities.resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = 5_368_709_120L,
                isLowRamDevice = false,
                memoryClassMb = 256,
                largeMemoryClassMb = 384,
                cores = 8,
                glEsVersion = 0x00030000,
            ),
        ) shouldBe DevicePerformanceTier.LOW
    }

    @Test
    fun `resolvePerformanceTier identifies known low tier SoCs even with 4GB RAM`() {
        // Snapdragon 625 (msm8953) with 4 GB RAM and 8 cores
        PlatformCapabilities.resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = 4_500_000_000L,
                isLowRamDevice = false,
                memoryClassMb = 256,
                largeMemoryClassMb = 384,
                cores = 8,
                socOrHardware = "qualcomm msm8953",
            ),
        ) shouldBe DevicePerformanceTier.LOW

        // Helio P35 (mt6765)
        PlatformCapabilities.resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = 4_500_000_000L,
                isLowRamDevice = false,
                memoryClassMb = 256,
                largeMemoryClassMb = 384,
                cores = 8,
                socOrHardware = "mediatek mt6765",
            ),
        ) shouldBe DevicePerformanceTier.LOW
    }

    @Test
    fun `resolvePerformanceTier classifies mid range devices as MEDIUM`() {
        // 6 GB RAM, 8 cores, 384 MB heap, ES 3.2, modern SoC
        val profile =
            DeviceHardwareProfile(
                totalMemBytes = 6_442_450_944L,
                isLowRamDevice = false,
                memoryClassMb = 256,
                largeMemoryClassMb = 384,
                cores = 8,
                glEsVersion = PlatformCapabilities.GL_ES_VERSION_3_2,
                socOrHardware = "qcom sm7325",
            )
        PlatformCapabilities.resolvePerformanceTier(profile) shouldBe DevicePerformanceTier.MEDIUM
    }

    @Test
    fun `resolvePerformanceTier classifies flagship devices as HIGH`() {
        // 12 GB RAM, 8 cores, 512 MB large memory class, ES 3.2
        val profile =
            DeviceHardwareProfile(
                totalMemBytes = 12_884_901_888L,
                isLowRamDevice = false,
                memoryClassMb = 384,
                largeMemoryClassMb = 512,
                cores = 8,
                glEsVersion = PlatformCapabilities.GL_ES_VERSION_3_2,
                socOrHardware = "qcom sm8550",
            )
        PlatformCapabilities.resolvePerformanceTier(profile) shouldBe DevicePerformanceTier.HIGH
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
