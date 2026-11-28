package com.yunfie.illustia.platform

import com.yunfie.illustia.updater.AppReleaseInfo
import com.yunfie.illustia.updater.AppUpdateService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSLog
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURLCache
import kotlin.time.TimeSource

/**
 * iOS 側のプラットフォーム境界の実装。
 *
 * Android では `Context` / `ConnectivityManager` / `WorkManager` などに分散している処理を、
 * iOS の対応 API（`NSBundle` / `NSFileManager` / `NSProcessInfo` / `NSUserDefaults`）へ
 * 最小限に写像する。iOS に存在しない概念（アプリ更新・アイコン切替・壁紙・フォアグラウンド
 * サービス）は何もしない実装にする。
 */

object IosAppLogger : AppLogger {
    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        NSLog("%@: %@ %@", tag, message, throwable?.toString() ?: "")
    }
}

object IosMonotonicClock : MonotonicClock {
    private val mark = TimeSource.Monotonic.markNow()

    override fun elapsedRealtimeMillis(): Long = mark.elapsedNow().inWholeMilliseconds
}

object IosMainThreadChecker : MainThreadChecker {
    override fun isMainThread(): Boolean = NSThread.isMainThread
}

object IosTelemetry : Telemetry {
    override fun recordException(
        throwable: Throwable,
        tag: String?,
        extras: Map<String, Any?>?,
    ) = IosAppLogger.warn(tag ?: "Telemetry", "exception", throwable)

    override fun <T> trace(
        name: String,
        operation: String,
        block: () -> T,
    ): T = block()

    override suspend fun <T> traceAsync(
        name: String,
        operation: String,
        block: suspend () -> T,
    ): T = block()
}

object IosTelemetryControl : TelemetryControl {
    override fun setEnabled(enabled: Boolean) = Unit
}

class IosAppPaths : AppPaths {
    override fun cacheDir(): String {
        val caches =
            NSSearchPathForDirectoriesInDomains(NSCachesDirectory, 1u, true).firstOrNull() as? String
                ?: NSTemporaryDirectory()
        return caches
    }
}

class IosAppInfo(
    private val bundle: NSBundle = NSBundle.mainBundle,
) : AppInfo {
    override fun packageName(): String = bundle.bundleIdentifier ?: "com.yunfie.illustia"

    override fun versionCode(): Int? =
        (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.substringBefore('.')
            ?.toIntOrNull()

    override fun versionName(): String? = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
}

class IosConnectivity : Connectivity {
    override fun isWifiConnected(): Boolean = false

    override fun isOffline(): Boolean = false

    override fun isMetered(): Boolean = false
}

class IosDeviceProfile : DeviceProfile {
    override val isLowRamDevice: Boolean get() = NSProcessInfo.processInfo.physicalMemory < 3u * 1024u * 1024u * 1024u

    override val dataStoreDebounceMs: Long get() = if (isLowRamDevice) 800L else 300L

    override val supportsDynamicColor: Boolean get() = false
}

class IosImageCache : ImageCache {
    override fun prefetch(url: String) = Unit

    override fun clear() {
        NSURLCache.sharedURLCache().removeAllCachedResponses()
    }
}

/** iOS にはバックグラウンド更新ジョブが無いため、進捗は常に空。 */
class IosBackgroundWork : BackgroundWork {
    private val progress = MutableStateFlow<AppUpdateEvent?>(null)

    override fun appUpdateProgress(): Flow<AppUpdateEvent?> = progress

    override fun startAppUpdateDownload(release: AppReleaseInfo) = Unit

    override fun cancelAppUpdateWork() = Unit

    override fun schedulePeriodicUpdateCheck() = Unit

    override fun cancelPeriodicUpdateCheck() = Unit
}

object IosAppIconSwitcher : AppIconSwitcher {
    override fun apply(
        privacyModeEnabled: Boolean,
        appIconVariant: String,
    ) = Unit
}

object IosAppServices : AppServices {
    override fun setWallpaperPlaylistEnabled(enabled: Boolean) = Unit

    override fun refreshPrivacySensitiveWidgets() = Unit

    override suspend fun refreshRankingWidget() = Unit

    override fun notifyLiveWallpaperSettingsChanged() = Unit

    override fun reconcileAccounts(accounts: List<com.yunfie.illustia.models.StoredAccount>) = Unit

    override fun enqueueBookmarkToggle(
        illustId: Long,
        bookmarked: Boolean,
        restrict: com.yunfie.illustia.models.Restrict,
        followRestrict: com.yunfie.illustia.models.Restrict,
        bookmarkUserId: Long,
        artistId: Long,
        tags: List<String>,
        bookmarkTags: List<String>,
    ): Boolean = false

    override suspend fun recordFollowDeltaSnapshot(
        token: String,
        freshItems: List<com.yunfie.illustia.models.Illust>,
        proxyBaseUrl: String,
    ) = Unit

    override suspend fun readStorageUsage(): com.yunfie.illustia.data.AppStorageUsage =
        com.yunfie.illustia.data
            .AppStorageUsage(appBytes = 0L, dataBytes = 0L, cacheBytes = 0L)

    override fun acquireArtworkDownloadService(): AutoCloseable = AutoCloseable { }
}

/** iOS では設定画面・ログイン中はアプリが前面に居るので、何もしないでよい。 */
class IosDownloadService : DownloadService {
    override fun downloadToPartFile(
        url: String,
        partPath: String,
        resumeEnabled: Boolean,
        headers: Map<String, String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult = throw UnsupportedOperationException("iOS download service is not implemented yet")
}

object IosMediaEncoder : MediaEncoder {
    override fun encodeGif(
        frames: List<com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame>,
        outputPath: String,
    ) = throw UnsupportedOperationException("iOS GIF encoding is not implemented yet")

    override fun encodeMp4(
        frames: List<com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame>,
        outputPath: String,
    ) = throw UnsupportedOperationException("iOS MP4 encoding is not implemented yet")
}

class IosDocumentIo : DocumentIo {
    override fun export(
        uri: String,
        settings: com.yunfie.illustia.settings.AppSettings,
    ) = throw UnsupportedOperationException("iOS backup export is not implemented yet")

    override fun import(
        uri: String,
        current: com.yunfie.illustia.settings.AppSettings,
    ): com.yunfie.illustia.settings.AppSettings = throw UnsupportedOperationException("iOS backup import is not implemented yet")

    override fun downloadToUri(
        url: String,
        headers: Map<String, String>,
        uri: String,
    ) = throw UnsupportedOperationException("iOS document download is not implemented yet")
}

class IosGallerySaver : GallerySaver {
    override fun saveFromFile(
        sourcePath: String,
        name: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean,
        illust: com.yunfie.illustia.models.Illust?,
        embedMetadata: Boolean,
    ): Boolean = false

    override fun exists(
        name: String,
        sourceUrl: String,
    ): Boolean = false
}

class IosAppUpdateService : AppUpdateService {
    override fun getCurrentVersionName(): String = IosAppInfo().versionName() ?: "0.0.0"

    override fun cleanUpdateApks() = Unit

    override fun cancelDownload() = Unit

    override fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String,
    ): Boolean {
        val remote = remoteVersion.split('.').mapNotNull { it.toIntOrNull() }
        val current = currentVersion.split('.').mapNotNull { it.toIntOrNull() }
        for (index in 0 until maxOf(remote.size, current.size)) {
            val left = remote.getOrElse(index) { 0 }
            val right = current.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    override fun requestShizukuPermission(requestCode: Int) = Unit

    override fun isShizukuAvailable(): Boolean = false

    override fun isShizukuPermissionGranted(): Boolean = false

    override suspend fun fetchLatestRelease(includePrerelease: Boolean): Result<AppReleaseInfo?> = Result.success(null)

    override suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<String> = Result.failure(UnsupportedOperationException("iOS APK install is not applicable"))

    override suspend fun installApk(
        apkPath: String,
        method: com.yunfie.illustia.updater.UpdateInstallMethod,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("iOS APK install is not applicable"))
}
