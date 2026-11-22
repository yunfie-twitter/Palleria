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
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.RelatedUsersResult
import com.yunfie.illustia.models.pixiv.SpotlightResult
import com.yunfie.illustia.models.pixiv.TrendingTag
import com.yunfie.illustia.models.pixiv.UserFollowDetail
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.models.pixiv.UserWorkspace
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate

private const val APP_API_HOST = "app-api.pixiv.net"

/**
 * Pixiv API クライアント (KMP / Ktor 版)。
 *
 * 以前は Rust の UniFFI クライアントを使っていたが、全処理を Kotlin へ移した。
 * URL の組み立ては [pixivApiUrl] に集約し、リクエスト送信は [PixivHttpClient] に任せる。
 */
class PixivApiClient(
    httpClient: HttpClient,
    signer: PixivRequestSigner = defaultSigner(),
) {
    private val httpClient = PixivHttpClient(httpClient, signer)

    suspend fun loginWithRefreshToken(refreshToken: String): PixivSession {
        val token = refreshToken.trim()
        require(token.isNotEmpty()) { "refresh token を入力してください。" }
        return httpClient.loginWithRefreshToken(token)
    }

    suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession = httpClient.loginWithAuthorizationCode(code, codeVerifier)

    fun createWebLoginUrl(
        createProvisionalAccount: Boolean = false,
        codeChallenge: String,
    ): String = httpClient.createWebLoginUrl(createProvisionalAccount, codeChallenge)

    suspend fun recommended(session: PixivSession): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl("v1/illust/recommended", "filter" to "for_android", "include_ranking_label" to "true"),
        )

    suspend fun ranking(
        session: PixivSession,
        mode: String = "day",
        date: LocalDate? = null,
    ): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl(
                "v1/illust/ranking",
                "filter" to "for_android",
                "mode" to mode,
                "date" to date?.let { pixivDate(it) },
            ),
        )

    suspend fun newest(session: PixivSession): PageResult<Illust> =
        getIllustPage(session, pixivApiUrl("v1/illust/new", "content_type" to "illust", "filter" to "for_android"))

    suspend fun following(
        session: PixivSession,
        restrict: Restrict,
    ): PageResult<Illust> =
        getIllustPage(session, pixivApiUrl("v2/illust/follow", "restrict" to restrict.apiValue, "filter" to "for_android"))

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
        type: String? = null,
    ): PageResult<Illust> {
        val effectiveWord = listOfNotNull(word, bookmarkFilter.keyword).joinToString(" ")
        val url =
            pixivApiUrl(
                "v1/search/illust",
                "word" to effectiveWord,
                "search_target" to target.apiValue,
                "sort" to sort.apiValue,
                "duration" to duration.apiValue,
                "filter" to if (includeR18) "for_android" else "for_ios",
                "merge_plain_keyword_results" to "true",
                "include_translated_tag_results" to "true",
                "search_ai_type" to searchAiType?.toString(),
                "bookmark_num_min" to bookmarkNum?.first?.toString(),
                "bookmark_num_max" to bookmarkNum?.second?.toString(),
                "start_date" to startDate?.let { pixivDate(it) },
                "end_date" to endDate?.let { pixivDate(it) },
                "r18" to if (includeR18) "true" else null,
                "type" to type,
            )
        if (sort == SearchSort.PopularDesc && effectiveWord.isNotBlank()) {
            return runCatching { getIllustPage(session, url) }.getOrElse { error ->
                if (error.isPixivAuthExpired() || error.isPixivRateLimited()) throw error
                popularPreview(session, effectiveWord, target, includeR18)
            }
        }
        return getIllustPage(session, url)
    }

    suspend fun searchNovels(
        session: PixivSession,
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
    ): PageResult<NovelPreview> {
        val effectiveWord = listOfNotNull(word, bookmarkFilter.keyword).joinToString(" ")
        return getNovelPage(
            session,
            pixivApiUrl(
                "v1/search/novel",
                "word" to effectiveWord,
                "search_target" to target.apiValue,
                "sort" to sort.apiValue,
                "duration" to duration.apiValue,
                "filter" to if (includeR18) "for_android" else "for_ios",
                "merge_plain_keyword_results" to "true",
                "include_translated_tag_results" to "true",
                "r18" to if (includeR18) "true" else null,
            ),
        )
    }

    suspend fun searchAutocomplete(
        session: PixivSession,
        word: String,
    ): List<String> =
        getCall(session, pixivApiUrl("v2/search/autocomplete", "word" to word, "merge_plain_keyword_results" to "true"))
            .awaitAutocomplete()

    suspend fun watchlistManga(session: PixivSession): WatchlistMangaModel =
        getCall(session, pixivApiUrl("v1/watchlist/manga")).awaitWatchlistManga()

    suspend fun nextWatchlistMangaPage(
        session: PixivSession,
        nextUrl: String,
    ): WatchlistMangaModel = getCall(session, nextUrl).awaitWatchlistManga()

    suspend fun searchUsers(
        session: PixivSession,
        word: String,
    ): PageResult<UserPreview> = getUserPreviewPage(session, pixivApiUrl("v1/search/user", "word" to word, "filter" to "for_android"))

    suspend fun followingUsers(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
    ): PageResult<UserPreview> =
        getUserPreviewPage(
            session,
            pixivApiUrl(
                "v1/user/following",
                "user_id" to userId.toString(),
                "restrict" to restrict.apiValue,
                "filter" to "for_android",
            ),
        )

    suspend fun nextUserPreviewPage(
        session: PixivSession,
        nextUrl: String,
    ): PageResult<UserPreview> = getUserPreviewPage(session, nextUrl)

    private suspend fun getUserPreviewPage(
        session: PixivSession,
        url: String,
    ): PageResult<UserPreview> = getCall(session, url).awaitUserPreviewPage()

    suspend fun userDetail(
        session: PixivSession,
        userId: Long,
    ): UserProfile = getCall(session, pixivApiUrl("v1/user/detail", "user_id" to userId.toString(), "filter" to "for_android")).awaitUserProfile(userId)

    suspend fun illustDetail(
        session: PixivSession,
        illustId: Long,
    ): Illust = getCall(session, pixivApiUrl("v1/illust/detail", "illust_id" to illustId.toString())).awaitIllustDetail()

    suspend fun ugoiraMetadata(
        session: PixivSession,
        illustId: Long,
    ) = getCall(session, pixivApiUrl("v1/ugoira/metadata", "illust_id" to illustId.toString())).awaitUgoiraMetadata()

    suspend fun relatedIllusts(
        session: PixivSession,
        illustId: Long,
    ): PageResult<Illust> = getIllustPage(session, pixivApiUrl("v2/illust/related", "illust_id" to illustId.toString(), "filter" to "for_android"))

    suspend fun illustSeries(
        session: PixivSession,
        seriesId: Long,
    ): IllustSeriesWithIdModel = getCall(session, pixivApiUrl("v2/illust/manga", "illust_series_id" to seriesId.toString(), "filter" to "for_android")).awaitIllustSeries()

    suspend fun nextIllustSeriesPage(
        session: PixivSession,
        nextUrl: String,
    ): IllustSeriesWithIdModel = getCall(session, nextUrl).awaitIllustSeries()

    suspend fun followUser(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
    ) {
        postAuthedForm(
            session,
            "https://$APP_API_HOST/v1/user/follow/add",
            listOf("user_id" to userId.toString(), "restrict" to restrict.apiValue),
        )
    }

    suspend fun unfollowUser(
        session: PixivSession,
        userId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/user/follow/delete", listOf("user_id" to userId.toString()))
    }

    suspend fun userIllusts(
        session: PixivSession,
        userId: Long,
        type: String = "illust",
        offset: Int? = null,
    ): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl(
                "v1/user/illusts",
                "user_id" to userId.toString(),
                "type" to type,
                "filter" to "for_android",
                "offset" to offset?.toString(),
            ),
        )

    suspend fun bookmarks(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
        tag: String? = null,
        offset: Int? = null,
    ): PageResult<Illust> =
        getIllustPage(
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

    suspend fun illustBookmarkTags(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
    ): List<BookmarkTag> =
        getCall(
            session,
            pixivApiUrl("v1/user/bookmark-tags/illust", "user_id" to userId.toString(), "restrict" to restrict.apiValue),
        ).awaitBookmarkTags()

    suspend fun nextIllustPage(
        session: PixivSession,
        nextUrl: String,
    ): PageResult<Illust> = getIllustPage(session, nextUrl)

    suspend fun addBookmark(
        session: PixivSession,
        illustId: Long,
        restrict: Restrict,
        tags: List<String>? = null,
    ) {
        require(tags == null || tags.size <= 10) { "ブックマークタグは10件まで指定できます。" }
        val fields = mutableListOf("illust_id" to illustId.toString(), "restrict" to restrict.apiValue)
        if (!tags.isNullOrEmpty()) {
            fields += "tags[]" to tags.joinToString(" ") { it.trim() }
        }
        postAuthedForm(session, "https://$APP_API_HOST/v2/illust/bookmark/add", fields)
    }

    suspend fun removeBookmark(
        session: PixivSession,
        illustId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/illust/bookmark/delete", listOf("illust_id" to illustId.toString()))
    }

    suspend fun trendingTags(session: PixivSession): List<String> = trendingTagDetails(session).map { it.tag }

    suspend fun userFollowDetail(
        session: PixivSession,
        userId: Long,
    ): UserFollowDetail = getCall(session, pixivApiUrl("v1/user/follow/detail", "user_id" to userId.toString())).awaitUserFollowDetail()

    suspend fun reportIllust(
        session: PixivSession,
        illustId: Long,
        problemType: String?,
        message: String?,
    ) {
        val fields = mutableListOf("illust_id" to illustId.toString())
        problemType?.let { fields += "type_of_problem" to it }
        message?.let { fields += "message" to it }
        postAuthedForm(session, "https://$APP_API_HOST/v1/illust/report", fields)
    }

    suspend fun reportUser(
        session: PixivSession,
        userId: Long,
        problemType: String?,
        message: String?,
    ) {
        val fields = mutableListOf("user_id" to userId.toString())
        problemType?.let { fields += "type_of_problem" to it }
        message?.let { fields += "message" to it }
        postAuthedForm(session, "https://$APP_API_HOST/v1/user/report", fields)
    }

    suspend fun addNovelBookmark(
        session: PixivSession,
        novelId: Long,
        restrict: Restrict,
    ) {
        postAuthedForm(
            session,
            "https://$APP_API_HOST/v2/novel/bookmark/add",
            listOf("novel_id" to novelId.toString(), "restrict" to restrict.apiValue),
        )
    }

    suspend fun removeNovelBookmark(
        session: PixivSession,
        novelId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/novel/bookmark/delete", listOf("novel_id" to novelId.toString()))
    }

    suspend fun addNovelMarker(
        session: PixivSession,
        novelId: Long,
        page: Int,
    ) {
        require(page >= 1) { "小説のしおりページは1以上で指定してください。" }
        postAuthedForm(
            session,
            "https://$APP_API_HOST/v1/novel/marker/add",
            listOf("novel_id" to novelId.toString(), "page" to page.toString()),
        )
    }

    suspend fun removeNovelMarker(
        session: PixivSession,
        novelId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/novel/marker/delete", listOf("novel_id" to novelId.toString()))
    }

    suspend fun notifications(session: PixivSession): NotificationListResult = getNotificationPage(session, pixivApiUrl("v1/notification/list"))

    suspend fun notificationViewMore(
        session: PixivSession,
        notificationId: Long,
    ): NotificationListResult = getNotificationPage(session, pixivApiUrl("v1/notification/view-more", "notification_id" to notificationId.toString()))

    suspend fun nextNotificationPage(
        session: PixivSession,
        nextUrl: String,
    ): NotificationListResult = getNotificationPage(session, nextUrl)

    private suspend fun getNotificationPage(
        session: PixivSession,
        url: String,
    ): NotificationListResult = getCall(session, url).awaitNotificationPage()

    suspend fun stamps(session: PixivSession): List<PixivStamp> = getCall(session, pixivApiUrl("v1/stamps")).awaitStamps()

    suspend fun trendingTagDetails(session: PixivSession): List<TrendingTag> =
        getCall(session, pixivApiUrl("v1/trending-tags/illust", "filter" to "for_android")).awaitTrendingTags()

    suspend fun spotlightArticles(session: PixivSession): SpotlightResult =
        getSpotlightPage(session, pixivApiUrl("v1/spotlight/articles", "filter" to "for_android"))

    suspend fun nextSpotlightPage(
        session: PixivSession,
        nextUrl: String,
    ): SpotlightResult = getSpotlightPage(session, nextUrl)

    private suspend fun getSpotlightPage(
        session: PixivSession,
        url: String,
    ): SpotlightResult = getCall(session, url).awaitSpotlight()

    suspend fun illustComments(
        session: PixivSession,
        illustId: Long,
        offset: Int? = null,
    ): CommentResponse = getCall(session, pixivApiUrl("v3/illust/comments", "illust_id" to illustId.toString(), "offset" to offset?.toString())).awaitCommentPage()

    suspend fun illustCommentReplies(
        session: PixivSession,
        commentId: Long,
        offset: Int? = null,
    ): CommentResponse = getCall(session, pixivApiUrl("v2/illust/comment/replies", "comment_id" to commentId.toString(), "offset" to offset?.toString())).awaitCommentPage()

    suspend fun novelComments(
        session: PixivSession,
        novelId: Long,
    ): CommentResponse = getCall(session, pixivApiUrl("v3/novel/comments", "novel_id" to novelId.toString())).awaitCommentPage()

    suspend fun novelCommentReplies(
        session: PixivSession,
        commentId: Long,
    ): CommentResponse = getCall(session, pixivApiUrl("v2/novel/comment/replies", "comment_id" to commentId.toString())).awaitCommentPage()

    suspend fun nextCommentPage(
        session: PixivSession,
        nextUrl: String,
    ): CommentResponse = getCall(session, nextUrl).awaitCommentPage()

    suspend fun addIllustComment(
        session: PixivSession,
        illustId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        val fields = mutableListOf("illust_id" to illustId.toString(), "comment" to comment)
        parentCommentId?.let { fields += "parent_comment_id" to it.toString() }
        postAuthedForm(session, "https://$APP_API_HOST/v1/illust/comment/add", fields)
    }

    suspend fun currentUserProfile(session: PixivSession): CurrentUserProfile =
        getCall(session, pixivApiUrl("v1/user/me/state")).awaitCurrentUserProfile()

    suspend fun relatedUsers(
        session: PixivSession,
        userId: Long,
    ): RelatedUsersResult = getRelatedUsersPage(session, pixivApiUrl("v1/user/related", "seed_user_id" to userId.toString(), "filter" to "for_android"))

    suspend fun nextRelatedUsersPage(
        session: PixivSession,
        nextUrl: String,
    ): RelatedUsersResult = getRelatedUsersPage(session, nextUrl)

    private suspend fun getRelatedUsersPage(
        session: PixivSession,
        url: String,
    ): RelatedUsersResult =
        getCall(session, url).awaitUserPreviewPage().let { RelatedUsersResult(users = it.items, nextUrl = it.nextUrl) }

    suspend fun setUserWorkspace(
        session: PixivSession,
        workspace: UserWorkspace,
    ) {
        val fields = mutableListOf<Pair<String, String>>()
        mapOf(
            "pc" to workspace.pc,
            "monitor" to workspace.monitor,
            "tool" to workspace.tool,
            "scanner" to workspace.scanner,
            "tablet" to workspace.tablet,
            "mouse" to workspace.mouse,
            "printer" to workspace.printer,
            "desktop" to workspace.desktop,
            "music" to workspace.music,
            "desk" to workspace.desk,
            "chair" to workspace.chair,
            "comment" to workspace.comment,
            "workspace_image_url" to workspace.workspaceImageUrl,
        ).forEach { (key, value) -> value?.let { fields += key to it } }
        postAuthedForm(session, "https://$APP_API_HOST/v1/user/workspace/edit", fields)
    }

    suspend fun setUserProfile(
        session: PixivSession,
        profile: UserProfileEdit,
    ): AccountEditResult {
        val fields =
            buildList {
                add("gender" to profile.gender.lowercase())
                add("address" to profile.address.toString())
                add("job" to profile.job.toString())
                add("user_name" to profile.userName)
                add("webpage" to profile.webpage)
                add("twitter" to profile.twitter)
                add("comment" to profile.comment)
                add("birthday" to profile.birthday)
                profile.country?.let { add("country" to it) }
            }
        return httpClient
            .newCall(
                pixivRequest("https://$APP_API_HOST/v1/user/profile/edit") {
                    addHeaders(pixivApiHeaders(session))
                    post(fields)
                },
            ).awaitAccountEdit()
    }

    suspend fun addIllustStampComment(
        session: PixivSession,
        illustId: Long,
        stampId: Long,
        parentCommentId: Long? = null,
    ) {
        val fields = mutableListOf("illust_id" to illustId.toString(), "stamp_id" to stampId.toString())
        parentCommentId?.let { fields += "parent_comment_id" to it.toString() }
        postAuthedForm(session, "https://$APP_API_HOST/v1/illust/comment/add", fields)
    }

    suspend fun deleteIllustComment(
        session: PixivSession,
        commentId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/illust/comment/delete", listOf("comment_id" to commentId.toString()))
    }

    suspend fun isAiContentVisible(session: PixivSession): Boolean =
        getCall(session, pixivApiUrl("v1/user/ai-show-settings")).awaitOptionalBoolean() ?: false

    suspend fun setAiContentVisible(
        session: PixivSession,
        visible: Boolean,
    ) {
        val saved =
            httpClient
                .newCall(
                    pixivRequest("https://$APP_API_HOST/v1/user/ai-show-settings/edit") {
                        addHeaders(pixivApiHeaders(session))
                        post(listOf("show_ai" to visible.toString()))
                    },
                ).awaitOptionalBoolean()
        check(saved == visible) { "AI作品の表示設定を更新できませんでした。" }
    }

    suspend fun addNovelComment(
        session: PixivSession,
        novelId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        val fields = mutableListOf("novel_id" to novelId.toString(), "comment" to comment)
        parentCommentId?.let { fields += "parent_comment_id" to it.toString() }
        postAuthedForm(session, "https://$APP_API_HOST/v1/novel/comment/add", fields)
    }

    suspend fun addWatchlistManga(
        session: PixivSession,
        seriesId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/watchlist/manga/add", listOf("series_id" to seriesId.toString()))
    }

    suspend fun removeWatchlistManga(
        session: PixivSession,
        seriesId: Long,
    ) {
        postAuthedForm(session, "https://$APP_API_HOST/v1/watchlist/manga/delete", listOf("series_id" to seriesId.toString()))
    }

    suspend fun popularPreview(
        session: PixivSession,
        word: String,
        target: SearchTarget = SearchTarget.PartialTags,
        includeR18: Boolean = true,
    ): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl(
                "v1/search/popular-preview/illust",
                "filter" to if (includeR18) "for_android" else "for_ios",
                "include_translated_tag_results" to "true",
                "merge_plain_keyword_results" to "true",
                "word" to word,
                "search_target" to target.apiValue,
            ),
        )

    suspend fun recommendedNovels(session: PixivSession): PageResult<NovelPreview> =
        getNovelPage(session, pixivApiUrl("v1/novel/recommended", "filter" to "for_android", "include_ranking_novels" to "true"))

    suspend fun nextNovelPage(
        session: PixivSession,
        nextUrl: String,
    ): PageResult<NovelPreview> = getNovelPage(session, nextUrl)

    suspend fun novelText(
        session: PixivSession,
        novelId: Long,
    ): NovelTextContent = getCall(session, pixivApiUrl("webview/v2/novel", "id" to novelId.toString(), "viewer_version" to "20221031_ai")).awaitNovelText(novelId)

    private suspend fun getIllustPage(
        session: PixivSession,
        url: String,
    ): PageResult<Illust> = getCall(session, url).awaitIllustPage()

    private suspend fun getNovelPage(
        session: PixivSession,
        url: String,
    ): PageResult<NovelPreview> = getCall(session, url).awaitNovelPage()

    private suspend fun postAuthedForm(
        session: PixivSession,
        url: String,
        fields: List<Pair<String, String>>,
    ) {
        httpClient
            .newCall(
                pixivRequest(url) {
                    addHeaders(pixivApiHeaders(session))
                    post(fields)
                },
            ).awaitComplete()
    }

    private fun getCall(
        session: PixivSession,
        url: String,
    ): PixivCall = httpClient.newCall(pixivRequest(trustedPixivApiUrl(url)) { addHeaders(pixivApiHeaders(session)) })
}

private fun pixivApiUrl(
    path: String,
    vararg queryParameters: Pair<String, String?>,
): String {
    val query = queryParameters.mapNotNull { (name, value) -> value?.let { "${name.urlEncode()}=${it.urlEncode()}" } }
    val base = "https://$APP_API_HOST/$path"
    return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
}

private fun pixivDate(date: LocalDate): String = "${date.year}-${date.monthNumber}-${date.dayOfMonth}"

private fun String.urlEncode(): String =
    buildString {
        for (byte in this@urlEncode.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            if (char.isLetterOrDigit() || char in "-._~") append(char) else append('%').append(HEX_UPPER[value shr 4]).append(HEX_UPPER[value and 0xF])
        }
    }

private const val HEX_UPPER = "0123456789ABCDEF"

