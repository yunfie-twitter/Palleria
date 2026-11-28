package com.yunfie.illustia.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat

/** 現在の Compose ツリーから [AppInfo] を取得する。 */
@Composable
fun rememberAppInfo(): AppInfo {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppInfo(context) }
}

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
