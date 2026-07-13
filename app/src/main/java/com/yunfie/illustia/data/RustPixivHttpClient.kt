package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.rust.ApiException
import com.yunfie.illustia.rust.PixivHttpClient
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer
import okio.sink

/** OkHttp request builder compatibility layer backed by the UniFFI Rust client. */
internal class RustPixivHttpClient(mode: NetworkMode) {
    private val native = PixivHttpClient(mode.code)

    fun newCall(request: Request): RustPixivCall = RustPixivCall(native, request)
}

internal class RustPixivCall(
    private val native: PixivHttpClient,
    private val request: Request,
) {
    suspend fun awaitBody(): String = withContext(Dispatchers.IO) {
        val sink = ByteArrayOutputStream()
        request.body?.let { body ->
            val bufferedSink = sink.sink().buffer()
            body.writeTo(bufferedSink)
            bufferedSink.flush()
        }
        val headers = buildMap {
            request.headers.names().forEach { name -> put(name, request.headers.values(name).joinToString(", ")) }
        }
        try {
            native.execute(
                method = request.method,
                url = request.url.toString(),
                headers = headers,
                body = sink.toByteArray(),
                contentType = request.body?.contentType()?.toString(),
            ).body.toString(Charsets.UTF_8)
        } catch (error: ApiException.Http) {
            throw PixivApiException(error.status.toInt(), error.detail)
        } catch (error: ApiException) {
            val detail = when (error) {
                is ApiException.InvalidRequest -> error.detail
                is ApiException.Network -> error.detail
                is ApiException.Http -> error.detail
            }
            throw PixivApiException(0, detail)
        }
    }
}
