package com.yunfie.illustia.wallpaper

import android.os.Build

internal object LiveWallpaperSupport {
    fun isSupported(): Boolean {
        return !isHyperOsDevice(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            osVersionName = systemProperty("ro.mi.os.version.name"),
            incremental = Build.VERSION.INCREMENTAL,
            display = Build.DISPLAY,
        )
    }
}

internal fun isHyperOsDevice(
    manufacturer: String,
    brand: String,
    osVersionName: String,
    incremental: String,
    display: String,
): Boolean {
    val vendor = "$manufacturer $brand".lowercase()
    val isXiaomiFamily = listOf("xiaomi", "redmi", "poco").any(vendor::contains)
    if (!isXiaomiFamily) return false

    return osVersionName.isNotBlank() ||
        incremental.startsWith("OS", ignoreCase = true) ||
        display.contains("HyperOS", ignoreCase = true)
}

private fun systemProperty(name: String): String {
    return runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        systemProperties.getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull().orEmpty()
}
