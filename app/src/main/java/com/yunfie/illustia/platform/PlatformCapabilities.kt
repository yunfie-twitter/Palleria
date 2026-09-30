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
 * Centralized Android feature gates.
 *
 * Call sites should branch on a capability instead of an SDK number. The annotations keep Android
 * Lint aware of the guarded framework APIs, while this file remains the single place to update
 * platform thresholds when a feature gains a compat implementation.
 */
internal object PlatformCapabilities {
    const val HANDOFF_API = 37
    const val LOW_RAM_THRESHOLD_BYTES = 3_758_096_384L // 3.5 GB
    const val MEDIUM_RAM_THRESHOLD_BYTES = 6_442_450_944L // 6.0 GB
    const val LOW_MEMORY_CLASS_THRESHOLD_MB = 192

    private const val LOW_TIER_MAX_CORES = 4
    private const val MEDIUM_TIER_MAX_CORES = 6

    private const val MAX_DECODE_DIMENSION_LOW = 1080
    private const val MAX_DECODE_DIMENSION_MEDIUM = 1536
    private const val MAX_DECODE_DIMENSION_HIGH = 2560

    private const val PREFETCH_COUNT_LOW = 2
    private const val PREFETCH_COUNT_MEDIUM = 4
    private const val PREFETCH_COUNT_HIGH = 6

    private const val DATASTORE_DEBOUNCE_LOW_MS = 1200L
    private const val DATASTORE_DEBOUNCE_DEFAULT_MS = 500L

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

        return resolvePerformanceTier(
            totalMemBytes = memoryInfo.totalMem,
            isLowRamDevice = activityManager?.isLowRamDevice == true,
            memoryClassMb = activityManager?.memoryClass ?: 0,
            cores = Runtime.getRuntime().availableProcessors(),
        )
    }

    internal fun resolvePerformanceTier(
        totalMemBytes: Long,
        isLowRamDevice: Boolean,
        memoryClassMb: Int,
        cores: Int,
    ): DevicePerformanceTier {
        val isRamConstrained = isLowRamDevice || totalMemBytes in 1..LOW_RAM_THRESHOLD_BYTES
        val isHardwareConstrained = memoryClassMb in 1..LOW_MEMORY_CLASS_THRESHOLD_MB || cores <= LOW_TIER_MAX_CORES
        val isLowTier = isRamConstrained || isHardwareConstrained

        val isMediumTier = totalMemBytes in 1..MEDIUM_RAM_THRESHOLD_BYTES || cores <= MEDIUM_TIER_MAX_CORES

        return when {
            isLowTier -> DevicePerformanceTier.LOW
            isMediumTier -> DevicePerformanceTier.MEDIUM
            else -> DevicePerformanceTier.HIGH
        }
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
