package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.MAX_SESSION_LIFETIME_SECONDS
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlin.time.Clock

/**
 * Pixiv HTTP クライアント (Ktor 版)。
 *
 * `PixivApiClient` からは従来どおり `request(...).awaitXxx()` の形で使えるよう、
 * 1 リクエストを表す [PixivCall] を返す。
 */
class PixivHttpClient(
    private val http: HttpClient,
    private val signer: PixivRequestSigner,
) {
    fun newCall(request: PixivRequest): PixivCall = PixivCall(http, signer, request)

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
        val params =
            listOf(
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
                "client" to "pixiv-android",
            )
        val query = params.joinToString("&") { (k, v) -> "$k=$v" }
        return "${PixivApiConstants.WEB_LOGIN_BASE}/$path?$query"
    }

    private suspend fun oauthLogin(
        fields: List<Pair<String, String>>,
        fallbackRefreshToken: String?,
    ): PixivSession =
        withContext(Dispatchers.IO) {
            val form = (fields + listOf("client_id" to PixivApiConstants.CLIENT_ID, "client_secret" to PixivApiConstants.CLIENT_SECRET))
            val response =
                http.request(PixivApiConstants.OAUTH_URL) {
                    method = HttpMethod.Post
                    headers {
                        signer.sign(emptyMap()).forEach { (name, value) -> header(name, value) }
                        contentType(ContentType.Application.FormUrlEncoded)
                    }
                    setBody(form.toFormBody())
                }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw PixivApiException(response.status.value, text.take(240).ifBlank { response.status.description })
            }
            val payload =
                runCatching { parseOAuthResponse(text) }
                    .getOrElse { throw PixivApiException(0, "invalid OAuth response: ${it.message}") }
            val refreshToken =
                payload.refreshToken ?: fallbackRefreshToken
                    ?: throw PixivApiException(0, "OAuth response has no refresh token")
            val expiresAt =
                payload.expiresIn
                    ?.coerceAtMost(MAX_SESSION_LIFETIME_SECONDS)
                    ?.let { seconds -> Clock.System.now().toEpochMilliseconds() + seconds * 1000 }
                    ?: 0
            PixivSession(
                accessToken = payload.accessToken,
                refreshToken = refreshToken,
                userId = payload.user?.resolveId(),
                expiresAtMillis = expiresAt,
            )
        }
}

private fun List<Pair<String, String>>.toFormBody(): String =
    joinToString("&") { (k, v) -> "${k.encodeForm()}=${v.encodeForm()}" }

private fun String.encodeForm(): String =
    buildString {
        for (char in this@encodeForm) {
            when {
                char.isLetterOrDigit() || char in "-._~" -> append(char)
                char == ' ' -> append('+')
                else -> {
                    for (b in char.toString().encodeToByteArray()) {
                        append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
                    }
                }
            }
        }
    }

private const val HEX = "0123456789ABCDEF"

/**
 * 1 リクエスト分の呼び出し。レスポンスをアプリモデルへデコードする。
 */
class PixivCall(
    private val http: HttpClient,
    private val signer: PixivRequestSigner,
    private val request: PixivRequest,
) {
    internal suspend fun awaitIllustPage(): PageResult<Illust> = decode(IllustPageResponse.serializer()) { PixivApiDecoder.illustPage(it) }

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
            val body = execute()
            PixivNovelDecoder.novelText(body, novelId)
        }

    suspend fun awaitComplete() {
        withContext(Dispatchers.IO) { execute() }
    }

    internal suspend fun <D, M> decode(
        deserializer: DeserializationStrategy<D>,
        toModel: (D) -> M,
    ): M =
        withContext(Dispatchers.IO) {
            val body = execute()
            val decoded =
                try {
                    PixivApiJson.json.decodeFromString(deserializer, body)
                } catch (error: SerializationException) {
                    throw PixivApiException(0, error.message ?: error.toString(), error)
                }
            toModel(decoded)
        }

    /** 署名付きでリクエストを送り、ボディを text として返す。非 2xx は例外にする。 */
    internal suspend fun execute(): String {
        val signed = signer.sign(request.headers)
        val response =
            http.request(request.url) {
                method = request.method
                headers {
                    signed.forEach { (name, value) -> header(name, value) }
                }
                request.queryParameters.forEach { (name, value) -> parameter(name, value) }
                request.formFields?.let {
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(it.toFormBody())
                }
            }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val detail = text.lineSequence().joinToString(" ").take(240).ifBlank { response.status.description }
            throw PixivApiException(response.status.value, detail)
        }
        return text
    }
}

/** 1 リクエストの内容。OkHttp の Request に代わる KMP 向けの最小表現。 */
class PixivRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.Get,
    val headers: Map<String, String> = emptyMap(),
    val queryParameters: List<Pair<String, String?>> = emptyList(),
    val formFields: List<Pair<String, String>>? = null,
)

/** ビルダー。既存の `PixivApiClient` からの移行を短く書けるように用意する。 */
class PixivRequestBuilder(
    private val url: String,
) {
    private var method: HttpMethod = HttpMethod.Get
    private val headers = LinkedHashMap<String, String>()
    private val query = mutableListOf<Pair<String, String?>>()
    private var form: List<Pair<String, String>>? = null

    fun get(): PixivRequestBuilder = apply { method = HttpMethod.Get }

    fun post(body: List<Pair<String, String>>): PixivRequestBuilder =
        apply {
            method = HttpMethod.Post
            form = body
        }

    fun delete(): PixivRequestBuilder = apply { method = HttpMethod.Delete }

    fun addHeader(
        name: String,
        value: String,
    ): PixivRequestBuilder = apply { headers[name] = value }

    fun addHeaders(values: Map<String, String>): PixivRequestBuilder = apply { headers.putAll(values) }

    fun addQueryParameter(
        name: String,
        value: String?,
    ): PixivRequestBuilder = apply { query += name to value }

    fun build(): PixivRequest = PixivRequest(url, method, headers.toMap(), query.toList(), form)
}

/** Ktor の `HttpRequestBuilder` 相当を使わずにリクエストを組み立てるための入口。 */
fun pixivRequest(
    url: String,
    block: PixivRequestBuilder.() -> Unit = {},
): PixivRequest = PixivRequestBuilder(url).apply(block).build()
