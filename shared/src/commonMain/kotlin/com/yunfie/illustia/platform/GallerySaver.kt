package com.yunfie.illustia.platform

import com.yunfie.illustia.models.Illust

/**
 * ViewModel 層が必要とするギャラリー保存の境界。
 *
 * 保存元は常にディスク上の一時ファイル（ダウンロード済みの `.part`、変換済み GIF/MP4）で
 * あるため、ストリームではなくパス文字列を受け取る。実装側でファイルを開いて
 * ストリーミングコピーするので、メモリに全バイトを載せることはない。
 */
interface GallerySaver {
    /**
     * [sourcePath] のファイルをギャラリーへ保存する。
     *
     * @return 保存した場合は true。同名の保存が進行中などで見送った場合は false。
     */
    fun saveFromFile(
        sourcePath: String,
        name: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean = false,
        illust: Illust? = null,
        embedMetadata: Boolean = false,
    ): Boolean

    /** 同名（拡張子解決後）の保存先が既に存在するか。 */
    fun exists(
        name: String,
        sourceUrl: String = "",
    ): Boolean
}
