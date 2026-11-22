package com.yunfie.illustia.data

import androidx.compose.runtime.Immutable
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Immutable
data class PixivImageProxy(
    val name: String,
    val baseUrl: String,
)

const val PALLERIA_IMAGE_PROXY_BASE_URL = "https://media.yunfi.f5.si/image.webp?url="
private const val PALLERIA_WORKERS_HOST_PREFIX = "https://pximg.2wf6bfhvwk.workers.dev/"

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
    val secureUrl = url.replaceFirst("http://", "https://")
    if (secureUrl.isBlank() || proxyBaseUrl.isBlank()) return secureUrl

    val sourcePrefix = secureUrl.pixivImagePrefixOrNull() ?: return secureUrl
    val relativePath = secureUrl.removePrefix(sourcePrefix)

    if (proxyBaseUrl == PALLERIA_IMAGE_PROXY_BASE_URL ||
        proxyBaseUrl.startsWith("https://media.yunfi.f5.si/")
    ) {
        val workersUrl = PALLERIA_WORKERS_HOST_PREFIX + relativePath
        return PALLERIA_IMAGE_PROXY_BASE_URL + URLEncoder.encode(workersUrl, StandardCharsets.UTF_8.name())
    }

    val proxy = proxyBaseUrl.trim().trimEnd('/')
    if (!proxy.startsWith("https://")) return secureUrl

    return "$proxy/$relativePath"
}

fun normalizePixivImageProxyBaseUrl(url: String): String =
    when (url.trim()) {
        "https://media.yunfi.f5.si/image.webp?url=",
        "https://media.yunfi.f5.si/image.webp?url",
        -> PALLERIA_IMAGE_PROXY_BASE_URL

        else -> url.trim().replaceFirst("http://", "https://")
    }

private fun String.pixivImagePrefixOrNull(): String? =
    when {
        startsWith("https://i.pximg.net/") -> "https://i.pximg.net/"
        startsWith("https://i-f.pximg.net/") -> "https://i-f.pximg.net/"
        else -> null
    }
