package com.yunfie.illustia.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PixivApiException(
    val statusCode: Int,
    val apiMessage: String,
    cause: Throwable? = null,
) : IOException("Pixiv API error $statusCode: $apiMessage", cause)

suspend fun Call.awaitBody(): String =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    val body =
                        try {
                            response.use {
                                val text = it.body.string()
                                if (!it.isSuccessful) {
                                    val pixivMessage =
                                        text
                                            .lineSequence()
                                            .joinToString(" ")
                                            .take(240)
                                            .ifBlank { it.message }
                                    throw PixivApiException(it.code, pixivMessage)
                                }
                                text
                            }
                        } catch (error: IOException) {
                            continuation.resumeWithException(error)
                            return
                        }
                    continuation.resume(body)
                }
            },
        )
    }

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
