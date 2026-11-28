package com.yunfie.illustia.ui.screens.data

/**
 * データ設定画面が ViewModel に要求する操作。
 *
 * エクスポート/インポートは URI 文字列で受け取るため、画面側は
 * プラットフォームのファイル選択結果を文字列にして渡すだけでよい。
 */
interface DataSettingsActions {
    fun updateSaveViewHistory(value: Boolean)

    fun updateSaveSearchHistory(value: Boolean)

    fun updateSendTelemetry(value: Boolean)

    fun exportManagedData(uri: String)

    fun importManagedData(uri: String)

    fun clearAppCache()
}
