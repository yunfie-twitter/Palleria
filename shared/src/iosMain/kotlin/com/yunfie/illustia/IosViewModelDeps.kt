package com.yunfie.illustia

import com.yunfie.illustia.data.IllustiaRepositoryApi
import com.yunfie.illustia.data.IosIllustiaRepository
import com.yunfie.illustia.data.IosUgoiraArchive
import com.yunfie.illustia.platform.AppIconSwitcher
import com.yunfie.illustia.platform.AppInfo
import com.yunfie.illustia.platform.AppLogger
import com.yunfie.illustia.platform.AppPaths
import com.yunfie.illustia.platform.AppServices
import com.yunfie.illustia.platform.BackgroundWork
import com.yunfie.illustia.platform.Connectivity
import com.yunfie.illustia.platform.DeviceProfile
import com.yunfie.illustia.platform.DocumentIo
import com.yunfie.illustia.platform.DownloadService
import com.yunfie.illustia.platform.FileSystem
import com.yunfie.illustia.platform.GallerySaver
import com.yunfie.illustia.platform.ImageCache
import com.yunfie.illustia.platform.IosAppIconSwitcher
import com.yunfie.illustia.platform.IosAppInfo
import com.yunfie.illustia.platform.IosAppLogger
import com.yunfie.illustia.platform.IosAppPaths
import com.yunfie.illustia.platform.IosAppServices
import com.yunfie.illustia.platform.IosAppUpdateService
import com.yunfie.illustia.platform.IosBackgroundWork
import com.yunfie.illustia.platform.IosConnectivity
import com.yunfie.illustia.platform.IosDeviceProfile
import com.yunfie.illustia.platform.IosDocumentIo
import com.yunfie.illustia.platform.IosDownloadService
import com.yunfie.illustia.platform.IosFileSystem
import com.yunfie.illustia.platform.IosGallerySaver
import com.yunfie.illustia.platform.IosImageCache
import com.yunfie.illustia.platform.IosMainThreadChecker
import com.yunfie.illustia.platform.IosMediaEncoder
import com.yunfie.illustia.platform.IosMonotonicClock
import com.yunfie.illustia.platform.IosTelemetry
import com.yunfie.illustia.platform.IosTelemetryControl
import com.yunfie.illustia.platform.MainThreadChecker
import com.yunfie.illustia.platform.MediaEncoder
import com.yunfie.illustia.platform.MonotonicClock
import com.yunfie.illustia.platform.StringProvider
import com.yunfie.illustia.platform.Telemetry
import com.yunfie.illustia.platform.TelemetryControl
import com.yunfie.illustia.platform.ViewModelDepsApi
import com.yunfie.illustia.settings.IosSettingsStore
import com.yunfie.illustia.settings.SettingsFacade
import com.yunfie.illustia.updater.AppUpdateService
import kotlinx.coroutines.CoroutineScope

/**
 * iOS 側の [ViewModelDepsApi] 実装。
 *
 * Android の `ViewModelDeps` と同じ契約を、iOS の対応 API で満たす。Repository と Settings は
 * 実際の実装（[IosIllustiaRepository] / [IosSettingsStore]）を返すため、iOS でも本編の
 * 画面が実データで動く。
 */
class IosViewModelDeps(
    applicationScope: CoroutineScope,
    override val strings: StringProvider,
) : ViewModelDepsApi {
    override val scope: CoroutineScope = applicationScope

    private val appFileSystem: FileSystem = IosFileSystem()
    private val settingsStore = IosSettingsStore(appFileSystem, IosAppPaths().cacheDir())

    override val logger: AppLogger = IosAppLogger
    override val clock: MonotonicClock = IosMonotonicClock
    override val mainThread: MainThreadChecker = IosMainThreadChecker
    override val connectivity: Connectivity = IosConnectivity()
    override val appPaths: AppPaths = IosAppPaths()
    override val appInfo: AppInfo = IosAppInfo()
    override val backgroundWork: BackgroundWork = IosBackgroundWork()
    override val imageCache: ImageCache = IosImageCache()
    override val deviceProfile: DeviceProfile = IosDeviceProfile()
    override val telemetry: Telemetry = IosTelemetry
    override val telemetryControl: TelemetryControl = IosTelemetryControl

    override val documentIo: DocumentIo = IosDocumentIo()
    override val fileSystem: FileSystem get() = appFileSystem
    override val appServices: AppServices = IosAppServices
    override val appIconSwitcher: AppIconSwitcher = IosAppIconSwitcher
    override val downloadService: DownloadService = IosDownloadService()
    override val mediaEncoder: MediaEncoder = IosMediaEncoder
    override val imageStore: GallerySaver = IosGallerySaver()
    override val appUpdateService: AppUpdateService = IosAppUpdateService()

    override val settings: SettingsFacade = settingsStore

    override val repository: IllustiaRepositoryApi =
        IosIllustiaRepository(
            persistence = settingsStore,
            fileSystem = appFileSystem,
            ugoiraArchive = IosUgoiraArchive(appFileSystem),
        )
}
