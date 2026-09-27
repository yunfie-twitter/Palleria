package com.yunfie.illustia.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import java.io.IOException

class OkHttpAwaitTest :
    StringSpec({
        "response read failures reach the caller and close the body" {
            coroutineScope {
                val call = ControlledCall()
                val body = TrackingBody(failOnRead = true)
                val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { call.awaitBody() } }

                call.respond(body)

                val error = result.await().exceptionOrNull().shouldBeInstanceOf<IOException>()
                error.message shouldBe "interrupted body"
                body.closed shouldBe true
            }
        }

        "successful responses return their text and close the body" {
            coroutineScope {
                val call = ControlledCall()
                val body = TrackingBody()
                val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitBody() }

                call.respond(body)

                result.await() shouldBe "response body"
                body.closed shouldBe true
            }
        }

        "HTTP failures retain their status and close the body" {
            coroutineScope {
                val call = ControlledCall()
                val body = TrackingBody()
                val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { call.awaitBody() } }

                call.respond(body, code = 429)

                val error = result.await().exceptionOrNull().shouldBeInstanceOf<PixivApiException>()
                error.statusCode shouldBe 429
                error.apiMessage shouldBe "response body"
                body.closed shouldBe true
            }
        }

        "cancellation cancels the call and closes a late response" {
            coroutineScope {
                val call = ControlledCall()
                val body = TrackingBody(failOnRead = true)
                val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitBody() }

                result.cancel()
                call.respond(body)
                result.join()

                call.isCanceled() shouldBe true
                result.isCancelled shouldBe true
                body.closed shouldBe true
            }
        }
    })

private class ControlledCall(
    delegate: Call = OkHttpClient().newCall(Request.Builder().url("https://app-api.pixiv.net/test").build()),
) : Call by delegate {
    private val request = delegate.request()
    private var callback: Callback? = null
    private var cancelled = false

    override fun request(): Request = request

    override fun execute(): Response = error("Use enqueue")

    override fun enqueue(responseCallback: Callback) {
        callback = responseCallback
    }

    override fun cancel() {
        cancelled = true
    }

    override fun isExecuted(): Boolean = callback != null

    override fun isCanceled(): Boolean = cancelled

    override fun timeout(): Timeout = Timeout.NONE

    override fun clone(): Call = ControlledCall()

    fun respond(
        body: ResponseBody,
        code: Int = 200,
    ) {
        checkNotNull(callback).onResponse(
            this,
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test response")
                .body(body)
                .build(),
        )
    }
}

private class TrackingBody(
    private val failOnRead: Boolean = false,
) : ResponseBody() {
    var closed = false
        private set

    private val source =
        object : ForwardingSource(Buffer().writeUtf8("response body")) {
            override fun read(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                if (failOnRead) throw IOException("interrupted body")
                return super.read(sink, byteCount)
            }

            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = -1L

    override fun source(): BufferedSource = source
}
