package com.yunfie.illustia.platform

import com.yunfie.illustia.settings.AppSettings

/**
 * ViewModel 層が必要とするバックアップ入出力の境界。
 *
 * 従来は ViewModel が `android.net.Uri` を受け取って `ManagedDataRepository` に
 * そのまま渡していた。ViewModel は URI の中身を知らないため、文字列表現で受け取る。
 */
interface DocumentIo {
    /** 指定 URI へバックアップを書き出す。 */
    fun export(
        uri: String,
        settings: AppSettings,
    )

    /** 指定 URI からバックアップを読み込み、設定へ反映した結果を返す。 */
    fun import(
        uri: String,
        current: AppSettings,
    ): AppSettings

    /**
     * [url] を取得し、そのボディを [uri] へストリーミングで書き出す。
     *
     * @throws IllegalStateException レスポンスが成功しなかった場合。
     */
    fun downloadToUri(
        url: String,
        headers: Map<String, String>,
        uri: String,
    )
}
