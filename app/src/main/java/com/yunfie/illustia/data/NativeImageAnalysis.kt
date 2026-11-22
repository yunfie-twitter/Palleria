package com.yunfie.illustia.data

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import kotlin.math.roundToInt

internal object NativeImageAnalysis {
    private const val HEADER_SAMPLE_SIZE = 40
    private const val COLOR_SAMPLE_SIZE = 32
    private const val DARK_ICON_LUMINANCE_THRESHOLD = 0.58
    private const val INSET_DIVISOR = 6

    fun shouldUseDarkHeaderIcons(bitmap: Bitmap): Boolean {
        val sample = sampledPixels(bitmap, HEADER_SAMPLE_SIZE)
        if (sample.pixels.isEmpty()) return false
        val insetX = (sample.width / INSET_DIVISOR).coerceAtLeast(1)
        val insetY = (sample.height / INSET_DIVISOR).coerceAtLeast(1)
        val rgba = pixelsToRgba(sample.pixels, sample.width, sample.height, insetX, insetY)
        val analysis = ImageStatistics.analyzeRgba(rgba)
        val effectiveAnalysis =
            if (analysis.sampleCount == 0) {
                ImageStatistics.analyzeRgba(pixelsToRgba(sample.pixels, sample.width, sample.height))
            } else {
                analysis
            }
        return effectiveAnalysis.averageLuminance >= DARK_ICON_LUMINANCE_THRESHOLD
    }

    fun dominantColor(bitmap: Bitmap): Int {
        val sample = sampledPixels(bitmap, COLOR_SAMPLE_SIZE)
        if (sample.pixels.isEmpty()) return Color.BLACK
        return ImageStatistics.analyzeRgba(pixelsToRgba(sample.pixels, sample.width, sample.height)).dominantArgb
    }

    private fun sampledPixels(
        bitmap: Bitmap,
        maxDimension: Int,
    ): PixelSample {
        val softwareBitmap =
            (
                if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                    null
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                    runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
                } else {
                    bitmap
                }
            ) ?: return PixelSample(0, 0, IntArray(0))
        var sampled: Bitmap? = null
        return try {
            val scale =
                minOf(
                    1f,
                    maxDimension.toFloat() / maxOf(softwareBitmap.width, softwareBitmap.height).toFloat(),
                )
            val width = (softwareBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (softwareBitmap.height * scale).roundToInt().coerceAtLeast(1)
            val currentSampled =
                if (width == softwareBitmap.width && height == softwareBitmap.height) {
                    softwareBitmap
                } else {
                    Bitmap.createScaledBitmap(softwareBitmap, width, height, true)
                }
            sampled = currentSampled
            val pixels = IntArray(width * height)
            currentSampled.getPixels(pixels, 0, width, 0, 0, width, height)
            PixelSample(width, height, pixels)
        } catch (_: Throwable) {
            PixelSample(0, 0, IntArray(0))
        } finally {
            if (sampled != null && sampled !== softwareBitmap && sampled !== bitmap) {
                sampled.recycle()
            }
            if (softwareBitmap !== bitmap) {
                softwareBitmap.recycle()
            }
        }
    }

    private fun pixelsToRgba(
        pixels: IntArray,
        width: Int,
        height: Int,
        insetX: Int = 0,
        insetY: Int = 0,
    ): ByteArray {
        if (insetX == 0 && insetY == 0) {
            val rgba = ByteArray(pixels.size * 4)
            var offset = 0
            for (color in pixels) {
                rgba[offset++] = Color.red(color).toByte()
                rgba[offset++] = Color.green(color).toByte()
                rgba[offset++] = Color.blue(color).toByte()
                rgba[offset++] = Color.alpha(color).toByte()
            }
            return rgba
        }
        val rgba = ByteArray(pixels.size * 4)
        var offset = 0
        for (y in 0 until height) {
            if (y < insetY || y >= height - insetY) continue
            val rowStart = y * width
            for (x in insetX until width - insetX) {
                val color = pixels[rowStart + x]
                rgba[offset++] = Color.red(color).toByte()
                rgba[offset++] = Color.green(color).toByte()
                rgba[offset++] = Color.blue(color).toByte()
                rgba[offset++] = Color.alpha(color).toByte()
            }
        }
        return if (offset == rgba.size) rgba else rgba.copyOf(offset)
    }

    private data class PixelSample(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
    )
}

/**
 * RGBA 画素列から平均輝度と最頻色を求める。旧 Rust (`image_analysis.rs`) の移植。
 *
 * - 輝度は sRGB の相対輝度 (WCAG) を使う。
 * - 16x16x16 の色バケットで最頻色を選び、同数なら後ろのバケットを優先（Rust の
 *   `max_by_key` と同じ挙動）。
 */
internal object ImageStatistics {
    private const val TRANSPARENT_ALPHA = 16
    private const val BUCKETS = 16 * 16 * 16
    private const val BUCKET_SHIFT = 4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val SRGB_THRESHOLD = 0.03928
    private const val SRGB_DIVISOR = 12.92
    private const val SRGB_OFFSET = 0.055
    private const val SRGB_SCALE = 1.055
    private const val SRGB_EXPONENT = 2.4

    private val linearizeLut =
        DoubleArray(256) { index ->
            val normalized = index / 255.0
            if (normalized <= SRGB_THRESHOLD) {
                normalized / SRGB_DIVISOR
            } else {
                Math.pow((normalized + SRGB_OFFSET) / SRGB_SCALE, SRGB_EXPONENT)
            }
        }

    data class Result(
        val averageLuminance: Double,
        val dominantArgb: Int,
        val sampleCount: Int,
    )

    fun analyzeRgba(pixels: ByteArray): Result {
        val pixelCount = pixels.size / 4
        if (pixelCount == 0) return Result(0.0, argb(0, 0, 0), 0)
        var luminanceSum = 0.0
        var sampleCount = 0
        val bucketCounts = IntArray(BUCKETS)
        for (index in 0 until pixelCount) {
            val offset = index * 4
            val red = pixels[offset].toInt() and 0xFF
            val green = pixels[offset + 1].toInt() and 0xFF
            val blue = pixels[offset + 2].toInt() and 0xFF
            val alpha = pixels[offset + 3].toInt() and 0xFF
            if (alpha < TRANSPARENT_ALPHA) continue
            luminanceSum += relativeLuminance(red, green, blue)
            sampleCount += 1
            bucketCounts[bucketIndex(red, green, blue)] += 1
        }
        val winner =
            bucketCounts.indices
                .filter { bucketCounts[it] > 0 }
                .maxByOrNull { bucketCounts[it] }
        val dominantArgb = if (winner == null) argb(0, 0, 0) else dominantBucketColor(pixels, pixelCount, winner, bucketCounts)
        return Result(
            averageLuminance = if (sampleCount == 0) 0.0 else luminanceSum / sampleCount,
            dominantArgb = dominantArgb,
            sampleCount = sampleCount,
        )
    }

    private fun dominantBucketColor(
        pixels: ByteArray,
        pixelCount: Int,
        winner: Int,
        bucketCounts: IntArray,
    ): Int {
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        for (index in 0 until pixelCount) {
            val offset = index * 4
            val red = pixels[offset].toInt() and 0xFF
            val green = pixels[offset + 1].toInt() and 0xFF
            val blue = pixels[offset + 2].toInt() and 0xFF
            val alpha = pixels[offset + 3].toInt() and 0xFF
            if (alpha < TRANSPARENT_ALPHA || bucketIndex(red, green, blue) != winner) continue
            redSum += red
            greenSum += green
            blueSum += blue
        }
        val count = bucketCounts[winner].toLong()
        return argb((redSum / count).toInt(), (greenSum / count).toInt(), (blueSum / count).toInt())
    }

    private fun bucketIndex(
        red: Int,
        green: Int,
        blue: Int,
    ): Int =
        ((red shr BUCKET_SHIFT) shl (BUCKET_SHIFT * 2)) or
            ((green shr BUCKET_SHIFT) shl BUCKET_SHIFT) or
            (blue shr BUCKET_SHIFT)

    private fun relativeLuminance(
        red: Int,
        green: Int,
        blue: Int,
    ): Double = RED_WEIGHT * linearizeLut[red] + GREEN_WEIGHT * linearizeLut[green] + BLUE_WEIGHT * linearizeLut[blue]

    private fun argb(
        red: Int,
        green: Int,
        blue: Int,
    ): Int = Color.rgb(red, green, blue)
}
