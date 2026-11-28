package com.yunfie.illustia.data

import androidx.compose.runtime.Immutable

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
        return PALLERIA_IMAGE_PROXY_BASE_URL + formEncode(workersUrl)
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

/**
 * `java.net.URLEncoder.encode(value, "UTF-8")` と同じ出力。
 *
 * この値は既存プロキシ URL の一部として既に配布済みなので、置き換えは出力の互換が必須:
 * 空白は `+`、`A-Z a-z 0-9 . - * _` だけを素通しし、それ以外は UTF-8 バイトの `%XX`（大文字）。
 */
private fun formEncode(value: String): String {
    val builder = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        when {
            char == ' ' -> {
                builder.append('+')
            }

            char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' -> {
                builder.append(char)
            }

            char == '.' || char == '-' || char == '*' || char == '_' -> {
                builder.append(char)
            }

            else -> {
                builder.append('%')
                builder.append(HEX_DIGITS[code shr 4])
                builder.append(HEX_DIGITS[code and 0x0F])
            }
        }
    }
    return builder.toString()
}

private const val HEX_DIGITS = "0123456789ABCDEF"
