package com.yunfie.illustia.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.yunfie.illustia.data.NativeImageAnalysis
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.nativebridge.NativeSavedImage
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import android.net.Uri
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PalleriaLiveWallpaperService : WallpaperService() {
    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.yunfie.illustia.wallpaper.LIVE_SETTINGS_CHANGED"
    }

    override fun onCreateEngine(): Engine = PalleriaEngine()

    private inner class PalleriaEngine : Engine() {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val handler = Handler(Looper.getMainLooper())
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var loadJob: Job? = null
        private var visible = false
        private var surfaceReady = false
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var current: Bitmap? = null
        private var previous: Bitmap? = null
        private var currentPath: String? = null
        private var currentSettings: AppSettings? = null
        private var lastTapAt = 0L
        private var lastOffset = Float.NaN
        private val settingsChangedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (visible) loadNext(forceDifferent = false)
            }
        }

        private val intervalRunnable = object : Runnable {
            override fun run() {
                if (!visible) return
                loadNext(forceDifferent = true)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            ContextCompat.registerReceiver(
                applicationContext,
                settingsChangedReceiver,
                android.content.IntentFilter(ACTION_SETTINGS_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            surfaceReady = true
            current?.let { drawFrame(it, 1f, currentSettings) } ?: loadNext(forceDifferent = false)
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler.removeCallbacks(intervalRunnable)
            if (isVisible) {
                if (surfaceReady) {
                    loadNext(forceDifferent = current != null)
                }
            } else {
                loadJob?.cancel()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            handler.removeCallbacks(intervalRunnable)
            loadJob?.cancel()
            super.onSurfaceDestroyed(holder)
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            if (!visible) return
            scope.launch {
                val settings = withContext(Dispatchers.IO) { SettingsStore(applicationContext).read() }
                if (
                    settings.liveWallpaperChangeMode == "home" &&
                    !lastOffset.isNaN() &&
                    kotlin.math.abs(xOffset - lastOffset) >= max(xOffsetStep, 0.1f)
                ) {
                    loadNext(forceDifferent = true)
                }
                lastOffset = xOffset
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (event.action != MotionEvent.ACTION_UP) return
            val now = System.currentTimeMillis()
            if (now - lastTapAt <= 350L) {
                scope.launch {
                    val settings = withContext(Dispatchers.IO) { SettingsStore(applicationContext).read() }
                    if (settings.liveWallpaperChangeMode == "double_tap") {
                        loadNext(forceDifferent = true)
                    }
                }
                lastTapAt = 0L
            } else {
                lastTapAt = now
            }
        }

        override fun onDestroy() {
            runCatching { applicationContext.unregisterReceiver(settingsChangedReceiver) }
            handler.removeCallbacksAndMessages(null)
            loadJob?.cancel()
            current?.recycle()
            previous?.recycle()
            current = null
            previous = null
            scope.cancel()
            super.onDestroy()
        }

        private fun loadNext(forceDifferent: Boolean) {
            if (!visible || !surfaceReady) return
            loadJob?.cancel()
            loadJob = scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val store = SettingsStore(applicationContext)
                    val settings = store.read()
                    if (settings.privacyModeEnabled) {
                        return@withContext WallpaperLoadResult(settings, null, null)
                    }
                    val imageStore = NativeImageStore(applicationContext)
                    val selectedFolder = settings.liveWallpaperSourceFolder
                        .takeIf {
                            settings.liveWallpaperSource == "selected_folder" ||
                                settings.liveWallpaperSource == "folder"
                        }
                    val candidates = imageStore.listSavedImages(selectedFolder)
                    val selected = selectCandidate(candidates, settings, currentPath, forceDifferent)
                    val bitmap = selected?.uri
                        ?.let { decodeSampledBitmap(applicationContext, it, surfaceWidth, surfaceHeight) }
                    WallpaperLoadResult(settings, selected?.uri, bitmap)
                }

                if (!visible) {
                    result.bitmap?.recycle()
                    return@launch
                }
                currentSettings = result.settings
                if (result.bitmap == null) {
                    currentPath = null
                    current?.recycle()
                    current = null
                    drawFallback(result.settings)
                } else {
                    currentPath = result.path
                    showBitmap(result.bitmap, result.settings)
                }
                scheduleNext(result.settings)
            }
        }

        private fun showBitmap(bitmap: Bitmap, settings: AppSettings) {
            previous?.recycle()
            previous = current
            current = bitmap
            if (!settings.liveWallpaperCrossfade || previous == null) {
                previous?.recycle()
                previous = null
                drawFrame(bitmap, 1f, settings)
                return
            }
            val startedAt = System.currentTimeMillis()
            val duration = 500L
            val animate = object : Runnable {
                override fun run() {
                    if (!visible || current !== bitmap) return
                    val progress = ((System.currentTimeMillis() - startedAt).toFloat() / duration).coerceIn(0f, 1f)
                    drawCrossfade(previous, bitmap, progress, settings)
                    if (progress < 1f) {
                        handler.postDelayed(this, 16L)
                    } else {
                        previous?.recycle()
                        previous = null
                    }
                }
            }
            handler.post(animate)
        }

        private fun scheduleNext(settings: AppSettings) {
            handler.removeCallbacks(intervalRunnable)
            if (visible && settings.liveWallpaperChangeMode == "interval") {
                handler.postDelayed(
                    intervalRunnable,
                    settings.liveWallpaperIntervalMinutes.coerceIn(15, 1440) * 60_000L,
                )
            }
        }

        private fun drawCrossfade(old: Bitmap?, next: Bitmap, progress: Float, settings: AppSettings) {
            withCanvas { canvas ->
                drawBackground(canvas, next, settings)
                old?.let {
                    paint.alpha = ((1f - progress) * 255).toInt()
                    drawScaled(canvas, it, settings.liveWallpaperScaleMode)
                }
                paint.alpha = (progress * 255).toInt()
                drawScaled(canvas, next, settings.liveWallpaperScaleMode)
                paint.alpha = 255
            }
        }

        private fun drawFrame(bitmap: Bitmap, alpha: Float, settings: AppSettings? = null) {
            val resolved = settings ?: return
            withCanvas { canvas ->
                drawBackground(canvas, bitmap, resolved)
                paint.alpha = (alpha * 255).toInt()
                drawScaled(canvas, bitmap, resolved.liveWallpaperScaleMode)
                paint.alpha = 255
            }
        }

        private fun drawFallback(settings: AppSettings) {
            withCanvas { canvas ->
                canvas.drawColor(if (settings.liveWallpaperBackground == "white") Color.WHITE else Color.rgb(15, 18, 24))
                paint.color = if (settings.liveWallpaperBackground == "white") Color.rgb(35, 38, 44) else Color.WHITE
                paint.alpha = 215
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = min(surfaceWidth, surfaceHeight) * 0.08f
                canvas.drawText("Palleria", surfaceWidth / 2f, surfaceHeight / 2f, paint)
                paint.alpha = 255
            }
        }

        private fun drawBackground(canvas: Canvas, bitmap: Bitmap, settings: AppSettings) {
            when (settings.liveWallpaperBackground) {
                "white" -> canvas.drawColor(Color.WHITE)
                "dominant" -> canvas.drawColor(NativeImageAnalysis.dominantColor(bitmap))
                "blur" -> {
                    canvas.drawColor(Color.BLACK)
                    val blurredWidth = 24
                    val blurredHeight = (blurredWidth * bitmap.height.toFloat() / bitmap.width)
                        .toInt()
                        .coerceAtLeast(1)
                    val blurred = Bitmap.createScaledBitmap(bitmap, blurredWidth, blurredHeight, true)
                    paint.alpha = 190
                    drawScaled(canvas, blurred, "cover")
                    paint.alpha = 255
                    if (blurred !== bitmap) blurred.recycle()
                }
                else -> canvas.drawColor(Color.BLACK)
            }
        }

        private fun drawScaled(canvas: Canvas, bitmap: Bitmap, mode: String) {
            val sourceWidth = bitmap.width.toFloat()
            val sourceHeight = bitmap.height.toFloat()
            val scale = when (mode) {
                "contain" -> min(surfaceWidth / sourceWidth, surfaceHeight / sourceHeight)
                "fit_width" -> surfaceWidth / sourceWidth
                "fit_height" -> surfaceHeight / sourceHeight
                else -> max(surfaceWidth / sourceWidth, surfaceHeight / sourceHeight)
            }
            val width = sourceWidth * scale
            val height = sourceHeight * scale
            val destination = RectF(
                (surfaceWidth - width) / 2f,
                (surfaceHeight - height) / 2f,
                (surfaceWidth + width) / 2f,
                (surfaceHeight + height) / 2f,
            )
            canvas.drawBitmap(bitmap, null, destination, paint)
        }

        private inline fun withCanvas(block: (Canvas) -> Unit) {
            if (!surfaceHolder.surface.isValid) return
            val canvas = runCatching { surfaceHolder.lockCanvas() }.getOrNull() ?: return
            try {
                block(canvas)
            } finally {
                runCatching { surfaceHolder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}

internal data class WallpaperLoadResult(
    val settings: AppSettings,
    val path: String?,
    val bitmap: Bitmap?,
)

internal fun selectCandidate(
    candidates: List<NativeSavedImage>,
    settings: AppSettings,
    currentPath: String?,
    forceDifferent: Boolean,
): NativeSavedImage? {
    if (candidates.isEmpty()) return null
    val ordered = when (settings.liveWallpaperOrder) {
        "newest" -> candidates.sortedByDescending { it.modifiedAtMillis }
        "oldest" -> candidates.sortedBy { it.modifiedAtMillis }
        else -> candidates.shuffled()
    }
    if (!forceDifferent || ordered.size == 1) return ordered.first()
    val currentIndex = ordered.indexOfFirst { it.uri == currentPath }
    return when {
        settings.liveWallpaperOrder == "random" -> ordered.firstOrNull { it.uri != currentPath }
        currentIndex < 0 -> ordered.first()
        else -> ordered[(currentIndex + 1) % ordered.size]
    }
}

private fun decodeSampledBitmap(context: Context, uriValue: String, width: Int, height: Int): Bitmap? {
    val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
    }.getOrNull()
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) {
        sample *= 2
    }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }.getOrNull()
}
