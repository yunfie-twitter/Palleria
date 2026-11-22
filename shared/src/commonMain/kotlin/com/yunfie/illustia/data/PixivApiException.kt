package com.yunfie.illustia.data

import kotlinx.io.IOException

/**
 * Pixiv API が返したエラー。HTTP ステータスと API メッセージを保持する。
 */
class PixivApiException(
    val statusCode: Int,
    val apiMessage: String,
    cause: Throwable? = null,
) : IOException("Pixiv API error $statusCode: $apiMessage", cause)

/** 通信断・レート制限などの判定を 1 か所にまとめる。 */
fun Throwable.isPixivRateLimited(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val isRateLimited =
            (current is PixivApiException && current.statusCode == 429) ||
                current.message.orEmpty().let { msg ->
                    msg.contains("429") ||
                        msg.contains("rate limit", ignoreCase = true) ||
                        msg.contains("Too Many Requests", ignoreCase = true)
                }
        if (isRateLimited) {
            return true
        }
        current = current.cause
    }
    return false
}
