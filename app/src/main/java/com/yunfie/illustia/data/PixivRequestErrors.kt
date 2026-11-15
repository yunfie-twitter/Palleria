package com.yunfie.illustia.data

import android.content.Context
import com.yunfie.illustia.R

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
 * Stores pass their [Context] so the message is resolved from resources in the user's
 * language instead of hardcoding Japanese, and so a raw `PixivApiException: ...` never
 * leaks into an error card.
 */
internal fun Throwable.toUserMessage(context: Context): String {
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
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> context.getString(R.string.error_forbidden)
        statusCode == HTTP_NOT_FOUND -> context.getString(R.string.error_not_found)
        statusCode == HTTP_TOO_MANY_REQUESTS -> context.getString(R.string.error_rate_limited)
        statusCode != null && statusCode in HTTP_SERVER_ERROR_RANGE -> context.getString(R.string.error_server_error)
        statusCode != null -> context.getString(R.string.error_request_failed)
        this is java.io.IOException -> context.getString(R.string.error_server_unreachable)
        else -> context.getString(R.string.error_load_failed)
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
