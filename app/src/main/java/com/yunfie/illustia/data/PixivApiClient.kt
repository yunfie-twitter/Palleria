package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
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

    fun createWebLoginUrl(createProvisionalAccount: Boolean = false, codeChallenge: String): String {
        val path = if (createProvisionalAccount) {
            "web/v1/provisional-accounts/create"
        } else {
            "web/v1/login"
        }
        return HttpUrl.Builder()
            .scheme("https")
            .host("app-api.pixiv.net")
            .addPathSegments(path)
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("client", "pixiv-android")
            .build()
            .toString()
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

    suspend fun watchlistManga(session: PixivSession): WatchlistMangaModel {
        val body = Request.Builder()
            .url(pixivApiUrl("v1/watchlist/manga"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toWatchlistMangaModelOrNull()
        }
    }

    suspend fun nextWatchlistMangaPage(session: PixivSession, nextUrl: String): WatchlistMangaModel {
        val body = Request.Builder()
            .url(nextUrl.toHttpUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toWatchlistMangaModelOrNull()
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

    suspend fun illustSeries(session: PixivSession, illustSeriesId: Long): IllustSeriesWithIdModel {
        val body = Request.Builder()
            .url(pixivApiUrl("v1/illust/series", "filter" to "for_ios", "illust_series_id" to illustSeriesId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toIllustSeriesWithIdModelOrNull()
        }
    }

    suspend fun nextIllustSeriesPage(session: PixivSession, nextUrl: String): IllustSeriesWithIdModel {
        val body = Request.Builder()
            .url(nextUrl.toHttpUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toIllustSeriesWithIdModelOrNull()
        }
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

    suspend fun illustComments(session: PixivSession, illustId: Long): CommentResponse {
        val body = Request.Builder()
            .url(pixivApiUrl("v3/illust/comments", "illust_id" to illustId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toCommentResponseOrNull()
        }
    }

    suspend fun illustCommentReplies(session: PixivSession, commentId: Long): CommentResponse {
        val body = Request.Builder()
            .url(pixivApiUrl("v2/illust/comment/replies", "comment_id" to commentId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toCommentResponseOrNull()
        }
    }

    suspend fun novelComments(session: PixivSession, novelId: Long): CommentResponse {
        val body = Request.Builder()
            .url(pixivApiUrl("v3/novel/comments", "novel_id" to novelId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toCommentResponseOrNull()
        }
    }

    suspend fun novelCommentReplies(session: PixivSession, commentId: Long): CommentResponse {
        val body = Request.Builder()
            .url(pixivApiUrl("v2/novel/comment/replies", "comment_id" to commentId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toCommentResponseOrNull()
        }
    }

    suspend fun nextCommentPage(session: PixivSession, nextUrl: String): CommentResponse {
        val body = Request.Builder()
            .url(nextUrl.toHttpUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            json.parseToJsonElement(body).jsonObject.toCommentResponseOrNull()
        }
    }

    suspend fun addIllustComment(session: PixivSession, illustId: Long, comment: String, parentCommentId: Long? = null) {
        val form = FormBody.Builder()
            .add("illust_id", illustId.toString())
            .add("comment", comment)
        parentCommentId?.let { form.add("parent_comment_id", it.toString()) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/illust/comment/add", form.build())
    }

    suspend fun addNovelComment(session: PixivSession, novelId: Long, comment: String, parentCommentId: Long? = null) {
        val form = FormBody.Builder()
            .add("novel_id", novelId.toString())
            .add("comment", comment)
        parentCommentId?.let { form.add("parent_comment_id", it.toString()) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/novel/comment/add", form.build())
    }

    suspend fun addWatchlistManga(session: PixivSession, seriesId: Long) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/watchlist/manga/add",
            body = FormBody.Builder().add("series_id", seriesId.toString()).build(),
        )
    }

    suspend fun removeWatchlistManga(session: PixivSession, seriesId: Long) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/watchlist/manga/delete",
            body = FormBody.Builder().add("series_id", seriesId.toString()).build(),
        )
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

    suspend fun recommendedNovels(session: PixivSession): PageResult<NovelPreview> {
        return getNovelPage(
            session = session,
            url = pixivApiUrl("v1/novel/recommended", "filter" to "for_android", "include_ranking_novels" to "true"),
        )
    }

    suspend fun nextNovelPage(session: PixivSession, nextUrl: String): PageResult<NovelPreview> {
        return getNovelPage(session, nextUrl.toHttpUrl())
    }

    suspend fun novelText(session: PixivSession, novelId: Long): NovelTextContent {
        val body = Request.Builder()
            .url(
                pixivApiUrl(
                    "webview/v2/novel",
                    "id" to novelId.toString(),
                    "viewer_version" to "20221031_ai",
                ),
            )
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = parseNovelWebviewBody(body)
            val prev = root["seriesNavigation"].asObjectOrNull()?.get("prevNovel")?.asObjectOrNull()
            val next = root["seriesNavigation"].asObjectOrNull()?.get("nextNovel")?.asObjectOrNull()
            NovelTextContent(
                novelId = novelId,
                title = root.string("title").orEmpty(),
                text = root.string("text")?.ifBlank { root.string("novel_text").orEmpty() } ?: root.string("novel_text").orEmpty(),
                seriesPrevId = prev?.long("id"),
                seriesPrevTitle = prev?.string("title"),
                seriesNextId = next?.long("id"),
                seriesNextTitle = next?.string("title"),
            )
        }
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

    private suspend fun getNovelPage(session: PixivSession, url: HttpUrl): PageResult<NovelPreview> {
        val body = Request.Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitBody() }

        return withContext(Dispatchers.Default) {
            val root = json.parseToJsonElement(body).jsonObject
            val novels = root["novels"].asArrayOrEmpty().mapNotNull { element ->
                val item = element.asObjectOrNull() ?: return@mapNotNull null
                val user = item["user"].asObjectOrNull()
                val images = item["image_urls"].asObjectOrNull()
                NovelPreview(
                    id = item.long("id") ?: return@mapNotNull null,
                    title = item.string("title").orEmpty(),
                    caption = item.string("caption").orEmpty(),
                    userId = user?.long("id") ?: 0L,
                    userName = user?.string("name").orEmpty(),
                    userAccount = user?.string("account").orEmpty(),
                    coverUrl = images?.string("medium").orEmpty(),
                    pageCount = item.int("page_count") ?: 0,
                    textLength = item.int("text_length") ?: 0,
                    isBookmarked = item.boolean("is_bookmarked") ?: false,
                    totalBookmarks = item.int("total_bookmarks") ?: 0,
                    totalView = item.int("total_view") ?: 0,
                )
            }
            PageResult(items = novels, nextUrl = root.string("next_url"))
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

    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    private fun parseNovelWebviewBody(body: String): JsonObject {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            return json.parseToJsonElement(trimmed).jsonObject
        }

        val match = NOVEL_WEBVIEW_PATTERN.matcher(body)
        if (!match.find() || match.groupCount() < 1) {
            val preview = body.lineSequence().take(8).joinToString(" ").take(320)
            throw PixivApiException(200, "小説レスポンスを解析できませんでした: $preview")
        }
        return json.parseToJsonElement(match.group(1)!!).jsonObject
    }

    private companion object {
        val NOVEL_WEBVIEW_PATTERN: Pattern =
            Pattern.compile("novel:\\s*(\\{.+?\\}),\\s*isOwnWork", Pattern.DOTALL)
    }
}
