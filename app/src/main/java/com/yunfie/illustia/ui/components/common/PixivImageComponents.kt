package com.yunfie.illustia.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.yunfie.illustia.data.proxyPixivImageUrl

val PixivImageHeaders = NetworkHeaders.Builder()
    .set("Referer", "https://www.pixiv.net/")
    .set("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
    .build()

private const val ThumbnailDecodeSizePx = 512
private const val PrefetchDecodeSizePx = 512

@Composable
fun PixivImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false,
    thumbnail: Boolean = false,
    onSuccess: ((Bitmap) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val effectiveUrl = remember(url, proxyBaseUrl) {
        proxyPixivImageUrl(url, proxyBaseUrl)
    }
    val imageRequest = remember(effectiveUrl, thumbnail) {
        ImageRequest.Builder(context)
            .data(effectiveUrl)
            .httpHeaders(PixivImageHeaders)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(!thumbnail && crossfade)
            .listener(
                onSuccess = { _, result ->
                    runCatching {
                        onSuccess?.invoke(result.image.toBitmap())
                    }
                },
            )
            .apply {
                if (thumbnail) {
                    size(ThumbnailDecodeSizePx)
                    scale(Scale.FILL)
                    precision(Precision.INEXACT)
                    allowRgb565(true)
                }
            }
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun PrefetchPixivImages(
    urls: List<String>,
    enabled: Boolean,
    limit: Int = 12,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val prefetchUrls = remember(urls, proxyBaseUrl, limit) {
        urls.asSequence()
            .filter { it.isNotBlank() }
            .map { proxyPixivImageUrl(it, proxyBaseUrl) }
            .distinct()
            .take(limit)
            .toList()
    }

    var previousUrls by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(enabled, prefetchUrls) {
        if (!enabled || prefetchUrls.isEmpty()) {
            previousUrls = emptySet()
            return@LaunchedEffect
        }

        val newUrls = prefetchUrls.toSet()
        val urlsToPrefetch = newUrls - previousUrls

        if (urlsToPrefetch.isNotEmpty()) {
            val imageLoader = SingletonImageLoader.get(context)
            urlsToPrefetch.forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(PixivImageHeaders)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .size(PrefetchDecodeSizePx)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .build()
                imageLoader.enqueue(request)
            }
            previousUrls = newUrls
        }
    }
}
