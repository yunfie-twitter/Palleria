package com.yunfie.illustia.platform

import com.yunfie.illustia.updater.AppReleaseInfo
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel 層が必要とするバックグラウンド実行の境界。
 *
 * 汎用の WorkManager API ではなく、アプリ更新という 1 ジョブに絞った最小の窓口。
 * `WorkInfo` / `Data` はここから外へ出さず、ドメインイベントへ変換して渡す。
 */
interface BackgroundWork {
    /** アプリ更新ダウンロードの進行状況。 */
    fun appUpdateProgress(): Flow<AppUpdateEvent?>

    /** アプリ更新のダウンロードを開始する。 */
    fun startAppUpdateDownload(release: AppReleaseInfo)

    /** アプリ更新ダウンロードをキャンセルする。 */
    fun cancelAppUpdateWork()

    /** 定期アップデートチェックをスケジュールする。 */
    fun schedulePeriodicUpdateCheck()

    /** 定期アップデートチェックを取り消す。 */
    fun cancelPeriodicUpdateCheck()
}

/** アプリ更新ダウンロードの進行状況を表すドメインイベント。 */
sealed interface AppUpdateEvent {
    data class Progress(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : AppUpdateEvent

    data class Completed(
        val apkPath: String,
        val release: AppReleaseInfo,
    ) : AppUpdateEvent

    data class Failed(
        val error: String,
        val release: AppReleaseInfo,
    ) : AppUpdateEvent
}
