package com.yunfie.illustia.data

internal fun Throwable.isPixivAuthExpired(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is PixivApiException && current.isAuthExpired()) return true
        current = current.cause
    }
    return false
}

private fun PixivApiException.isAuthExpired(): Boolean =
    when (statusCode) {
        java.net.HttpURLConnection.HTTP_UNAUTHORIZED -> {
            true
        }

        java.net.HttpURLConnection.HTTP_BAD_REQUEST -> {
            val message = apiMessage.lowercase()
            message.contains("oauth") || message.contains("token") || message.contains("invalid_grant") ||
                message.contains("invalid refresh")
        }

        else -> {
            false
        }
    }

internal fun Throwable.isTransientConnectionIssue(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("Connection closed before full header was received")) {
            return true
        }
        current = current.cause
    }
    return false
}

/**
 * A short, user-facing description of a request failure.
 *
 * Data-layer stores have no [android.content.Context] to resolve string resources, so these
 * return plain Japanese text instead of leaking `PixivApiException: Pixiv API error 500: {...}`
 * verbatim into an error card.
 */
internal fun Throwable.toUserMessage(): String {
    var current: Throwable? = this
    var statusCode: Int? = null
    while (current != null) {
        if (current is PixivApiException) {
            statusCode = current.statusCode
            break
        }
        current = current.cause
    }
    return when {
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> "この操作を行う権限がありません。ログインし直してください。"
        statusCode == HTTP_NOT_FOUND -> "対象が見つかりませんでした。削除された可能性があります。"
        statusCode == HTTP_TOO_MANY_REQUESTS -> "リクエストが一時的に制限されています。少し待ってから再度お試しください。"
        statusCode != null && statusCode in HTTP_SERVER_ERROR_RANGE -> "サーバー側で問題が発生しました。しばらく待ってから再度お試しください。"
        statusCode != null -> "リクエストを処理できませんでした。時間をおいて再度お試しください。"
        this is java.io.IOException -> "サーバーに接続できませんでした。通信環境を確認して再度お試しください。"
        else -> "データを読み込めませんでした。時間をおいて再度お試しください。"
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
