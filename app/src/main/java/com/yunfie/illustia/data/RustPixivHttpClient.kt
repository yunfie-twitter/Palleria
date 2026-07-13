package com.yunfie.illustia.data

import android.os.Build
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.rust.ApiException
import com.yunfie.illustia.rust.PixivHttpClient
import com.yunfie.illustia.settings.currentAcceptLanguage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer
import okio.sink

/** OkHttp request builder compatibility layer backed by the UniFFI Rust client. */
internal class RustPixivHttpClient(mode: NetworkMode) {
    private val native = PixivHttpClient(
        networkMode = mode.code,
        userAgent = "PixivAndroidApp/${PixivApiConfig.APP_VERSION} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})",
        appOsVersion = "Android ${Build.VERSION.RELEASE}",
        acceptLanguage = currentAcceptLanguage(),
    )

    fun newCall(request: Request): RustPixivCall = RustPixivCall(native, request)

    suspend fun loginWithRefreshToken(refreshToken: String): PixivSession = withContext(Dispatchers.IO) {
        nativeCall {
            native.loginWithRefreshToken(refreshToken).let {
                PixivSession(it.accessToken, it.refreshToken, it.userId?.toLong())
            }
        }
    }

    suspend fun loginWithAuthorizationCode(code: String, codeVerifier: String): PixivSession = withContext(Dispatchers.IO) {
        nativeCall {
            native.loginWithAuthorizationCode(code, codeVerifier).let {
                PixivSession(it.accessToken, it.refreshToken, it.userId?.toLong())
            }
        }
    }

    fun createWebLoginUrl(createProvisionalAccount: Boolean, codeChallenge: String): String =
        native.createWebLoginUrl(createProvisionalAccount, codeChallenge)
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
        nativeCall {
            native.execute(
                method = request.method,
                url = request.url.toString(),
                headers = headers,
                body = sink.toByteArray(),
                contentType = request.body?.contentType()?.toString(),
            ).body
        }
    }
}

private inline fun <T> nativeCall(block: () -> T): T = try {
    block()
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
