package com.yunfie.illustia.platform

import android.app.Application
import android.content.ContentResolver
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.settings.SettingsStore
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
    val scope: CoroutineScope,
    val logger: AppLogger = AndroidAppLogger(),
    val clock: MonotonicClock = AndroidMonotonicClock(),
    val mainThread: MainThreadChecker = AndroidMainThreadChecker(),
    val connectivity: Connectivity = AndroidConnectivity(application),
    val appPaths: AppPaths = AndroidAppPaths(application),
    val appInfo: AppInfo = AndroidAppInfo(application),
    val backgroundWork: BackgroundWork = AndroidBackgroundWork(application),
    val imageCache: ImageCache = AndroidImageCache(application),
    val deviceProfile: DeviceProfile = AndroidDeviceProfile(application),
    val telemetry: Telemetry = AndroidTelemetry,
    val strings: StringProvider = AndroidStringProvider(application),
) {
    val appUpdateService: AppUpdateService by lazy {
        AppUpdaterRepository(application, requireNotNull(illustiaApplication).sharedHttpClient)
    }
    val imageStore: GallerySaver by lazy { NativeImageStore(application.applicationContext) }
    val appIconSwitcher: AppIconSwitcher by lazy { AndroidAppIconSwitcher(application) }
    val appServices: AppServices by lazy { AndroidAppServices(application) }
    private val documentIoImpl: AndroidDocumentIo by lazy { AndroidDocumentIo(application.contentResolver) }
    val documentIo: DocumentIo get() = documentIoImpl
    val documentStreamIo: DocumentStreamIo get() = documentIoImpl
    val illustiaApplication: IllustiaApplication? get() = application as? IllustiaApplication

    /** `IllustiaApplication` の共有インスタンス。サービスロケータの downcast をここに集約する。 */
    val settings: SettingsStore get() = requireNotNull(illustiaApplication).settingsStore
    val repository: IllustiaRepository get() = requireNotNull(illustiaApplication).repository
    val sharedHttpClient: OkHttpClient get() = requireNotNull(illustiaApplication).sharedHttpClient

    val contentResolver: ContentResolver get() = application.contentResolver
}
