package com.yunfie.illustia.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object ApkInstallIntents {
    private const val MIME_TYPE_APK = "application/vnd.android.package-archive"

    /**
     * Builds the ACTION_VIEW intent handed to the system package installer.
     *
     * Android 12+ requires every receiving package to hold an explicit read grant on the content
     * URI; merely setting FLAG_GRANT_READ_URI_PERMISSION does not carry the grant across the
     * process boundary, so the installer crashes with a SecurityException when it opens the file.
     * Grant the URI to every activity that can handle the APK intent before returning.
     */
    fun build(
        context: Context,
        apkFile: File,
    ): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_TYPE_APK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            grantToInstallers(context, uri)
        }
    }

    private fun grantToInstallers(
        context: Context,
        uri: Uri,
    ) {
        val probe =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_TYPE_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        resolveInstallers(context, probe).forEach { info ->
            runCatching {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun resolveInstallers(
        context: Context,
        probe: Intent,
    ) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.queryIntentActivities(
            probe,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
    }
}
