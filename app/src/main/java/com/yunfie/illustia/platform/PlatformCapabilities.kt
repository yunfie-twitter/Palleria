package com.yunfie.illustia.platform

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

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

    fun isLowRamDevice(context: Context): Boolean {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return false
        if (activityManager.isLowRamDevice) {
            return true
        }
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem <= LOW_RAM_THRESHOLD_BYTES
    }

    fun isLowSpecDevice(context: Context): Boolean {
        if (isLowRamDevice(context)) {
            return true
        }
        val cores = Runtime.getRuntime().availableProcessors()
        return cores <= 2
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
