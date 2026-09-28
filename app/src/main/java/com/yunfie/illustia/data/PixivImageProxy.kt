package com.yunfie.illustia.data

import androidx.compose.runtime.Immutable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Immutable
data class PixivImageProxy(
    val name: String,
    val baseUrl: String,
)

const val PALLERIA_IMAGE_PROXY_BASE_URL = "https://webp.yunfi.f5.si/image.webp?url="
private const val PALLERIA_WORKERS_HOST_PREFIX = "https://piximg.2wf6bfhvwk.workers.dev/"
private const val LEGACY_PALLERIA_PROXY_BASE_URL = "https://i.yunfi.f5.si/"
private const val LEGACY_PALLERIA_WEBP_PROXY_BASE_URL = "https://proxy.yunfi.f5.si/image.webp?url="

val PixivImageProxyOptions =
    listOf(
        PixivImageProxy("Palleria", PALLERIA_IMAGE_PROXY_BASE_URL),
        PixivImageProxy("suimoe.com", "https://i.suimoe.com/"),
        PixivImageProxy("pixiv.re", "https://i.pixiv.re/"),
    )

fun proxyPixivImageUrl(
    url: String,
    proxyBaseUrl: String,
): String {
    if (url.isBlank() || proxyBaseUrl.isBlank()) return url

    val sourcePrefix = url.pixivImagePrefixOrNull() ?: return url
    val relativePath = url.removePrefix(sourcePrefix)

    if (proxyBaseUrl == PALLERIA_IMAGE_PROXY_BASE_URL ||
        proxyBaseUrl == LEGACY_PALLERIA_PROXY_BASE_URL ||
        proxyBaseUrl == LEGACY_PALLERIA_WEBP_PROXY_BASE_URL
    ) {
        val workersUrl = PALLERIA_WORKERS_HOST_PREFIX + relativePath
        return PALLERIA_IMAGE_PROXY_BASE_URL + URLEncoder.encode(workersUrl, StandardCharsets.UTF_8.name())
    }

    val proxy = proxyBaseUrl.trim().trimEnd('/')
    if (!proxy.startsWith("https://") && !proxy.startsWith("http://")) return url

    return "$proxy/$relativePath"
}

fun normalizePixivImageProxyBaseUrl(url: String): String =
    when (url.trim()) {
        "https://i.yunfi.f5.si/",
        "https://i.yunfi.f5.si",
        "https://proxy.yunfi.f5.si/image.webp?url=",
        "https://proxy.yunfi.f5.si/image.webp?url",
        -> PALLERIA_IMAGE_PROXY_BASE_URL

        else -> url
    }

private fun String.pixivImagePrefixOrNull(): String? =
    when {
        startsWith("https://i.pximg.net/") -> "https://i.pximg.net/"
        startsWith("http://i.pximg.net/") -> "http://i.pximg.net/"
        startsWith("https://i-f.pximg.net/") -> "https://i-f.pximg.net/"
        startsWith("http://i-f.pximg.net/") -> "http://i-f.pximg.net/"
        else -> null
    }
