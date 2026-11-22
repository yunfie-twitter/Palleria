package com.yunfie.illustia.data

private const val PIXIV_API_HOST = "app-api.pixiv.net"

/** Pixiv API の URL だけを許可する。信頼できないホストへトークンを送らないための最終防衛線。 */
fun trustedPixivApiUrl(url: String): String {
    require(url.startsWith("https://$PIXIV_API_HOST/")) { "Untrusted Pixiv API URL" }
    require(!url.contains('@')) { "Untrusted Pixiv API URL" }
    return url
}

/** 401 / 無効なトークン応答を検出する。再ログインが必要かの判断に使う。 */
fun Throwable.isPixivAuthExpired(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is PixivApiException) {
            when (current.statusCode) {
                401 -> return true
                400 -> {
                    val message = current.apiMessage.lowercase()
                    if (message.contains("oauth") || message.contains("token") || message.contains("invalid_grant") ||
                        message.contains("invalid refresh")
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
fun Throwable.isTransientConnectionIssue(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.message.orEmpty().contains("Connection closed before full header was received")) {
            return true
        }
        current = current.cause
    }
    return false
}
