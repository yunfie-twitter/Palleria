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
