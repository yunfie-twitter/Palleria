package com.yunfie.illustia.platform

import com.yunfie.illustia.data.AnimatedGifEncoder
import com.yunfie.illustia.data.UgoiraMp4Encoder
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import java.io.File

/**
 * [MediaEncoder] の Android 実装。
 *
 * 既存の [AnimatedGifEncoder] / [UgoiraMp4Encoder] へ委譲する薄いアダプタ。
 */
class AndroidMediaEncoder : MediaEncoder {
    override fun encodeGif(
        frames: List<UgoiraPlaybackFrame>,
        outputPath: String,
    ) {
        val output = File(outputPath)
        output.outputStream().buffered().use { stream ->
            AnimatedGifEncoder.encode(frames, stream)
        }
    }

    override fun encodeMp4(
        frames: List<UgoiraPlaybackFrame>,
        outputPath: String,
    ) {
        UgoiraMp4Encoder.encode(frames, File(outputPath))
    }
}
