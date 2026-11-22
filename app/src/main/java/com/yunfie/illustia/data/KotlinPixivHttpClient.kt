package com.yunfie.illustia.data

import android.os.Build
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.MAX_SESSION_LIFETIME_SECONDS
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.settings.currentAcceptLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp ベースの Pixiv HTTP クライアント。
 *
 * 以前は UniFFI 経由で Rust 実装を呼んでいたが、Rust クライアント廃止に伴い全処理を
 * Kotlin へ移した。`PixivApiClient` からは従来どおり `newCall(request).awaitXxx()` の形で使う。
 */
internal class KotlinPixivHttpClient(
    private val mode: NetworkMode,
) {
    private val okHttp: OkHttpClient by lazy { createPixivHttpClient(mode) }
    private val signer: PixivRequestSigner by lazy {
        PixivRequestSigner(
            userAgent =
                "PixivAndroidApp/${PixivApiConfig.APP_VERSION} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})",
            appOsVersion = "Android ${Build.VERSION.RELEASE}",
            acceptLanguage = currentAcceptLanguage(),
        )
    }

    fun newCall(request: Request): PixivCall = PixivCall(okHttp, signer, request)

    suspend fun loginWithRefreshToken(refreshToken: String): PixivSession {
        val token = refreshToken.trim()
        require(token.isNotEmpty()) { "refresh token を入力してください。" }
        return oauthLogin(
            fields =
                listOf(
                    "grant_type" to "refresh_token",
                    "include_policy" to "true",
                    "refresh_token" to token,
                ),
            fallbackRefreshToken = token,
        )
    }

    suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession {
        require(code.trim().isNotEmpty()) { "authorization code is empty" }
        require(codeVerifier.trim().isNotEmpty()) { "authorization code verifier is empty" }
        return oauthLogin(
            fields =
                listOf(
                    "grant_type" to "authorization_code",
                    "include_policy" to "true",
                    "code" to code,
                    "code_verifier" to codeVerifier,
                    "redirect_uri" to PixivApiConstants.REDIRECT_URI,
                ),
            fallbackRefreshToken = null,
        )
    }

    fun createWebLoginUrl(
        createProvisionalAccount: Boolean,
        codeChallenge: String,
    ): String {
        val path = if (createProvisionalAccount) "provisional-accounts/create" else "login"
        return "${PixivApiConstants.WEB_LOGIN_BASE}/$path"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("client", "pixiv-android")
            .build()
            .toString()
    }

    suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback =
        withContext(Dispatchers.IO) {
            UgoiraCache.prepare(okHttp, signer, url, cacheDir, frames)
        }

    private suspend fun oauthLogin(
        fields: List<Pair<String, String>>,
        fallbackRefreshToken: String?,
    ): PixivSession =
        withContext(Dispatchers.IO) {
            val form =
                FormBody
                    .Builder()
                    .apply {
                        fields.forEach { (name, value) -> add(name, value) }
                        add("client_id", PixivApiConstants.CLIENT_ID)
                        add("client_secret", PixivApiConstants.CLIENT_SECRET)
                    }.build()
            val request =
                Request
                    .Builder()
                    .url(PixivApiConstants.OAUTH_URL)
                    .headers(signer.sign(okhttp3.Headers.headersOf()))
                    .post(form)
                    .build()
            val body = okHttp.newCall(request).awaitTextBody()
            val payload =
                runCatching { parseOAuthResponse(body) }
                    .getOrElse { throw PixivApiException(0, "invalid OAuth response: ${it.message}") }
            val refreshToken =
                payload.refreshToken ?: fallbackRefreshToken
                    ?: throw PixivApiException(0, "OAuth response has no refresh token")
            val expiresAt =
                payload.expiresIn
                    ?.coerceAtMost(MAX_SESSION_LIFETIME_SECONDS)
                    ?.let { seconds -> System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds) }
                    ?: 0
            PixivSession(
                accessToken = payload.accessToken,
                refreshToken = refreshToken,
                userId = payload.user?.resolveId(),
                expiresAtMillis = expiresAt,
            )
        }
}

/**
 * 1 リクエスト分の呼び出し。`PixivApiClient` が組み立てた OkHttp [Request] を受け取り、
 * 署名ヘッダを付け、レスポンスをアプリモデルへデコードする。エンドポイントごとのデコードは
 * クラス肥大化を避けるため一部を extension 関数（PixivCallDecoders.kt）に分割している。
 */
internal class PixivCall(
    private val okHttp: OkHttpClient,
    private val signer: PixivRequestSigner,
    private val request: Request,
) {
    internal fun signed(): Request =
        request
            .newBuilder()
            .headers(signer.sign(request.headers))
            .build()

    suspend fun awaitIllustPage(): PageResult<Illust> = decode(IllustPageResponse.serializer()) { PixivApiDecoder.illustPage(it) }

    suspend fun awaitIllustDetail(): Illust = decode(IllustDetailResponse.serializer()) { PixivApiDecoder.illustDetail(it) }

    suspend fun awaitUserProfile(fallbackUserId: Long): UserProfile =
        decode(UserDetailResponse.serializer()) { PixivApiDecoder.userProfile(it, fallbackUserId) }

    suspend fun awaitAutocomplete(): List<String> = decode(AutocompleteResponse.serializer()) { PixivApiDecoder.autocomplete(it) }

    suspend fun awaitUserPreviewPage(): PageResult<UserPreview> =
        decode(UserPreviewPageResponse.serializer()) { PixivApiDecoder.userPreviewPage(it) }

    suspend fun awaitUgoiraMetadata(): UgoiraMetadataResponse =
        decode(UgoiraMetadataResponseDto.serializer()) { PixivApiDecoder.ugoiraMetadata(it) }

    suspend fun awaitNovelText(novelId: Long): NovelTextContent =
        withContext(Dispatchers.IO) {
            val body = okHttp.newCall(signed()).awaitTextBody()
            PixivNovelDecoder.novelText(body, novelId)
        }

    suspend fun awaitComplete() {
        withContext(Dispatchers.IO) { okHttp.newCall(signed()).awaitTextBody() }
    }

    internal suspend fun <D, M> decode(
        deserializer: DeserializationStrategy<D>,
        toModel: (D) -> M,
    ): M =
        withContext(Dispatchers.IO) {
            val body = okHttp.newCall(signed()).awaitTextBody()
            val decoded =
                try {
                    PixivApiJson.json.decodeFromString(deserializer, body)
                } catch (error: SerializationException) {
                    throw PixivApiException(0, error.message ?: error.toString(), error)
                }
            toModel(decoded)
        }
}

/** レスポンスボディを text として読み出す。非 2xx は [PixivApiException] にする。 */
internal suspend fun Call.awaitTextBody(): String =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
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
                    try {
                        response.use {
                            val text = it.body.string()
                            if (!it.isSuccessful) {
                                val detail =
                                    text
                                        .lineSequence()
                                        .joinToString(" ")
                                        .take(240)
                                        .ifBlank { it.message }
                                continuation.resumeWithException(PixivApiException(it.code, detail))
                                return
                            }
                            continuation.resume(text)
                        }
                    } catch (error: IOException) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            },
        )
    }
