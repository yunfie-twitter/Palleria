package com.yunfie.illustia.platform

import android.app.Application
import android.content.ContentResolver
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.data.IllustiaRepositoryApi
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.settings.SettingsFacade
import com.yunfie.illustia.updater.AppUpdateService
import com.yunfie.illustia.updater.AppUpdaterRepository
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

/**
 * ViewModel 連鎖が実際に必要とする環境依存の入口。
 *
 * これまで各モジュールは `Application` をそのまま受け取って次の親へ渡していただけで、
 * 実際に使っていたのは「Application インスタンス」「ContentResolver」の 2 点だった。
 * Android 依存（ログ・時刻・スレッド判定・コルーチンスコープ）もここに集約し、
 * ViewModel 本体から `android.util.Log` / `android.os.SystemClock` / `android.os.Looper`
 * を追い出す。
 *
 * [scope] は ViewModel の生存期間に紐づくコルーチンスコープ。`AndroidViewModel` を
 * 継承しないため、生成側（ファクトリ）が明示的に渡す。
 */
class ViewModelDeps(
    val application: Application,
    override val scope: CoroutineScope,
    override val logger: AppLogger = AndroidAppLogger(),
    override val clock: MonotonicClock = AndroidMonotonicClock(),
    override val mainThread: MainThreadChecker = AndroidMainThreadChecker(),
    override val connectivity: Connectivity = AndroidConnectivity(application),
    override val appPaths: AppPaths = AndroidAppPaths(application),
    override val appInfo: AppInfo = AndroidAppInfo(application),
    override val backgroundWork: BackgroundWork = AndroidBackgroundWork(application),
    override val imageCache: ImageCache = AndroidImageCache(application),
    override val deviceProfile: DeviceProfile = AndroidDeviceProfile(application),
    override val telemetry: Telemetry = AndroidTelemetry,
    override val strings: StringProvider = AndroidStringProvider(application),
) : ViewModelDepsApi {
    override val appUpdateService: AppUpdateService by lazy {
        AppUpdaterRepository(application, requireNotNull(illustiaApplication).sharedHttpClient)
    }
    override val imageStore: GallerySaver by lazy { NativeImageStore(application.applicationContext) }
    override val appIconSwitcher: AppIconSwitcher by lazy { AndroidAppIconSwitcher(application) }
    override val downloadService: DownloadService by lazy { AndroidDownloadService(sharedHttpClient) }
    override val mediaEncoder: MediaEncoder = AndroidMediaEncoder()
    override val appServices: AppServices by lazy { AndroidAppServices(application) }
    private val documentIoImpl: AndroidDocumentIo by lazy { AndroidDocumentIo(application.contentResolver, sharedHttpClient) }
    override val documentIo: DocumentIo get() = documentIoImpl
    val documentStreamIo: DocumentStreamIo get() = documentIoImpl
    val illustiaApplication: IllustiaApplication? get() = application as? IllustiaApplication

    /** `IllustiaApplication` の共有インスタンス。サービスロケータの downcast をここに集約する。 */
    override val settings: SettingsFacade get() = requireNotNull(illustiaApplication).settingsStore
    override val repository: IllustiaRepositoryApi get() = requireNotNull(illustiaApplication).repository
    val sharedHttpClient: OkHttpClient get() = requireNotNull(illustiaApplication).sharedHttpClient
    override val telemetryControl: TelemetryControl by lazy {
        AndroidTelemetryControl(requireNotNull(illustiaApplication))
    }

    val contentResolver: ContentResolver get() = application.contentResolver
}
