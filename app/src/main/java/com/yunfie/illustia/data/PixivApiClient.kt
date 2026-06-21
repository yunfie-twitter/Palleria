package com.yunfie.illustia.data

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class PixivApiClient(
    private val httpClient: OkHttpClient = createPixivHttpClient(),
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun loginWithRefreshToken(refreshToken: String): PixivSession {
        val token = refreshToken.trim()
        require(token.isNotEmpty()) { "refresh token を入力してください。" }

        val request = Request.Builder()
            .url("https://oauth.secure.pixiv.net/auth/token")
            .pixivOAuthHeaders()
            .post(
                FormBody.Builder()
                    .add("client_id", PixivApiConfig.CLIENT_ID)
                    .add("client_secret", PixivApiConfig.CLIENT_SECRET)
                    .add("grant_type", "refresh_token")
                    .add("include_policy", "true")
                    .add("refresh_token", token)
                    .build(),
            )
            .build()

        val body = httpClient.newCall(request).awaitBody()
        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            val response = root["response"].asObjectOrNull() ?: root
            PixivSession(
                accessToken = response.string("access_token") ?: error("Pixiv access token を取得できませんでした。"),
                refreshToken = response.string("refresh_token") ?: token,
                userId = response["user"].asObjectOrNull()?.long("id"),
            )
        }
    }

    suspend fun loginWithAuthorizationCode(code: String, codeVerifier: String): PixivSession {
        val request = Request.Builder()
            .url("https://oauth.secure.pixiv.net/auth/token")
            .pixivOAuthHeaders()
            .post(
                FormBody.Builder()
                    .add("client_id", PixivApiConfig.CLIENT_ID)
                    .add("client_secret", PixivApiConfig.CLIENT_SECRET)
                    .add("grant_type", "authorization_code")
                    .add("include_policy", "true")
                    .add("code", code)
                    .add("code_verifier", codeVerifier)
                    .add("redirect_uri", PixivApiConfig.REDIRECT_URI)
                    .build(),
            )
            .build()

        val body = httpClient.newCall(request).awaitBody()
        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            val response = root["response"].asObjectOrNull() ?: root
            PixivSession(
                accessToken = response.string("access_token") ?: error("Pixiv access token を取得できませんでした。"),
                refreshToken = response.string("refresh_token") ?: error("Pixiv refresh token を取得できませんでした。"),
                userId = response["user"].asObjectOrNull()?.long("id"),
            )
        }
    }

    suspend fun recommended(session: PixivSession): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v1/illust/recommended?filter=for_android")
    }

    suspend fun ranking(session: PixivSession, mode: String = "day"): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v1/illust/ranking?filter=for_android&mode=$mode")
    }

    suspend fun newest(session: PixivSession): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v1/illust/new?content_type=illust&filter=for_android")
    }

    suspend fun following(session: PixivSession, restrict: Restrict): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v2/illust/follow?restrict=${restrict.apiValue}&filter=for_android")
    }

    suspend fun search(
        session: PixivSession,
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
    ): PageResult<Illust> {
        val effectiveWord = listOfNotNull(word, bookmarkFilter.keyword).joinToString(" ")
        val encodedWord = effectiveWord.encodeUrl()
        val durationQuery = duration.apiValue?.let { "&duration=$it" }.orEmpty()
        return getIllustPage(
            session,
            "https://app-api.pixiv.net/v1/search/illust?word=$encodedWord&search_target=${target.apiValue}&sort=${sort.apiValue}$durationQuery&filter=for_android&merge_plain_keyword_results=true&include_translated_tag_results=true${if (includeR18) "&r18=true" else ""}",
        )
    }

    suspend fun searchUsers(session: PixivSession, word: String): PageResult<UserPreview> {
        val encodedWord = word.encodeUrl()
        return getUserPreviewPage(session, "https://app-api.pixiv.net/v1/search/user?word=$encodedWord&filter=for_android")
    }

    suspend fun followingUsers(session: PixivSession, userId: Long, restrict: Restrict): PageResult<UserPreview> {
        return getUserPreviewPage(session, "https://app-api.pixiv.net/v1/user/following?user_id=$userId&restrict=${restrict.apiValue}&filter=for_android")
    }

    suspend fun nextUserPreviewPage(session: PixivSession, nextUrl: String): PageResult<UserPreview> {
        return getUserPreviewPage(session, nextUrl)
    }

    private suspend fun getUserPreviewPage(session: PixivSession, url: String): PageResult<UserPreview> {
        val body = Request.Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            PageResult(
                items = root["user_previews"].asArrayOrEmpty().mapNotNull { it.asObjectOrNull()?.toUserPreviewOrNull() },
                nextUrl = root.string("next_url"),
            )
        }
    }

    suspend fun userDetail(session: PixivSession, userId: Long): UserProfile {
        val body = Request.Builder()
            .url("https://app-api.pixiv.net/v1/user/detail?user_id=$userId&filter=for_android")
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            val user = root["user"].asObjectOrNull() ?: JsonObject(emptyMap())
            val profile = root["profile"].asObjectOrNull()
            val imageUrls = user["profile_image_urls"].asObjectOrNull()
            UserProfile(
                id = user.long("id") ?: userId,
                name = user.string("name").orEmpty(),
                account = user.string("account").orEmpty(),
                profileImageUrl = imageUrls?.string("medium"),
                backgroundImageUrl = profile?.string("background_image_url"),
                comment = profile?.string("comment").orEmpty(),
                isFollowed = user["is_followed"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }

    suspend fun illustDetail(session: PixivSession, illustId: Long): Illust {
        val body = Request.Builder()
            .url("https://app-api.pixiv.net/v1/illust/detail?illust_id=$illustId&filter=for_android")
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            root["illust"].asObjectOrNull()?.toIllustOrNull()
                ?: throw PixivApiException(200, "作品情報を読み込めませんでした。")
        }
    }

    suspend fun relatedIllusts(session: PixivSession, illustId: Long): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v2/illust/related?illust_id=$illustId&filter=for_android")
    }

    suspend fun followUser(session: PixivSession, userId: Long, restrict: Restrict) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/user/follow/add",
            body = FormBody.Builder()
                .add("user_id", userId.toString())
                .add("restrict", restrict.apiValue)
                .build(),
        )
    }

    suspend fun unfollowUser(session: PixivSession, userId: Long) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/user/follow/delete",
            body = FormBody.Builder()
                .add("user_id", userId.toString())
                .build(),
        )
    }

    suspend fun userIllusts(session: PixivSession, userId: Long): PageResult<Illust> {
        return getIllustPage(session, "https://app-api.pixiv.net/v1/user/illusts?user_id=$userId&type=illust&filter=for_android")
    }

    suspend fun bookmarks(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
    ): PageResult<Illust> {
        return getIllustPage(
            session,
            "https://app-api.pixiv.net/v1/user/bookmarks/illust?user_id=$userId&restrict=${restrict.apiValue}&filter=for_android",
        )
    }

    suspend fun nextIllustPage(session: PixivSession, nextUrl: String): PageResult<Illust> {
        return getIllustPage(session, nextUrl)
    }

    suspend fun addBookmark(session: PixivSession, illustId: Long, restrict: Restrict) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v2/illust/bookmark/add",
            body = FormBody.Builder()
                .add("illust_id", illustId.toString())
                .add("restrict", restrict.apiValue)
                .build(),
        )
    }

    suspend fun removeBookmark(session: PixivSession, illustId: Long) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/illust/bookmark/delete",
            body = FormBody.Builder()
                .add("illust_id", illustId.toString())
                .build(),
        )
    }

    private suspend fun getIllustPage(session: PixivSession, url: String): PageResult<Illust> {
        val body = Request.Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            val illusts = root["illusts"].asArrayOrEmpty()
            PageResult(
                items = illusts.mapNotNull { it.asObjectOrNull()?.toIllustOrNull() },
                nextUrl = root.string("next_url"),
            )
        }
    }

    private suspend fun postAuthedForm(session: PixivSession, url: String, body: FormBody) {
        Request.Builder()
            .url(url)
            .pixivApiHeaders(session)
            .post(body)
            .build()
            .let { httpClient.newCall(it).awaitBody() }
    }

    private fun String.encodeUrl(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
