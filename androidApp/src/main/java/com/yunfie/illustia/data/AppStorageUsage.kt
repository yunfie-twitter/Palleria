package com.yunfie.illustia.data

import android.app.usage.StorageStats
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.yunfie.illustia.platform.PlatformCapabilities
import java.io.File

data class AppStorageUsage(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val apkBytes: Long? = null,
    val optimizedCodeBytes: Long? = null,
    val dexMetadataBytes: Long? = null,
    val nativeLibraryBytes: Long? = null,
    val referenceProfileBytes: Long? = null,
    val currentProfileBytes: Long? = null,
) {
    val totalBytes: Long
        get() = appBytes + dataBytes

    val userDataBytes: Long
        get() = (dataBytes - cacheBytes).coerceAtLeast(0L)

    val hasCodeBreakdown: Boolean
        get() = apkBytes != null
}

/**
 * Reads storage figures for this app only. Call from a worker thread: the platform query can take
 * several seconds on some devices.
 */
fun Context.readAppStorageUsage(): AppStorageUsage =
    if (PlatformCapabilities.supportsStorageStats()) {
        runCatching { readPlatformStorageUsage() }.getOrElse { readStorageUsageManually() }
    } else {
        readStorageUsageManually()
    }

@RequiresApi(Build.VERSION_CODES.O)
private fun Context.readPlatformStorageUsage(): AppStorageUsage {
    val manager =
        getSystemService(StorageStatsManager::class.java)
            ?: return readStorageUsageManually()
    val stats =
        manager.queryStatsForPackage(
            applicationInfo.storageUuid,
            packageName,
            Process.myUserHandle(),
        )

    return if (PlatformCapabilities.supportsDetailedStorageStats()) {
        stats.toDetailedUsage()
    } else {
        AppStorageUsage(
            appBytes = stats.appBytes,
            dataBytes = stats.dataBytes,
            cacheBytes = stats.cacheBytes,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun StorageStats.toDetailedUsage(): AppStorageUsage =
    AppStorageUsage(
        appBytes = appBytes,
        dataBytes = dataBytes,
        cacheBytes = cacheBytes,
        apkBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_APK),
        optimizedCodeBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_DEXOPT_ARTIFACT),
        dexMetadataBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_DM),
        nativeLibraryBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_LIB),
        referenceProfileBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE),
        currentProfileBytes = getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE),
    )

@Suppress("DEPRECATION")
private fun Context.readStorageUsageManually(): AppStorageUsage {
    val appInfo = applicationInfo
    val appFiles =
        buildList {
            add(File(appInfo.sourceDir))
            appInfo.splitSourceDirs?.mapTo(this, ::File)
            appInfo.nativeLibraryDir?.let { add(File(it)) }
            obbDirs.filterNotNull().forEach(::add)
        }
    val cacheFiles =
        buildList {
            add(cacheDir)
            add(codeCacheDir)
            externalCacheDirs.filterNotNull().forEach(::add)
        }
    val dataFiles =
        buildList {
            add(dataDir)
            getExternalFilesDirs(null).filterNotNull().forEach(::add)
            externalCacheDirs.filterNotNull().forEach(::add)
            externalMediaDirs.filterNotNull().forEach(::add)
        }

    return AppStorageUsage(
        appBytes = appFiles.distinctBy(File::getAbsolutePath).sumOf(File::safeSize),
        dataBytes = dataFiles.distinctBy(File::getAbsolutePath).sumOf(File::safeSize),
        cacheBytes = cacheFiles.distinctBy(File::getAbsolutePath).sumOf(File::safeSize),
    )
}

private fun File.safeSize(): Long =
    runCatching {
        when {
            !exists() -> 0L
            isFile -> length()
            else -> walkTopDown().filter(File::isFile).sumOf(File::length)
        }
    }.getOrDefault(0L)
