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
import com.yunfie.illustia.platform.PlatformCapabilities

val PixivImageHeaders =
    NetworkHeaders
        .Builder()
        .set("Referer", "https://www.pixiv.net/")
        .set("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
        .build()

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
    val hasSuccessListener = onSuccess != null
    val hasLoadingListener = onLoadingStateChanged != null || showLoadingSpinner
    var isLoading by remember(effectiveUrl, hasLoadingListener) { mutableStateOf(hasLoadingListener) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val currentOnLoadingStateChanged by rememberUpdatedState(onLoadingStateChanged)
    val defaultMaxDimension =
        remember(context) {
            val displayMetrics = context.resources.displayMetrics
            val screenMaxDim = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
            val maxCap = PlatformCapabilities.maxImageDecodeDimension(context)
            (screenMaxDim * 1.25f).toInt().coerceIn(1080, maxCap)
        }
    val imageRequest =
        remember(
            effectiveUrl,
            thumbnail,
            maxDecodeDimensionPx,
            defaultMaxDimension,
            allowRgb565,
            hasSuccessListener,
            hasLoadingListener,
        ) {
            val builder =
                ImageRequest
                    .Builder(context)
                    .data(effectiveUrl)
                    .httpHeaders(PixivImageHeaders)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(!thumbnail && crossfade && PlatformCapabilities.supportsImageCrossfade(context))

            if (hasLoadingListener || hasSuccessListener) {
                builder.listener(
                    onStart = {
                        if (hasLoadingListener) {
                            isLoading = true
                            currentOnLoadingStateChanged?.invoke(true)
                        }
                    },
                    onSuccess = { _, result ->
                        if (hasLoadingListener) {
                            isLoading = false
                            currentOnLoadingStateChanged?.invoke(false)
                        }
                        if (hasSuccessListener) {
                            runCatching {
                                currentOnSuccess?.invoke(result.image.toBitmap())
                            }
                        }
                    },
                    onError = { _, _ ->
                        if (hasLoadingListener) {
                            isLoading = false
                            currentOnLoadingStateChanged?.invoke(false)
                        }
                    },
                )
            }

            builder
                .apply {
                    if (thumbnail) {
                        val defaultThumbSize = PlatformCapabilities.recommendedThumbnailDecodeDimension(context)
                        val thumbSize =
                            if (maxDecodeDimensionPx != null && maxDecodeDimensionPx > 0) {
                                maxDecodeDimensionPx
                            } else {
                                defaultThumbSize
                            }
                        size(thumbSize)
                        scale(Scale.FILL)
                        precision(Precision.INEXACT)
                        allowRgb565(true)
                    } else {
                        val targetSize =
                            if (maxDecodeDimensionPx != null && maxDecodeDimensionPx > 0) {
                                maxDecodeDimensionPx
                            } else if (maxDecodeDimensionPx == null) {
                                defaultMaxDimension
                            } else {
                                null
                            }
                        if (targetSize != null) {
                            size(targetSize)
                            scale(Scale.FIT)
                            precision(Precision.INEXACT)
                        }
                        val shouldAllowRgb565 =
                            allowRgb565 ||
                                PlatformCapabilities.recommendedBitmapConfig(context) == Bitmap.Config.RGB_565
                        if (shouldAllowRgb565) {
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
    val effectiveLimit = minOf(limit, PlatformCapabilities.recommendedPrefetchItemCount(context))
    val prefetchUrls =
        remember(urls, proxyBaseUrl, effectiveLimit) {
            urls
                .asSequence()
                .filter { it.isNotBlank() }
                .map { proxyPixivImageUrl(it, proxyBaseUrl) }
                .distinct()
                .take(effectiveLimit)
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
                        .size(PlatformCapabilities.recommendedThumbnailDecodeDimension(context))
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
