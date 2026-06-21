package com.yunfie.illustia.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class PixivApiException(
    val statusCode: Int,
    val apiMessage: String,
) : IOException("Pixiv API error $statusCode: $apiMessage")

suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
    }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body.string()
                    if (it.isSuccessful) {
                        continuation.resume(body)
                    } else {
                        val pixivMessage = body
                            .lineSequence()
                            .joinToString(" ")
                            .take(240)
                            .ifBlank { it.message }
                        continuation.resumeWithException(PixivApiException(it.code, pixivMessage))
                    }
                }
            }
        },
    )
}
