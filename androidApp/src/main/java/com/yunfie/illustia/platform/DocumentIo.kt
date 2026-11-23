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

    /** 指定 URI の出力ストリームへ書き込む。 */
    fun write(
        uri: String,
        block: (java.io.OutputStream) -> Unit,
    )
}
