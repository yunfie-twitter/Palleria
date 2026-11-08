package com.yunfie.illustia.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import coil3.toBitmap
import com.yunfie.illustia.data.NativeImageAnalysis
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.platform.PlatformCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

val PixivImageHeaders =
    NetworkHeaders
        .Builder()
        .set("Referer", "https://www.pixiv.net/")
        .set("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
        .build()

val LocalWideColorGamutEnabled = compositionLocalOf { true }

internal val LocalImageRefreshRequest = compositionLocalOf { 0 }

private const val HEADER_THEME_ANALYSIS_CACHE_SIZE = 32
internal const val AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX = 192

/** Bounded cache of per-artwork header theme analysis; the same thumbnail is often analyzed by several screens. */
private val headerThemeAnalysisCache: MutableMap<String, Boolean> =
    object : java.util.LinkedHashMap<String, Boolean>(HEADER_THEME_ANALYSIS_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > HEADER_THEME_ANALYSIS_CACHE_SIZE
    }

private val headerThemeAnalysisScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val headerThemeAnalysisInFlight = ConcurrentHashMap<String, Deferred<Boolean>>()

/**
 * Resolves whether the header overlay should use dark icons for [thumbnailUrl], reusing the cached
 * result when this artwork was analyzed before. Concurrent callers for the same artwork share one
 * analysis; different artworks are analyzed in parallel. Never blocks the caller on the result path.
 */
internal suspend fun resolveDarkHeaderIcons(
    context: android.content.Context,
    thumbnailUrl: String,
    proxyBaseUrl: String,
): Boolean {
    val key = proxyPixivImageUrl(thumbnailUrl, proxyBaseUrl)
    synchronized(headerThemeAnalysisCache) { headerThemeAnalysisCache[key] }?.let { return it }
    val analysis =
        headerThemeAnalysisInFlight.computeIfAbsent(key) { cacheKey ->
            headerThemeAnalysisScope.async(start = CoroutineStart.LAZY) {
                try {
                    synchronized(headerThemeAnalysisCache) { headerThemeAnalysisCache[cacheKey] }
                        ?: runCatching { analyzeDarkHeaderIcons(context, cacheKey) }
                            .getOrDefault(false)
                            .also { isDark ->
                                synchronized(headerThemeAnalysisCache) { headerThemeAnalysisCache[cacheKey] = isDark }
                            }
                } finally {
                    headerThemeAnalysisInFlight.remove(cacheKey)
                }
            }
        }
    analysis.start()
    return analysis.await()
}

private suspend fun analyzeDarkHeaderIcons(
    context: android.content.Context,
    key: String,
): Boolean {
    val request =
        ImageRequest
            .Builder(context)
            .data(key)
            .httpHeaders(PixivImageHeaders)
            .size(64)
            .build()
    val result = SingletonImageLoader.get(context).execute(request)
    if (result !is SuccessResult) return false
    return withContext(Dispatchers.Default) {
        runCatching { NativeImageAnalysis.shouldUseDarkHeaderIcons(result.image.toBitmap()) }.getOrDefault(false)
    }
}

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
    placeholderUrl: String? = null,
    onSuccess: ((Bitmap) -> Unit)? = null,
    onLoadingStateChanged: ((Boolean) -> Unit)? = null,
) {
    val refreshRequest = LocalImageRefreshRequest.current
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val wideColorGamutEnabled = LocalWideColorGamutEnabled.current
    val effectiveUrl =
        remember(url, proxyBaseUrl) {
            proxyPixivImageUrl(url, proxyBaseUrl)
        }
    val hasPlaceholder = !thumbnail && !placeholderUrl.isNullOrBlank() && placeholderUrl != url
    val effectivePlaceholderUrl =
        remember(placeholderUrl, proxyBaseUrl) {
            if (hasPlaceholder) proxyPixivImageUrl(placeholderUrl!!, proxyBaseUrl) else null
        }

    val hasSuccessListener = onSuccess != null
    val hasLoadingListener = onLoadingStateChanged != null || showLoadingSpinner || hasPlaceholder
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
    val decodeDimensionPx =
        if (thumbnail) {
            if (maxDecodeDimensionPx != null && maxDecodeDimensionPx > 0) {
                maxDecodeDimensionPx
            } else {
                PlatformCapabilities.recommendedThumbnailDecodeDimension(context)
            }
        } else if (maxDecodeDimensionPx != null && maxDecodeDimensionPx > 0) {
            maxDecodeDimensionPx
        } else if (maxDecodeDimensionPx == null) {
            defaultMaxDimension
        } else {
            null
        }
    val imageRequest =
        remember(
            effectiveUrl,
            refreshRequest,
            thumbnail,
            decodeDimensionPx,
            allowRgb565,
            wideColorGamutEnabled,
            hasSuccessListener,
            hasLoadingListener,
        ) {
            val memoryCacheKey =
                pixivImageMemoryCacheKey(effectiveUrl, thumbnail, decodeDimensionPx) +
                    (if (refreshRequest == 0) "" else "#reload-$refreshRequest")
            val builder =
                ImageRequest
                    .Builder(context)
                    .data(effectiveUrl)
                    .httpHeaders(PixivImageHeaders)
                    .diskCachePolicy(if (refreshRequest == 0) CachePolicy.ENABLED else CachePolicy.WRITE_ONLY)
                    .memoryCacheKey(memoryCacheKey)
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
                        size(requireNotNull(decodeDimensionPx))
                        scale(Scale.FILL)
                        precision(Precision.INEXACT)
                        allowRgb565(true)
                    } else {
                        if (decodeDimensionPx != null) {
                            size(decodeDimensionPx)
                            scale(Scale.FIT)
                            precision(Precision.INEXACT)
                        }
                        val supportsWcg = PlatformCapabilities.supportsWideColorGamut(context)
                        if (wideColorGamutEnabled && supportsWcg && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            allowHardware(true)
                            bitmapConfig(Bitmap.Config.HARDWARE)
                        } else {
                            val shouldAllowRgb565 =
                                allowRgb565 ||
                                    PlatformCapabilities.recommendedBitmapConfig(context) == Bitmap.Config.RGB_565
                            if (shouldAllowRgb565) {
                                allowRgb565(true)
                            }
                        }
                    }
                }.build()
        }

    val placeholderRequest =
        effectivePlaceholderUrl?.let { placeholder ->
            remember(placeholder, refreshRequest) {
                ImageRequest
                    .Builder(context)
                    .data(placeholder)
                    .httpHeaders(PixivImageHeaders)
                    .diskCachePolicy(if (refreshRequest == 0) CachePolicy.ENABLED else CachePolicy.WRITE_ONLY)
                    .memoryCacheKey(pixivImageMemoryCacheKey(placeholder, thumbnail = true, decodeDimensionPx = null))
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .size(PlatformCapabilities.recommendedThumbnailDecodeDimension(context))
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .build()
            }
        }

    if (placeholderRequest != null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                AsyncImage(
                    model = placeholderRequest,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.matchParentSize(),
                )
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
            if (showLoadingSpinner && isLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    } else if (showLoadingSpinner) {
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
        val memoryCache = imageLoader.memoryCache
        val thumbnailDecodeDimension = PlatformCapabilities.recommendedThumbnailDecodeDimension(context)
        for (i in prefetchUrls.indices) {
            val url = prefetchUrls[i]
            if (!activeRequests.containsKey(url)) {
                val memoryCacheKey =
                    pixivImageMemoryCacheKey(
                        url,
                        thumbnail = true,
                        decodeDimensionPx = thumbnailDecodeDimension,
                    )
                // If the image is already in memory cache, no need to issue prefetch request.
                if (memoryCache != null && memoryCache[coil3.memory.MemoryCache.Key(memoryCacheKey)] != null) {
                    continue
                }

                // Add a small delay between requests to throttle concurrent disk access and image decoding spikes.
                if (i > 0) {
                    kotlinx.coroutines.delay(30L)
                }

                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .httpHeaders(PixivImageHeaders)
                        .memoryCacheKey(memoryCacheKey)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .size(thumbnailDecodeDimension)
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

/** サムネイル解像度で先読みする際のメモリキャッシュキー。通常表示の [PixivImage] と一致させる必要がある。 */
internal fun pixivImagePrefetchCacheKey(
    url: String,
    decodeDimensionPx: Int,
): String =
    pixivImageMemoryCacheKey(
        url,
        thumbnail = true,
        decodeDimensionPx = decodeDimensionPx,
    )

/**
 * [PixivImage]（サムネイル表示）と同じキー・設定で画像を先読みする。
 * メモリキャッシュに載っている場合は何もしない。
 */
internal suspend fun prefetchPixivThumbnail(
    context: android.content.Context,
    url: String,
    decodeDimensionPx: Int = PlatformCapabilities.recommendedThumbnailDecodeDimension(context),
) {
    if (url.isBlank()) return
    val imageLoader = SingletonImageLoader.get(context)
    val memoryCacheKey = pixivImagePrefetchCacheKey(url, decodeDimensionPx)
    if (imageLoader.memoryCache?.get(coil3.memory.MemoryCache.Key(memoryCacheKey)) != null) return
    val request =
        ImageRequest
            .Builder(context)
            .data(url)
            .httpHeaders(PixivImageHeaders)
            .memoryCacheKey(memoryCacheKey)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .size(decodeDimensionPx)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .allowRgb565(true)
            .build()
    imageLoader.enqueue(request)
}
