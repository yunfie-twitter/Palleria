package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Pixiv 画像の描画オプション。プラットフォーム実装にそのまま渡す。
 *
 * Coil のリクエスト構築・Bitmap デコード・環境設定の解決は Android 専用のため、
 * 共有 UI はパラメータだけを組み立てて [LocalPixivImageRenderer] に委ねる。
 */
data class PixivImageSpec(
    val url: String,
    val contentDescription: String?,
    val contentScale: ContentScale,
    val modifier: Modifier,
    val crossfade: Boolean,
    val thumbnail: Boolean,
    val maxDecodeDimensionPx: Int?,
    val allowRgb565: Boolean,
    val showLoadingSpinner: Boolean,
    val placeholderUrl: String?,
    val retryToken: Int,
)

/**
 * Pixiv 画像の描画実装。androidApp が Coil で実装し、
 * [LocalPixivImageRenderer] 経由で共有 UI に注入する。
 */
fun interface PixivImageRenderer {
    @Composable
    fun Render(spec: PixivImageSpec)
}

val LocalPixivImageRenderer = compositionLocalOf<PixivImageRenderer?> { null }

/**
 * 画像の先読み実装。Android は Coil のメージローダに投げる。
 */
fun interface PixivPrefetchRenderer {
    @Composable
    fun Prefetch(
        urls: List<String>,
        limit: Int,
    )
}

val LocalPixivPrefetchRenderer = compositionLocalOf<PixivPrefetchRenderer?> { null }

/**
 * Pixiv 画像。実装未注入時はレイアウトを保つため空の [Box] を置く。
 */
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
    retryToken: Int = 0,
) {
    val renderer = LocalPixivImageRenderer.current
    if (renderer == null) {
        Box(modifier = modifier)
        return
    }
    renderer.Render(
        PixivImageSpec(
            url = url,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
            crossfade = crossfade,
            thumbnail = thumbnail,
            maxDecodeDimensionPx = maxDecodeDimensionPx,
            allowRgb565 = allowRgb565,
            showLoadingSpinner = showLoadingSpinner,
            placeholderUrl = placeholderUrl,
            retryToken = retryToken,
        ),
    )
}

/** 画像を先読みする。実装未注入時は何もしない。 */
@Composable
fun PrefetchPixivImages(
    urls: List<String>,
    enabled: Boolean,
    limit: Int = 12,
) {
    val renderer = LocalPixivPrefetchRenderer.current
    if (!enabled || urls.isEmpty() || renderer == null) return
    renderer.Prefetch(urls, limit)
}
