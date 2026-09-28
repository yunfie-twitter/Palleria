package com.yunfie.illustia.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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

val PixivImageHeaders =
    NetworkHeaders
        .Builder()
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
    maxDecodeDimensionPx: Int? = null,
    allowRgb565: Boolean = false,
    showLoadingSpinner: Boolean = false,
    onSuccess: ((Bitmap) -> Unit)? = null,
    onLoadingStateChanged: ((Boolean) -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val effectiveUrl =
        remember(url, proxyBaseUrl) {
            proxyPixivImageUrl(url, proxyBaseUrl)
        }
    var isLoading by remember(effectiveUrl) { mutableStateOf(true) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnLoadingStateChanged by rememberUpdatedState(onLoadingStateChanged)
    val hasSuccessListener = onSuccess != null
    val hasLoadingListener = onLoadingStateChanged != null || showLoadingSpinner
    val imageRequest =
        remember(effectiveUrl, thumbnail, maxDecodeDimensionPx, allowRgb565, hasSuccessListener, hasLoadingListener) {
            ImageRequest
                .Builder(context)
                .data(effectiveUrl)
                .httpHeaders(PixivImageHeaders)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .crossfade(!thumbnail && crossfade)
                .listener(
                    onStart = {
                        isLoading = true
                        currentOnLoadingStateChanged?.invoke(true)
                    },
                    onSuccess = { _, result ->
                        isLoading = false
                        currentOnLoadingStateChanged?.invoke(false)
                        runCatching {
                            currentOnSuccess?.invoke(result.image.toBitmap())
                        }
                    },
                    onError = { _, _ ->
                        isLoading = false
                        currentOnLoadingStateChanged?.invoke(false)
                    },
                ).apply {
                    if (thumbnail) {
                        size(ThumbnailDecodeSizePx)
                        scale(Scale.FILL)
                        precision(Precision.INEXACT)
                        allowRgb565(true)
                    } else {
                        if (maxDecodeDimensionPx != null) {
                            size(maxDecodeDimensionPx)
                            scale(Scale.FIT)
                            precision(Precision.INEXACT)
                        }
                        if (allowRgb565) {
                            allowRgb565(true)
                        }
                    }
                }.build()
        }
    if (showLoadingSpinner) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
            if (isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

@Composable
fun PrefetchPixivImages(
    urls: List<String>,
    enabled: Boolean,
    limit: Int = 12,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val prefetchUrls =
        remember(urls, proxyBaseUrl, limit) {
            urls
                .asSequence()
                .filter { it.isNotBlank() }
                .map { proxyPixivImageUrl(it, proxyBaseUrl) }
                .distinct()
                .take(limit)
                .toList()
        }

    val activeRequests = remember { mutableMapOf<String, () -> Unit>() }

    LaunchedEffect(enabled, prefetchUrls) {
        if (!enabled || prefetchUrls.isEmpty()) {
            activeRequests.values.forEach { cancel -> cancel() }
            activeRequests.clear()
            return@LaunchedEffect
        }

        val iterator = activeRequests.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in prefetchUrls) {
                entry.value.invoke()
                iterator.remove()
            }
        }

        val imageLoader = SingletonImageLoader.get(context)
        for (i in prefetchUrls.indices) {
            val url = prefetchUrls[i]
            if (!activeRequests.containsKey(url)) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .httpHeaders(PixivImageHeaders)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .size(PrefetchDecodeSizePx)
                        .scale(Scale.FILL)
                        .precision(Precision.INEXACT)
                        .allowRgb565(true)
                        .build()
                val disposable = imageLoader.enqueue(request)
                activeRequests[url] = disposable::dispose
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRequests.values.forEach { cancel -> cancel() }
            activeRequests.clear()
        }
    }
}
