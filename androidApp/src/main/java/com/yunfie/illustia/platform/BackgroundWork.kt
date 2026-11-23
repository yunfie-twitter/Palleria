package com.yunfie.illustia.platform

import androidx.work.WorkInfo
import kotlinx.coroutines.flow.Flow

/**
 * ViewModel 層が必要とするバックグラウンド実行の境界。
 *
 * 今のところ唯一の利用者はアプリ更新ダウンロードなので、WorkManager の
 * 汎用 API ではなく、その 1 ジョブに絞った最小の窓口にしている。
 */
interface BackgroundWork {
    /** アプリ更新ダウンロードの進行状況。 */
    fun appUpdateWorkInfos(): Flow<List<WorkInfo>>

    /** アプリ更新ダウンロードをキャンセルする。 */
    fun cancelAppUpdateWork()
}
