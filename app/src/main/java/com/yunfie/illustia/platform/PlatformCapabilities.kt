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
    MEDIUM,
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
    private const val RAM_THRESHOLD_HIGH_BYTES = 5_368_709_120L // 5.0 GB (devices with >=6GB RAM)
    private const val LOW_LARGE_HEAP_THRESHOLD_MB = 192
    private const val HIGH_LARGE_HEAP_THRESHOLD_MB = 384

    private const val LOW_TIER_MAX_CORES = 4
    private const val HIGH_TIER_MIN_CORES = 8

    private const val MAX_DECODE_DIMENSION_LOW = 1080
    private const val MAX_DECODE_DIMENSION_MEDIUM = 1536
    private const val MAX_DECODE_DIMENSION_HIGH = 2560

    private const val PREFETCH_COUNT_LOW = 2
    private const val PREFETCH_COUNT_MEDIUM = 4
    private const val PREFETCH_COUNT_HIGH = 6

    private const val DATASTORE_DEBOUNCE_LOW_MS = 1200L
    private const val DATASTORE_DEBOUNCE_DEFAULT_MS = 500L

    private val KNOWN_LOW_TIER_SOCS =
        setOf(
            "msm8956", // Snapdragon 620
            "msm8976", // Snapdragon 652
            "msm8953", // Snapdragon 625
            "msm8937", // Snapdragon 430
            "msm8917", // Snapdragon 425
            "msm8940", // Snapdragon 435
            "sdm439", // Snapdragon 439
            "sdm450", // Snapdragon 450
            "mt6765", // Helio P35 / G35
            "mt6762", // Helio P22
            "mt6761", // Helio A22
            "sc9863a", // Unisoc SC9863A
            "ums512", // Unisoc T618
            "ums312", // Unisoc T310
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

    internal fun resolvePerformanceTier(profile: DeviceHardwareProfile): DevicePerformanceTier {
        val isGlConstrained = profile.glEsVersion < GL_ES_VERSION_3_2
        val isArchitectureConstrained = !profile.is64Bit || profile.isLowRamDevice || isGlConstrained
        val isKnownLowSoc = isKnownLowTierSoc(profile.socOrHardware)

        val isMemoryConstrained = profile.totalMemBytes in 1..RAM_THRESHOLD_LOW_BYTES
        val isHeapConstrained = profile.largeMemoryClassMb in 1..LOW_LARGE_HEAP_THRESHOLD_MB
        val isCpuConstrained = profile.cores <= LOW_TIER_MAX_CORES
        val isHardwareConstrained = isMemoryConstrained || isHeapConstrained || isCpuConstrained

        val isLowTier = isArchitectureConstrained || isKnownLowSoc || isHardwareConstrained

        val isHighRam = profile.totalMemBytes >= RAM_THRESHOLD_HIGH_BYTES
        val isHighHeap = profile.largeMemoryClassMb >= HIGH_LARGE_HEAP_THRESHOLD_MB
        val isHighCores = profile.cores >= HIGH_TIER_MIN_CORES
        val isHighTier = isHighRam && isHighHeap && isHighCores

        return when {
            isLowTier -> DevicePerformanceTier.LOW
            isHighTier -> DevicePerformanceTier.HIGH
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

    fun isLowSpecDevice(context: Context): Boolean = devicePerformanceTier(context) == DevicePerformanceTier.LOW

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsHardwareBlur(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && devicePerformanceTier(context) != DevicePerformanceTier.LOW

    fun recommendedBitmapConfig(context: Context): Bitmap.Config =
        if (devicePerformanceTier(context) == DevicePerformanceTier.LOW) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }

    fun maxImageDecodeDimension(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> MAX_DECODE_DIMENSION_LOW
            DevicePerformanceTier.MEDIUM -> MAX_DECODE_DIMENSION_MEDIUM
            DevicePerformanceTier.HIGH -> MAX_DECODE_DIMENSION_HIGH
        }

    fun recommendedPrefetchItemCount(context: Context): Int =
        when (devicePerformanceTier(context)) {
            DevicePerformanceTier.LOW -> PREFETCH_COUNT_LOW
            DevicePerformanceTier.MEDIUM -> PREFETCH_COUNT_MEDIUM
            DevicePerformanceTier.HIGH -> PREFETCH_COUNT_HIGH
        }

    fun supportsRichAnimations(context: Context): Boolean = devicePerformanceTier(context) != DevicePerformanceTier.LOW

    fun recommendedDataStoreDebounceMs(context: Context): Long =
        if (devicePerformanceTier(context) == DevicePerformanceTier.LOW) {
            DATASTORE_DEBOUNCE_LOW_MS
        } else {
            DATASTORE_DEBOUNCE_DEFAULT_MS
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

    @ChecksSdkIntAtLeast(api = 36)
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
    val supportsAdaptiveRefreshRate = sdkInt >= 36
    val supportsActivityHandoff = sdkInt >= PlatformCapabilities.HANDOFF_API
    val supportsWidgetPreview = sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM
    val supportsRemoteViewsSharedElement = sdkInt >= Build.VERSION_CODES.Q
    val supportsVibratorManager = sdkInt >= Build.VERSION_CODES.S
    val supportsVibrationComposition = sdkInt >= Build.VERSION_CODES.R
    val supportsPredefinedVibrationEffect = sdkInt >= Build.VERSION_CODES.Q
    val supportsVibrationEffect = sdkInt >= Build.VERSION_CODES.O
    val requiresLegacyStoragePermission = !supportsScopedMediaStore
}
