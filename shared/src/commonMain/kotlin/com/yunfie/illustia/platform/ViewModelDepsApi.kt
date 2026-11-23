package com.yunfie.illustia.platform

import com.yunfie.illustia.data.IllustiaRepositoryApi
import com.yunfie.illustia.settings.SettingsFacade
import com.yunfie.illustia.updater.AppUpdateService
import kotlinx.coroutines.CoroutineScope

/**
 * ViewModel 連鎖が必要とする環境依存の入口。
 *
 * `androidApp` の `ViewModelDeps` がこれを実装する。ここに並ぶプロパティは
 * すべて `commonMain` のインターフェースであり、Android 型は一つも現れない。
 */
interface ViewModelDepsApi {
    /** ViewModel の生存期間に紐づくコルーチンスコープ。 */
    val scope: CoroutineScope

    val logger: AppLogger
    val clock: MonotonicClock
    val mainThread: MainThreadChecker
    val connectivity: Connectivity
    val appPaths: AppPaths
    val appInfo: AppInfo
    val backgroundWork: BackgroundWork
    val imageCache: ImageCache
    val deviceProfile: DeviceProfile
    val telemetry: Telemetry
    val strings: StringProvider

    val documentIo: DocumentIo
    val appServices: AppServices
    val appIconSwitcher: AppIconSwitcher
    val downloadService: DownloadService
    val mediaEncoder: MediaEncoder
    val imageStore: GallerySaver
    val appUpdateService: AppUpdateService
    val settings: SettingsFacade
    val repository: IllustiaRepositoryApi
    val telemetryControl: TelemetryControl
}
