package com.yunfie.illustia.data

import java.net.URI

private const val PIXIV_API_HOST = "app-api.pixiv.net"
private const val HTTPS_PORT = 443

private fun URI.isSecureOrigin(expectedHost: String): Boolean =
    !isOpaque && scheme.equals("https", ignoreCase = true) &&
        host.equals(expectedHost, ignoreCase = true) &&
        (port == -1 || port == HTTPS_PORT) && rawUserInfo == null

/**
 * Pixiv API の URL だけを許可する。API レスポンス由来の next_url へトークンを
 * 送らないための最終防衛線。ホスト・スキーム・ポート・userinfo を検証する。
 */
actual fun trustedPixivApiUrl(url: String): String {
    val uri =
        runCatching { URI(url) }.getOrNull()
            ?: throw IllegalArgumentException("Untrusted Pixiv API URL")
    require(uri.isSecureOrigin(PIXIV_API_HOST)) { "Untrusted Pixiv API URL" }
    return url
}

/** 401 / 無効なトークン応答を検出する。再ログインが必要かの判断に使う。 */
actual fun Throwable.isPixivAuthExpired(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is PixivApiException) {
            when (current.statusCode) {
                401 -> {
                    return true
                }

                400 -> {
                    val message = current.apiMessage.lowercase()
                    if (message.contains("oauth") || message.contains("token") ||
                        message.contains("invalid_grant") || message.contains("invalid refresh")
                    ) {
                        return true
                    }
                }
            }
        }
        current = current.cause
    }
    return false
}

/** 一時的な接続断かどうか。リトライ判断に使う。 */
actual fun Throwable.isTransientConnectionIssue(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.message.orEmpty().contains("Connection closed before full header was received")) {
            return true
        }
        current = current.cause
    }
    return false
}
