package com.yunfie.illustia.platform

import android.app.Application
import com.yunfie.illustia.IllustiaApplication

/**
 * ViewModel 連鎖が実際に必要とする環境依存の入口。
 *
 * これまで各モジュールは `Application` をそのまま受け取って次の親へ渡していただけで、
 * 実際に使っていたのは「Application インスタンス」「ContentResolver」の 2 点だった。
 * Android 依存（ログ・時刻・スレッド判定）もここに集約し、ViewModel 本体から
 * `android.util.Log` / `android.os.SystemClock` / `android.os.Looper` を追い出す。
 *
 * コルーチンスコープは構築順の都合（AndroidViewModel の生成後にしか得られない）で
 * ここには置かず、[com.yunfie.illustia.IllustiaViewModelFoundation] 側で
 * `viewModelScope` を同名プロパティとして公開する。
 */
class ViewModelDeps(
    val application: Application,
    val logger: AppLogger = AndroidAppLogger(),
    val clock: MonotonicClock = AndroidMonotonicClock(),
    val mainThread: MainThreadChecker = AndroidMainThreadChecker(),
) {
    val illustiaApplication: IllustiaApplication? get() = application as? IllustiaApplication

    // contentResolver は application から取得できるが、明示的に公開しておくと
    // 呼び出し側が Application の詳細を知らずに済む。
    val contentResolver get() = application.contentResolver
}
