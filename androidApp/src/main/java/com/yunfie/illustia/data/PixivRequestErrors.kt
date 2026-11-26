package com.yunfie.illustia.data

import android.content.Context
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey

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
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> AppStrings.get(StringKey.ErrorForbidden)
        statusCode == HTTP_NOT_FOUND -> AppStrings.get(StringKey.ErrorNotFound)
        statusCode == HTTP_TOO_MANY_REQUESTS -> AppStrings.get(StringKey.ErrorRateLimited)
        statusCode != null && statusCode in HTTP_SERVER_ERROR_RANGE -> AppStrings.get(StringKey.ErrorServerError)
        statusCode != null -> AppStrings.get(StringKey.ErrorRequestFailed)
        this is java.io.IOException -> AppStrings.get(StringKey.ErrorServerUnreachable)
        else -> AppStrings.get(StringKey.ErrorLoadFailed)
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
