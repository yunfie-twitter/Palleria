package com.yunfie.illustia.platform

import android.content.Context
import android.os.Build

internal object DesktopEnvironment {
    fun isChromeOs(context: Context): Boolean =
        runCatching {
            context.packageManager.hasSystemFeature("org.chromium.arc") ||
                context.packageManager.hasSystemFeature("org.chromium.arc.device_management") ||
                Build.BRAND.equals("chromium", ignoreCase = true) ||
                Build.MANUFACTURER.equals("chromium", ignoreCase = true) ||
                Build.DEVICE.contains("cheets", ignoreCase = true)
        }.getOrDefault(false)

    fun isWsa(): Boolean =
        runCatching {
            Build.BRAND.equals("Windows", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Microsoft", ignoreCase = true) ||
                Build.MODEL.contains("Subsystem for Android", ignoreCase = true) ||
                Build.DEVICE.equals("windows_x86_64", ignoreCase = true) ||
                Build.DEVICE.equals("windows_arm64", ignoreCase = true)
        }.getOrDefault(false)

    fun isDesktop(context: Context): Boolean = isChromeOs(context) || isWsa()
}
