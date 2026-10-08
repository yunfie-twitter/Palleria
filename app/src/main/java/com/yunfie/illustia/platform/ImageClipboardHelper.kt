package com.yunfie.illustia.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val CLIPBOARD_CACHE_MAX_AGE_MS = 60 * 60 * 1000L
private const val PNG_COMPRESS_QUALITY = 100

object ImageClipboardHelper {
    suspend fun copyImageToClipboard(
        context: Context,
        imageUrl: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val imageLoader = SingletonImageLoader.get(context)
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.pixiv.net/").build())
                        .build()
                val result = imageLoader.execute(request)
                if (result !is SuccessResult) return@withContext false

                val bitmap = result.image.toBitmap()
                copyBitmapToClipboard(context, bitmap)
            }.getOrDefault(false)
        }

    fun copyBitmapToClipboard(
        context: Context,
        bitmap: Bitmap,
    ): Boolean =
        runCatching {
            val clipDir = File(context.cacheDir, "clipboard").apply { mkdirs() }
            val now = System.currentTimeMillis()
            clipDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > CLIPBOARD_CACHE_MAX_AGE_MS) {
                    file.delete()
                }
            }
            val file = File(clipDir, "clip_$now.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_COMPRESS_QUALITY, out)
            }
            PrivacyExifSanitizer.sanitizeFile(file)

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )

            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return false
            val clipData =
                ClipData(
                    ClipDescription("Image", arrayOf("image/png")),
                    ClipData.Item(uri),
                )
            clipboard.setPrimaryClip(clipData)
            true
        }.getOrDefault(false)
}
