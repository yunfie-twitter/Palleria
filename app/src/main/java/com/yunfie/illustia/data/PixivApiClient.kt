package com.yunfie.illustia.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/illust/recommended",
                "filter" to "for_ios",
                "include_ranking_label" to "true",
            ),
        )
    }

    suspend fun ranking(
        session: PixivSession,
        mode: String = "day",
        date: LocalDate? = null,
    ): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/illust/ranking",
                "filter" to "for_android",
                "mode" to mode,
                "date" to date?.let { pixivDate(it) },
            ),
        )
    }

    suspend fun newest(session: PixivSession): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl("v1/illust/new", "content_type" to "illust", "filter" to "for_android"),
        )
    }

    suspend fun following(session: PixivSession, restrict: Restrict): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl("v2/illust/follow", "restrict" to restrict.apiValue),
        )
    }

    suspend fun search(
        session: PixivSession,
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        searchAiType: Int? = null,
        bookmarkNum: Pair<Int, Int>? = null,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): PageResult<Illust> {
        val effectiveWord = listOfNotNull(word, bookmarkFilter.keyword).joinToString(" ")
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/search/illust",
                "word" to effectiveWord,
                "search_target" to target.apiValue,
                "sort" to sort.apiValue,
                "duration" to duration.apiValue,
                "filter" to "for_android",
                "merge_plain_keyword_results" to "true",
                "include_translated_tag_results" to "true",
                "search_ai_type" to searchAiType?.toString(),
                "bookmark_num_min" to bookmarkNum?.first?.toString(),
                "bookmark_num_max" to bookmarkNum?.second?.toString(),
                "start_date" to startDate?.let { pixivDate(it) },
                "end_date" to endDate?.let { pixivDate(it) },
                "r18" to if (includeR18) "true" else null,
            ),
        )
    }

    suspend fun searchAutocomplete(session: PixivSession, word: String): List<String> {
        val body = Request.Builder()
            .url(
                pixivApiUrl(
                    "v2/search/autocomplete",
                    "word" to word,
                    "merge_plain_keyword_results" to "true",
                ),
            )
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            root["tags"].asArrayOrEmpty()
                .mapNotNull { it.asObjectOrNull()?.string("name") }
        }
    }

    suspend fun searchUsers(session: PixivSession, word: String): PageResult<UserPreview> {
        return getUserPreviewPage(
            session,
            pixivApiUrl("v1/search/user", "word" to word, "filter" to "for_android"),
        )
    }

    suspend fun followingUsers(session: PixivSession, userId: Long, restrict: Restrict): PageResult<UserPreview> {
        return getUserPreviewPage(
            session,
            pixivApiUrl(
                "v1/user/following",
                "user_id" to userId.toString(),
                "restrict" to restrict.apiValue,
                "filter" to "for_android",
            ),
        )
    }

    suspend fun nextUserPreviewPage(session: PixivSession, nextUrl: String): PageResult<UserPreview> {
        return getUserPreviewPage(session, nextUrl.toHttpUrl())
    }

    private suspend fun getUserPreviewPage(session: PixivSession, url: HttpUrl): PageResult<UserPreview> {
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
            .url(pixivApiUrl("v1/user/detail", "user_id" to userId.toString(), "filter" to "for_android"))
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
            .url(pixivApiUrl("v1/illust/detail", "illust_id" to illustId.toString(), "filter" to "for_android"))
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
        return getIllustPage(
            session,
            pixivApiUrl("v2/illust/related", "illust_id" to illustId.toString(), "filter" to "for_android"),
        )
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

    suspend fun userIllusts(
        session: PixivSession,
        userId: Long,
        type: String = "illust",
        offset: Int? = null,
    ): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/user/illusts",
                "user_id" to userId.toString(),
                "type" to type,
                "filter" to "for_android",
                "offset" to offset?.toString(),
            ),
        )
    }

    suspend fun bookmarks(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
        tag: String? = null,
        offset: Int? = null,
    ): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/user/bookmarks/illust",
                "user_id" to userId.toString(),
                "restrict" to restrict.apiValue,
                "tag" to tag,
                "offset" to offset?.toString(),
                "filter" to "for_android",
            ),
        )
    }

    suspend fun nextIllustPage(session: PixivSession, nextUrl: String): PageResult<Illust> {
        return getIllustPage(session, nextUrl.toHttpUrl())
    }

    suspend fun addBookmark(
        session: PixivSession,
        illustId: Long,
        restrict: Restrict,
        tags: List<String>? = null,
    ) {
        val form = FormBody.Builder()
            .add("illust_id", illustId.toString())
            .add("restrict", restrict.apiValue)
        if (!tags.isNullOrEmpty()) {
            form.add("tags[]", tags.joinToString(" ") { it.trim() })
        }
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v2/illust/bookmark/add",
            body = form.build(),
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

    suspend fun trendingTags(session: PixivSession): List<String> {
        val body = Request.Builder()
            .url(pixivApiUrl("v1/trending-tags/illust", "filter" to "for_android"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            root["trend_tags"].asArrayOrEmpty()
                .mapNotNull { it.asObjectOrNull()?.string("tag") }
        }
    }

    suspend fun popularPreview(session: PixivSession, word: String): PageResult<Illust> {
        return getIllustPage(
            session,
            pixivApiUrl(
                "v1/search/popular-preview/illust",
                "filter" to "for_android",
                "include_translated_tag_results" to "true",
                "merge_plain_keyword_results" to "true",
                "word" to word,
                "search_target" to "partial_match_for_tags",
            ),
        )
    }

    private suspend fun getIllustPage(session: PixivSession, url: HttpUrl): PageResult<Illust> {
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

    private fun pixivApiUrl(path: String, vararg queryParameters: Pair<String, String?>): HttpUrl {
        return HttpUrl.Builder()
            .scheme("https")
            .host("app-api.pixiv.net")
            .addPathSegments(path)
            .apply {
                queryParameters.forEach { (name, value) ->
                    value?.let { addQueryParameter(name, it) }
                }
            }
            .build()
    }

    private fun pixivDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("yyyy-M-d"))
}
