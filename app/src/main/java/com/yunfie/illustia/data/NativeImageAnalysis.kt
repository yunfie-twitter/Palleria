package com.yunfie.illustia.data

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import com.yunfie.illustia.rust.analyzeRgba
import kotlin.math.roundToInt

internal object NativeImageAnalysis {
    private const val HEADER_SAMPLE_SIZE = 40
    private const val COLOR_SAMPLE_SIZE = 32

    fun shouldUseDarkHeaderIcons(bitmap: Bitmap): Boolean {
        val sample = sampledPixels(bitmap, HEADER_SAMPLE_SIZE)
        if (sample.pixels.isEmpty()) return false
        val insetX = (sample.width / 6).coerceAtLeast(1)
        val insetY = (sample.height / 6).coerceAtLeast(1)
        val rgba = pixelsToRgba(sample.pixels, sample.width, sample.height, insetX, insetY)
        val analysis = analyzeRgba(rgba)
        val effectiveAnalysis =
            if (analysis.sampleCount == 0u) {
                analyzeRgba(pixelsToRgba(sample.pixels, sample.width, sample.height))
            } else {
                analysis
            }
        return effectiveAnalysis.averageLuminance >= 0.58
    }

    fun dominantColor(bitmap: Bitmap): Int {
        val sample = sampledPixels(bitmap, COLOR_SAMPLE_SIZE)
        if (sample.pixels.isEmpty()) return Color.BLACK
        return analyzeRgba(pixelsToRgba(sample.pixels, sample.width, sample.height)).dominantArgb
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
