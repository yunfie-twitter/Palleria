@file:Suppress(
    "MagicNumber",
    "ComplexMethod",
    "CyclomaticComplexMethod",
    "LoopWithTooManyJumpStatements",
    "NestedBlockDepth",
    "LongMethod",
    "ReturnCount",
)

package com.yunfie.illustia.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import java.io.File
import kotlin.math.roundToInt

/**
 * Encodes Ugoira playback frames into an MP4 (H.264 / AVC) video using Android MediaCodec and MediaMuxer.
 */
object UgoiraMp4Encoder {
    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val MAX_DIMENSION = 1920
    private const val TIMEOUT_US = 10_000L
    private const val I_FRAME_INTERVAL_SEC = 1
    private const val MIN_VIDEO_DURATION_MS = 2_000L
    private const val MAX_DEQUEUE_ATTEMPTS = 50
    private const val MAX_EOS_DRAIN_ATTEMPTS = 50

    fun encode(
        frames: List<UgoiraPlaybackFrame>,
        outputFile: File,
        maxDimension: Int = MAX_DIMENSION,
    ) {
        require(frames.isNotEmpty()) { "Frames must not be empty" }

        val firstFrame = frames.first()
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(firstFrame.filePath, boundsOptions)
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        require(origWidth > 0 && origHeight > 0) { "Invalid frame dimensions" }

        val scale =
            if (origWidth > maxDimension || origHeight > maxDimension) {
                maxDimension.toFloat() / maxOf(origWidth, origHeight)
            } else {
                1.0f
            }

        var targetWidth = ((origWidth * scale).roundToInt() / 2) * 2
        var targetHeight = ((origHeight * scale).roundToInt() / 2) * 2
        targetWidth = targetWidth.coerceAtLeast(16)
        targetHeight = targetHeight.coerceAtLeast(16)

        val totalCycleMs = frames.sumOf { normalizedUgoiraDelayMillis(it.delayMillis) }
        val loopCount =
            if (totalCycleMs in 1 until MIN_VIDEO_DURATION_MS) {
                (MIN_VIDEO_DURATION_MS / totalCycleMs).toInt() + 1
            } else {
                1
            }

        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        val colorFormat = selectColorFormat(codec.codecInfo, MIME_TYPE)

        val bitRate = (targetWidth * targetHeight * 4).coerceIn(1_000_000, 12_000_000)
        val estimatedFps = (1000.0 / (totalCycleMs.toDouble() / frames.size)).roundToInt().coerceIn(1, 60)

        val format =
            MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, estimatedFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
            }

        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var videoTrackIndex = -1
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            var currentPtsUs = 0L
            val drain: () -> Unit = {
                drainEncoder(
                    codec = codec,
                    muxer = muxer,
                    bufferInfo = bufferInfo,
                    getMuxerStarted = { muxerStarted },
                    setMuxerStarted = { muxerStarted = it },
                    getVideoTrack = { videoTrackIndex },
                    setVideoTrack = { videoTrackIndex = it },
                    endOfStream = false,
                )
            }

            for (loop in 0 until loopCount) {
                for (frame in frames) {
                    val frameDurationUs = normalizedUgoiraDelayMillis(frame.delayMillis) * 1_000L
                    val bitmap = loadAndScaleBitmap(frame.filePath, targetWidth, targetHeight) ?: continue

                    val pixels = IntArray(targetWidth * targetHeight)
                    bitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                    bitmap.recycle()

                    feedFrameToEncoder(
                        codec = codec,
                        pixels = pixels,
                        width = targetWidth,
                        height = targetHeight,
                        colorFormat = colorFormat,
                        ptsUs = currentPtsUs,
                        onDrain = drain,
                    )
                    drain()

                    currentPtsUs += frameDurationUs
                }
            }

            signalEndOfStream(codec, currentPtsUs, drain)
            drainEncoder(
                codec = codec,
                muxer = muxer,
                bufferInfo = bufferInfo,
                getMuxerStarted = { muxerStarted },
                setMuxerStarted = { muxerStarted = it },
                getVideoTrack = { videoTrackIndex },
                setVideoTrack = { videoTrackIndex = it },
                endOfStream = true,
            )
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    private fun loadAndScaleBitmap(
        filePath: String,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
        return if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            if (scaled != bitmap) bitmap.recycle()
            scaled
        } else {
            bitmap
        }
    }

    private fun selectColorFormat(
        codecInfo: MediaCodecInfo,
        mimeType: String,
    ): Int {
        val capabilities = codecInfo.getCapabilitiesForType(mimeType)
        val supported = capabilities.colorFormats
        return when {
            supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) -> {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }

            supported.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) -> {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            }

            else -> {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }
        }
    }

    private fun feedFrameToEncoder(
        codec: MediaCodec,
        pixels: IntArray,
        width: Int,
        height: Int,
        colorFormat: Int,
        ptsUs: Long,
        onDrain: () -> Unit,
    ) {
        var inputIndex = -1
        var attempts = 0
        while (inputIndex < 0) {
            inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex < 0) {
                onDrain()
                attempts++
                if (attempts >= MAX_DEQUEUE_ATTEMPTS) {
                    error("Timeout waiting for encoder input buffer")
                }
            }
        }

        val inputImage = runCatching { codec.getInputImage(inputIndex) }.getOrNull()
        if (inputImage != null) {
            writeRgbToImage(pixels, width, height, inputImage)
            codec.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, ptsUs, 0)
        } else {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("Input buffer is null")
            inputBuffer.clear()
            val yuv = ByteArray(width * height * 3 / 2)
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                rgbToI420(pixels, width, height, yuv)
            } else {
                rgbToNv12(pixels, width, height, yuv)
            }
            inputBuffer.put(yuv)
            codec.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, 0)
        }
    }

    private fun signalEndOfStream(
        codec: MediaCodec,
        ptsUs: Long,
        onDrain: () -> Unit,
    ) {
        var inputIndex = -1
        var attempts = 0
        while (inputIndex < 0) {
            inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex < 0) {
                onDrain()
                attempts++
                if (attempts >= MAX_DEQUEUE_ATTEMPTS) {
                    error("Timeout waiting for encoder input buffer for EOS")
                }
            }
        }
        codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        getMuxerStarted: () -> Boolean,
        setMuxerStarted: (Boolean) -> Unit,
        getVideoTrack: () -> Int,
        setVideoTrack: (Int) -> Unit,
        endOfStream: Boolean,
    ) {
        var eosAttempts = 0
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                    eosAttempts++
                    if (eosAttempts >= MAX_EOS_DRAIN_ATTEMPTS) break
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (getMuxerStarted()) throw IllegalStateException("Output format changed twice")
                    val newFormat = codec.outputFormat
                    val trackIndex = muxer.addTrack(newFormat)
                    setVideoTrack(trackIndex)
                    muxer.start()
                    setMuxerStarted(true)
                }

                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null && getMuxerStarted()) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size != 0) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(getVideoTrack(), encodedData, bufferInfo)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        }
    }

    private fun writeRgbToImage(
        pixels: IntArray,
        width: Int,
        height: Int,
        image: Image,
    ) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                yBuffer.put(j * yRowStride + i * yPixelStride, y)

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    uBuffer.put((j / 2) * uRowStride + (i / 2) * uPixelStride, u)
                    vBuffer.put((j / 2) * vRowStride + (i / 2) * vPixelStride, v)
                }
            }
        }
    }

    private fun rgbToNv12(
        pixels: IntArray,
        width: Int,
        height: Int,
        out: ByteArray,
    ) {
        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                out[yIndex++] = y

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    out[uvIndex++] = u
                    out[uvIndex++] = v
                }
            }
        }
    }

    private fun rgbToI420(
        pixels: IntArray,
        width: Int,
        height: Int,
        out: ByteArray,
    ) {
        var yIndex = 0
        val uOffset = width * height
        val vOffset = uOffset + (width * height / 4)
        var uIndex = uOffset
        var vIndex = vOffset

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                out[yIndex++] = y

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    out[uIndex++] = u
                    out[vIndex++] = v
                }
            }
        }
    }
}
