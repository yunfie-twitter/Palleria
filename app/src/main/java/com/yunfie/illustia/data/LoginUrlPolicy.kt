package com.yunfie.illustia.data

import java.net.URI
import java.net.URLDecoder

private const val HTTPS_PORT = 443

internal fun pixivLoginCodeOrNull(url: String): String? =
    runCatching {
        val uri = URI(url)
        val isAppRedirect =
            uri.scheme.equals("pixiv", ignoreCase = true) &&
                uri.host.equals("account", ignoreCase = true) &&
                uri.port == -1 && uri.rawPath == "/login"
        val isWebRedirect =
            uri.isSecureOrigin("app-api.pixiv.net") &&
                uri.rawPath == "/web/v1/users/auth/pixiv/callback"
        val isCallbackRoute = isAppRedirect || isWebRedirect
        val hasInvalidAuthority = uri.isOpaque || uri.rawUserInfo != null || uri.rawFragment != null
        if (!isCallbackRoute || hasInvalidAuthority) {
            null
        } else {
            uri.rawQuery
                ?.split('&')
                ?.map { it.split('=', limit = 2) }
                ?.filter { URLDecoder.decode(it[0], "UTF-8") == "code" }
                ?.singleOrNull()
                ?.getOrNull(1)
                ?.let { URLDecoder.decode(it, "UTF-8") }
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

internal fun isDiscordAppUrl(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.isSecureOrigin("discord.com") &&
        (uri.rawPath == "/app" || uri.rawPath == "/channels/@me" || uri.rawPath.startsWith("/channels/@me/"))
}

private fun URI.isSecureOrigin(expectedHost: String): Boolean =
    !isOpaque && scheme.equals("https", ignoreCase = true) &&
        host.equals(expectedHost, ignoreCase = true) &&
        (port == -1 || port == HTTPS_PORT) && rawUserInfo == null
