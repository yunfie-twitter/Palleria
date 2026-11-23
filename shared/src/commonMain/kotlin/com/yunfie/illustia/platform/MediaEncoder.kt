package com.yunfie.illustia.platform

import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame

/**
 * ViewModel 層が必要とするうごイラ変換の境界。
 *
 * 出力先は `File` ではなくパス文字列で受け取り、`Bitmap` / `MediaCodec` といった
 * Android の型は実装側に閉じる。
 */
interface MediaEncoder {
    /**
     * 連続フレームをアニメーション GIF として [outputPath] へ書き出す。
     *
     * @throws IllegalArgumentException [frames] が空の場合。
     */
    fun encodeGif(
        frames: List<UgoiraPlaybackFrame>,
        outputPath: String,
    )

    /**
     * 連続フレームを MP4 として [outputPath] へ書き出す。
     *
     * @throws IllegalArgumentException [frames] が空の場合。
     */
    fun encodeMp4(
        frames: List<UgoiraPlaybackFrame>,
        outputPath: String,
    )
}
