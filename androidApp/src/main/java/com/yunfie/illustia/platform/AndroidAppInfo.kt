package com.yunfie.illustia.platform

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

class AndroidAppInfo(
    private val context: Context,
) : AppInfo {
    override fun packageName(): String = context.packageName

    @Suppress("TooGenericExceptionCaught")
    override fun versionCode(): Int? =
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            PackageInfoCompat.getLongVersionCode(info).toInt()
        } catch (_: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught")
    override fun versionName(): String? =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
}
