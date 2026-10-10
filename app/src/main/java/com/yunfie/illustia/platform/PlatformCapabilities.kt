package com.yunfie.illustia.platform

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Coarse device performance tier used for dynamic hardware adaptation.
 */
internal enum class DevicePerformanceTier {
    LOW,
    MEDIUM_LOW,
    MEDIUM,
    MEDIUM_HIGH,
    HIGH,
}

/**
 * Profile of hardware capabilities and constraints for tier classification.
 */
internal data class DeviceHardwareProfile(
    val totalMemBytes: Long,
    val isLowRamDevice: Boolean,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int = memoryClassMb,
    val cores: Int,
    val glEsVersion: Int = PlatformCapabilities.GL_ES_VERSION_3_2,
    val is64Bit: Boolean = true,
    val socOrHardware: String = "",
)

/**
 * Centralized Android feature gates.
 *
 * Call sites should branch on a capability instead of an SDK number. The annotations keep Android
 * Lint aware of the guarded framework APIs, while this file remains the single place to update
 * platform thresholds when a feature gains a compat implementation.
 */
internal object PlatformCapabilities {
    const val HANDOFF_API = 37

    // OpenGL ES 3.2 is encoded as 0x00030002 in reqGlEsVersion
    internal const val GL_ES_VERSION_3_2 = 0x00030002

    // Performance tier thresholds calibrated for Palleria's lightweight memory footprint (~100-500MB max)
    private const val RAM_THRESHOLD_LOW_BYTES = 3_221_225_472L // 3.0 GB (devices with <=3GB RAM)
    private const val RAM_THRESHOLD_MEDIUM_HIGH_BYTES = 5_368_709_120L // 5.0 GiB
    private const val RAM_THRESHOLD_HIGH_BYTES = 12_884_901_888L // 12.0 GB
    private const val LOW_LARGE_HEAP_THRESHOLD_MB = 192
    private const val MEDIUM_HIGH_LARGE_HEAP_THRESHOLD_MB = 320
    private const val HIGH_MEMORY_CLASS_THRESHOLD_MB = 384
    private const val HIGH_LARGE_HEAP_THRESHOLD_MB = 512

    private const val LOW_TIER_MAX_CORES = 4
    private const val HIGH_TIER_MIN_CORES = 8
    private const val MEDIUM_LOW_MAX_CORES = 6

    private const val MAX_DECODE_DIMENSION_LOW = 1080
    private const val MAX_DECODE_DIMENSION_MEDIUM_LOW = 1280
    private const val MAX_DECODE_DIMENSION_MEDIUM = 1536
    private const val MAX_DECODE_DIMENSION_MEDIUM_HIGH = 2048
    private const val MAX_DECODE_DIMENSION_HIGH = 2560

    private const val PREFETCH_COUNT_LOW = 2
    private const val PREFETCH_COUNT_MEDIUM_LOW = 3
    private const val PREFETCH_COUNT_MEDIUM = 4
    private const val PREFETCH_COUNT_MEDIUM_HIGH = 5
    private const val PREFETCH_COUNT_HIGH = 6

    private const val DATASTORE_DEBOUNCE_LOW_MS = 1200L
    private const val DATASTORE_DEBOUNCE_MEDIUM_LOW_MS = 800L
    private const val DATASTORE_DEBOUNCE_MEDIUM_MS = 500L
    private const val DATASTORE_DEBOUNCE_MEDIUM_HIGH_MS = 400L
    private const val DATASTORE_DEBOUNCE_HIGH_MS = 300L

    private const val MAX_DECODE_DIMENSION_THUMBNAIL_LOW = 384
    private const val MAX_DECODE_DIMENSION_THUMBNAIL_MEDIUM_LOW = 448
    private const val MAX_DECODE_DIMENSION_THUMBNAIL_DEFAULT = 512

    private const val COIL_MEMORY_CACHE_PERCENT_LOW = 0.10
    private const val COIL_MEMORY_CACHE_PERCENT_MEDIUM_LOW = 0.14
    private const val COIL_MEMORY_CACHE_PERCENT_MEDIUM = 0.20
    private const val COIL_MEMORY_CACHE_PERCENT_MEDIUM_HIGH = 0.22
    private const val COIL_MEMORY_CACHE_PERCENT_HIGH = 0.25

    private const val UGOIRA_PREFETCH_AHEAD_LOW = 6
    private const val UGOIRA_PREFETCH_AHEAD_MEDIUM_LOW = 10
    private const val UGOIRA_PREFETCH_AHEAD_MEDIUM = 18
    private const val UGOIRA_PREFETCH_AHEAD_MEDIUM_HIGH = 24
    private const val UGOIRA_PREFETCH_AHEAD_HIGH = 32

    private const val UGOIRA_KEEP_BEHIND_LOW = 2
    private const val UGOIRA_KEEP_BEHIND_MEDIUM_LOW = 3
    private const val UGOIRA_KEEP_BEHIND_MEDIUM = 4
    private const val UGOIRA_KEEP_BEHIND_MEDIUM_HIGH = 5
    private const val UGOIRA_KEEP_BEHIND_HIGH = 6

    private const val UGOIRA_FRAME_CACHE_LOW = 8
    private const val UGOIRA_FRAME_CACHE_MEDIUM_LOW = 12
    private const val UGOIRA_FRAME_CACHE_MEDIUM = 24
    private const val UGOIRA_FRAME_CACHE_MEDIUM_HIGH = 36
    private const val UGOIRA_FRAME_CACHE_HIGH = 48

    private val KNOWN_LOW_TIER_SOCS =
        setOf(
            // Qualcomm Snapdragon 200 / 400 / 600 legacy & entry series
            "msm8909", // Snapdragon 210
            "msm8916", // Snapdragon 410
            "msm8926", // Snapdragon 400
            "msm8928", // Snapdragon 400
            "msm8929", // Snapdragon 415
            "msm8936", // Snapdragon 610
            "msm8939", // Snapdragon 615 / 616
            "msm8952", // Snapdragon 617
            "msm8956", // Snapdragon 620
            "msm8976", // Snapdragon 652
            "msm8953", // Snapdragon 625 / 626
            "msm8937", // Snapdragon 430
            "msm8917", // Snapdragon 425
            "msm8940", // Snapdragon 435
            "sdm429", // Snapdragon 429
            "sdm439", // Snapdragon 439
            "sdm450", // Snapdragon 450
            "sm4250", // Snapdragon 460
            "sm4350", // Snapdragon 480 / 480+
            "qcm2150", // Snapdragon 215
            "qcm2290", // Snapdragon 2290
            "sdm630", // Snapdragon 630
            "sdm632", // Snapdragon 632
            "sdm636", // Snapdragon 636
            "sm6115", // Snapdragon 662
            "sm6125", // Snapdragon 665
            "msm8992", // Snapdragon 808
            "msm8994", // Snapdragon 810
            // MediaTek Helio A / P / low-end G / MT series
            "mt6580", // MT6580
            "mt6582", // MT6582
            "mt6735", // MT6735
            "mt6737", // MT6737
            "mt6739", // MT6739
            "mt6750", // MT6750
            "mt6752", // MT6752
            "mt6753", // MT6753
            "mt6755", // Helio P10
            "mt6757", // Helio P20 / P25
            "mt6761", // Helio A22
            "mt6762", // Helio P22 / A25
            "mt6763", // Helio P23
            "mt6765", // Helio P35 / G35 / G37
            "mt6768", // Helio P65 / G85
            "mt6769", // Helio G70 / G80 / G85 / G88
            "mt6771", // Helio P60 / P70
            "mt8163", // MT8163 (Fire Tablet)
            "mt8167", // MT8167 (Fire Tablet)
            "mt8168", // MT8168 (Fire HD 8)
            "mt8173", // MT8173
            "mt8765", // MT8765
            "mt8766", // MT8766
            "mt8768", // MT8768
            // Unisoc / Spreadtrum
            "sc7731", // SC7731
            "sc9832", // SC9832E
            "sc9863", // SC9863
            "sc9863a", // SC9863A
            "sp9863a", // SP9863A
            "ums312", // Unisoc T310
            "ums512", // Unisoc T618 / T616
            "ums9230", // Unisoc T606
            "t610", // Unisoc T610
            "t606", // Unisoc T606
            "sl8541e", // SL8541E
            "sl8521e", // SL8521E
            // Samsung Exynos (legacy & sluggish entry)
            "exynos7870", // Exynos 7870
            "exynos7880", // Exynos 7880
            "exynos7884", // Exynos 7884
            "exynos7885", // Exynos 7885
            "exynos7904", // Exynos 7904
            "exynos850", // Exynos 850 (A55x8 sluggish)
            "exynos9610", // Exynos 9610
            "exynos9611", // Exynos 9611
            "s5e3830", // Exynos 850 part code
            // Rockchip / JLQ / Allwinner
            "jr510", // JLQ JR510 (POCO C40)
            "rk3326", // Rockchip RK3326
            "rk3328", // Rockchip RK3328
            "rk3566", // Rockchip RK3566
            "a133", // Allwinner A133
            "h616", // Allwinner H616
            "h618", // Allwinner H618
        )

    @Volatile
    private var cachedPerformanceTier: DevicePerformanceTier? = null
    private val tierLock = Any()

    fun devicePerformanceTier(context: Context): DevicePerformanceTier {
        cachedPerformanceTier?.let { return it }
        return synchronized(tierLock) {
            cachedPerformanceTier ?: resolvePerformanceTier(context.applicationContext).also {
                cachedPerformanceTier = it
            }
        }
    }

    internal fun resolvePerformanceTier(context: Context): DevicePerformanceTier {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalMem = memoryInfo.totalMem
        val isLowRam = activityManager?.isLowRamDevice == true
        val memoryClass = activityManager?.memoryClass ?: 0
        val largeMemoryClass = activityManager?.largeMemoryClass ?: memoryClass
        val cores = Runtime.getRuntime().availableProcessors()
        val glEsVersion = activityManager?.deviceConfigurationInfo?.reqGlEsVersion ?: GL_ES_VERSION_3_2
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val socOrHardware =
            listOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "",
                Build.HARDWARE,
                Build.BOARD,
            ).filter { it.isNotBlank() }.joinToString(" ").lowercase()

        return resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = totalMem,
                isLowRamDevice = isLowRam,
                memoryClassMb = memoryClass,
                largeMemoryClassMb = largeMemoryClass,
                cores = cores,
                glEsVersion = glEsVersion,
                is64Bit = is64Bit,
                socOrHardware = socOrHardware,
            ),
        )
    }

    @Suppress("CyclomaticComplexMethod")
    internal fun resolvePerformanceTier(profile: DeviceHardwareProfile): DevicePerformanceTier {
        val isGlConstrained = profile.glEsVersion < GL_ES_VERSION_3_2
        val isArchitectureConstrained = !profile.is64Bit || profile.isLowRamDevice || isGlConstrained
        val isKnownLowSoc = isKnownLowTierSoc(profile.socOrHardware)

        val isMemoryConstrained = profile.totalMemBytes in 1..RAM_THRESHOLD_LOW_BYTES
        val isHeapConstrained = profile.largeMemoryClassMb in 1..LOW_LARGE_HEAP_THRESHOLD_MB
        val isCpuConstrained = profile.cores <= LOW_TIER_MAX_CORES
        val isHardwareConstrained = isMemoryConstrained || isHeapConstrained || isCpuConstrained

        val isLowTier = isArchitectureConstrained || isKnownLowSoc || isHardwareConstrained

        val isMediumLowTier =
            profile.totalMemBytes < 4_831_838_208L || // below 4.5 GB
                profile.largeMemoryClassMb <= 256 ||
                profile.cores <= MEDIUM_LOW_MAX_CORES

        val isMediumHighTier =
            profile.totalMemBytes >= RAM_THRESHOLD_MEDIUM_HIGH_BYTES &&
                profile.largeMemoryClassMb >= MEDIUM_HIGH_LARGE_HEAP_THRESHOLD_MB &&
                profile.cores >= HIGH_TIER_MIN_CORES

        val isHighTier =
            profile.totalMemBytes >= RAM_THRESHOLD_HIGH_BYTES &&
                profile.memoryClassMb >= HIGH_MEMORY_CLASS_THRESHOLD_MB &&
                profile.largeMemoryClassMb >= HIGH_LARGE_HEAP_THRESHOLD_MB &&
                profile.cores >= HIGH_TIER_MIN_CORES

        return when {
            isLowTier -> DevicePerformanceTier.LOW
            isHighTier -> DevicePerformanceTier.HIGH
            isMediumHighTier -> DevicePerformanceTier.MEDIUM_HIGH
            isMediumLowTier -> DevicePerformanceTier.MEDIUM_LOW
            else -> DevicePerformanceTier.MEDIUM
        }
    }

    internal fun resolvePerformanceTier(
        totalMemBytes: Long,
        isLowRamDevice: Boolean,
        memoryClassMb: Int,
        cores: Int,
    ): DevicePerformanceTier =
        resolvePerformanceTier(
            DeviceHardwareProfile(
                totalMemBytes = totalMemBytes,
                isLowRamDevice = isLowRamDevice,
                memoryClassMb = memoryClassMb,
                largeMemoryClassMb = memoryClassMb,
                cores = cores,
            ),
        )

    private fun isKnownLowTierSoc(socString: String): Boolean {
        if (socString.isBlank()) return false
        val normalized = socString.lowercase()
        return KNOWN_LOW_TIER_SOCS.any { normalized.contains(it) }
    }

    fun isLowRamDevice(context: Context): Boolean = devicePerformanceTier(context) == DevicePerformanceTier.LOW

    fun isLowSpecDevice(context: Context): Boolean =
        devicePerformanceTier(context) in setOf(DevicePerformanceTier.LOW, DevicePerformanceTier.MEDIUM_LOW)

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsHardwareBlur(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            devicePerformanceTier(context) !in setOf(DevicePerformanceTier.LOW, DevicePerformanceTier.MEDIUM_LOW)

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun supportsWideColorGamut(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.resources.configuration.isScreenWideColorGamut

    fun recommendedBitmapConfig(context: Context): Bitmap.Config =
        if (devicePerformanceTier(context) == DevicePerformanceTier.LOW) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }

    fun maxImageDecodeDimension(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> MAX_DECODE_DIMENSION_LOW
            DevicePerformanceTier.MEDIUM_LOW -> MAX_DECODE_DIMENSION_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> MAX_DECODE_DIMENSION_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> MAX_DECODE_DIMENSION_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> MAX_DECODE_DIMENSION_HIGH
        }

    fun recommendedThumbnailDecodeDimension(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> MAX_DECODE_DIMENSION_THUMBNAIL_LOW
            DevicePerformanceTier.MEDIUM_LOW -> MAX_DECODE_DIMENSION_THUMBNAIL_MEDIUM_LOW
            else -> MAX_DECODE_DIMENSION_THUMBNAIL_DEFAULT
        }

    fun recommendedPrefetchItemCount(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> PREFETCH_COUNT_LOW
            DevicePerformanceTier.MEDIUM_LOW -> PREFETCH_COUNT_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> PREFETCH_COUNT_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> PREFETCH_COUNT_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> PREFETCH_COUNT_HIGH
        }

    fun supportsRichAnimations(context: Context): Boolean =
        devicePerformanceTier(context) !in setOf(DevicePerformanceTier.LOW, DevicePerformanceTier.MEDIUM_LOW)

    fun supportsImageCrossfade(context: Context): Boolean =
        devicePerformanceTier(context) !in setOf(DevicePerformanceTier.LOW, DevicePerformanceTier.MEDIUM_LOW)

    fun recommendedCoilMemoryCachePercent(context: Context): Double =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> COIL_MEMORY_CACHE_PERCENT_LOW
            DevicePerformanceTier.MEDIUM_LOW -> COIL_MEMORY_CACHE_PERCENT_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> COIL_MEMORY_CACHE_PERCENT_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> COIL_MEMORY_CACHE_PERCENT_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> COIL_MEMORY_CACHE_PERCENT_HIGH
        }

    fun recommendedUgoiraPrefetchAhead(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> UGOIRA_PREFETCH_AHEAD_LOW
            DevicePerformanceTier.MEDIUM_LOW -> UGOIRA_PREFETCH_AHEAD_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> UGOIRA_PREFETCH_AHEAD_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> UGOIRA_PREFETCH_AHEAD_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> UGOIRA_PREFETCH_AHEAD_HIGH
        }

    fun recommendedUgoiraKeepBehind(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> UGOIRA_KEEP_BEHIND_LOW
            DevicePerformanceTier.MEDIUM_LOW -> UGOIRA_KEEP_BEHIND_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> UGOIRA_KEEP_BEHIND_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> UGOIRA_KEEP_BEHIND_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> UGOIRA_KEEP_BEHIND_HIGH
        }

    fun recommendedUgoiraMaxCachedFrames(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> UGOIRA_FRAME_CACHE_LOW
            DevicePerformanceTier.MEDIUM_LOW -> UGOIRA_FRAME_CACHE_MEDIUM_LOW
            DevicePerformanceTier.MEDIUM -> UGOIRA_FRAME_CACHE_MEDIUM
            DevicePerformanceTier.MEDIUM_HIGH -> UGOIRA_FRAME_CACHE_MEDIUM_HIGH
            DevicePerformanceTier.HIGH -> UGOIRA_FRAME_CACHE_HIGH
        }

    fun recommendedDataStoreDebounceMs(context: Context): Long =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> DATASTORE_DEBOUNCE_LOW_MS
            DevicePerformanceTier.MEDIUM_LOW -> DATASTORE_DEBOUNCE_MEDIUM_LOW_MS
            DevicePerformanceTier.MEDIUM -> DATASTORE_DEBOUNCE_MEDIUM_MS
            DevicePerformanceTier.MEDIUM_HIGH -> DATASTORE_DEBOUNCE_MEDIUM_HIGH_MS
            DevicePerformanceTier.HIGH -> DATASTORE_DEBOUNCE_HIGH_MS
        }

    internal fun setPerformanceTierForTesting(tier: DevicePerformanceTier?) {
        synchronized(tierLock) {
            cachedPerformanceTier = tier
        }
    }

    private val currentSnapshot by lazy(LazyThreadSafetyMode.PUBLICATION) {
        forSdk(Build.VERSION.SDK_INT)
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun supportsStorageStats(): Boolean = current().supportsStorageStats

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun supportsDetailedStorageStats(): Boolean = current().supportsDetailedStorageStats

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    fun supportsAnimatedImageDecoder(): Boolean = current().supportsAnimatedImageDecoder

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    fun supportsClipboardClear(): Boolean = current().supportsClipboardClear

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun supportsScopedMediaStore(): Boolean = current().supportsScopedMediaStore

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsDynamicColor(): Boolean = current().supportsDynamicColor

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun supportsPlatformLocaleManager(): Boolean = current().supportsPlatformLocaleManager

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun supportsRecentsScreenshotControl(): Boolean = current().supportsRecentsScreenshotControl

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun supportsPredictiveBack(): Boolean = current().supportsPredictiveBack

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun supportsRefreshRateHint(): Boolean = current().supportsRefreshRateHint

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun supportsAdaptiveRefreshRate(): Boolean = current().supportsAdaptiveRefreshRate

    @ChecksSdkIntAtLeast(api = HANDOFF_API)
    fun supportsActivityHandoff(): Boolean = current().supportsActivityHandoff

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun supportsWidgetPreview(): Boolean = current().supportsWidgetPreview

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun supportsRemoteViewsSharedElement(): Boolean = current().supportsRemoteViewsSharedElement

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsVibratorManager(): Boolean = current().supportsVibratorManager

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun supportsVibrationComposition(): Boolean = current().supportsVibrationComposition

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun supportsPredefinedVibrationEffect(): Boolean = current().supportsPredefinedVibrationEffect

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun supportsVibrationEffect(): Boolean = current().supportsVibrationEffect

    fun requiresLegacyStoragePermission(): Boolean = current().requiresLegacyStoragePermission

    internal fun forSdk(sdkInt: Int): PlatformCapabilitySnapshot = PlatformCapabilitySnapshot(sdkInt)

    private fun current(): PlatformCapabilitySnapshot = currentSnapshot
}

internal class PlatformCapabilitySnapshot(
    sdkInt: Int,
) {
    val supportsStorageStats = sdkInt >= Build.VERSION_CODES.O
    val supportsDetailedStorageStats = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    val supportsAnimatedImageDecoder = sdkInt >= Build.VERSION_CODES.P
    val supportsClipboardClear = sdkInt >= Build.VERSION_CODES.P
    val supportsScopedMediaStore = sdkInt >= Build.VERSION_CODES.Q
    val supportsDynamicColor = sdkInt >= Build.VERSION_CODES.S
    val supportsPlatformLocaleManager = sdkInt >= Build.VERSION_CODES.TIRAMISU
    val supportsRecentsScreenshotControl = sdkInt >= Build.VERSION_CODES.TIRAMISU
    val supportsPredictiveBack = sdkInt >= Build.VERSION_CODES.TIRAMISU
    val supportsRefreshRateHint = sdkInt >= Build.VERSION_CODES.R
    val supportsAdaptiveRefreshRate = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    val supportsActivityHandoff = sdkInt >= PlatformCapabilities.HANDOFF_API
    val supportsWidgetPreview = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    val supportsRemoteViewsSharedElement = sdkInt >= Build.VERSION_CODES.Q
    val supportsVibratorManager = sdkInt >= Build.VERSION_CODES.S
    val supportsVibrationComposition = sdkInt >= Build.VERSION_CODES.R
    val supportsPredefinedVibrationEffect = sdkInt >= Build.VERSION_CODES.Q
    val supportsVibrationEffect = sdkInt >= Build.VERSION_CODES.O
    val requiresLegacyStoragePermission = !supportsScopedMediaStore
}
