package com.yunfie.illustia.platform

import android.app.Application
import android.content.ContentResolver
import com.yunfie.illustia.IllustiaApplication
import kotlinx.coroutines.CoroutineScope

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
) {
    val illustiaApplication: IllustiaApplication? get() = application as? IllustiaApplication

    val contentResolver: ContentResolver get() = application.contentResolver
}
