package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NetworkMode
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
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.RelatedUsersResult
import com.yunfie.illustia.models.pixiv.SpotlightResult
import com.yunfie.illustia.models.pixiv.TrendingTag
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UserFollowDetail
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.models.pixiv.UserWorkspace
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PixivApiClient(
    private val mode: NetworkMode = NetworkMode.Standard,
) {
    private val httpClient = RustPixivHttpClient(mode)

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

    suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback = httpClient.prepareUgoira(url, cacheDir, frames)

    suspend fun recommended(session: PixivSession): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl(
                "v1/illust/recommended",
                "filter" to "for_ios",
                "include_ranking_label" to "true",
            ),
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
        getIllustPage(
            session,
            pixivApiUrl("v1/illust/new", "content_type" to "illust", "filter" to "for_android"),
        )

    suspend fun following(
        session: PixivSession,
        restrict: Restrict,
    ): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl("v2/illust/follow", "restrict" to restrict.apiValue),
        )

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
                "filter" to if (includeR18) "for_android" else "for_ios",
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
            session = session,
            url =
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
        Request
            .Builder()
            .url(
                pixivApiUrl(
                    "v2/search/autocomplete",
                    "word" to word,
                    "merge_plain_keyword_results" to "true",
                ),
            ).pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitAutocomplete() }

    suspend fun watchlistManga(session: PixivSession): WatchlistMangaModel =
        Request
            .Builder()
            .url(pixivApiUrl("v1/watchlist/manga"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitWatchlistManga() }

    suspend fun nextWatchlistMangaPage(
        session: PixivSession,
        nextUrl: String,
    ): WatchlistMangaModel =
        Request
            .Builder()
            .url(nextUrl.toTrustedPixivApiUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitWatchlistManga() }

    suspend fun searchUsers(
        session: PixivSession,
        word: String,
    ): PageResult<UserPreview> =
        getUserPreviewPage(
            session,
            pixivApiUrl("v1/search/user", "word" to word, "filter" to "for_android"),
        )

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
    ): PageResult<UserPreview> = getUserPreviewPage(session, nextUrl.toTrustedPixivApiUrl())

    private suspend fun getUserPreviewPage(
        session: PixivSession,
        url: HttpUrl,
    ): PageResult<UserPreview> =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitUserPreviewPage() }

    suspend fun userDetail(
        session: PixivSession,
        userId: Long,
    ): UserProfile =
        Request
            .Builder()
            .url(pixivApiUrl("v1/user/detail", "user_id" to userId.toString(), "filter" to "for_android"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitUserProfile(userId) }

    suspend fun illustDetail(
        session: PixivSession,
        illustId: Long,
    ): Illust =
        Request
            .Builder()
            .url(pixivApiUrl("v1/illust/detail", "illust_id" to illustId.toString(), "filter" to "for_android"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitIllustDetail() }

    suspend fun ugoiraMetadata(
        session: PixivSession,
        illustId: Long,
    ): UgoiraMetadataResponse =
        Request
            .Builder()
            .url(pixivApiUrl("v1/ugoira/metadata", "illust_id" to illustId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitUgoiraMetadata() }

    suspend fun relatedIllusts(
        session: PixivSession,
        illustId: Long,
    ): PageResult<Illust> =
        getIllustPage(
            session,
            pixivApiUrl("v2/illust/related", "illust_id" to illustId.toString(), "filter" to "for_android"),
        )

    suspend fun illustSeries(
        session: PixivSession,
        illustSeriesId: Long,
    ): IllustSeriesWithIdModel =
        Request
            .Builder()
            .url(pixivApiUrl("v1/illust/series", "filter" to "for_ios", "illust_series_id" to illustSeriesId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitIllustSeries() }

    suspend fun nextIllustSeriesPage(
        session: PixivSession,
        nextUrl: String,
    ): IllustSeriesWithIdModel =
        Request
            .Builder()
            .url(nextUrl.toTrustedPixivApiUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitIllustSeries() }

    suspend fun followUser(
        session: PixivSession,
        userId: Long,
        restrict: Restrict,
    ) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/user/follow/add",
            body =
                FormBody
                    .Builder()
                    .add("user_id", userId.toString())
                    .add("restrict", restrict.apiValue)
                    .build(),
        )
    }

    suspend fun unfollowUser(
        session: PixivSession,
        userId: Long,
    ) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/user/follow/delete",
            body =
                FormBody
                    .Builder()
                    .add("user_id", userId.toString())
                    .build(),
        )
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

    suspend fun nextIllustPage(
        session: PixivSession,
        nextUrl: String,
    ): PageResult<Illust> = getIllustPage(session, nextUrl.toTrustedPixivApiUrl())

    suspend fun addBookmark(
        session: PixivSession,
        illustId: Long,
        restrict: Restrict,
        tags: List<String>? = null,
    ) {
        require(tags == null || tags.size <= 10) { "ブックマークタグは10件まで指定できます。" }
        val form =
            FormBody
                .Builder()
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

    suspend fun removeBookmark(
        session: PixivSession,
        illustId: Long,
    ) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/illust/bookmark/delete",
            body =
                FormBody
                    .Builder()
                    .add("illust_id", illustId.toString())
                    .build(),
        )
    }

    suspend fun trendingTags(session: PixivSession): List<String> = trendingTagDetails(session).map { it.tag }

    suspend fun userFollowDetail(
        session: PixivSession,
        userId: Long,
    ): UserFollowDetail =
        Request
            .Builder()
            .url(pixivApiUrl("v1/user/follow/detail", "user_id" to userId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitUserFollowDetail() }

    fun createWebSocket(
        session: PixivSession,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): PixivWebSocketClient = PixivWebSocketClient(createPixivHttpClient(mode), url, { session.accessToken }, headers)

    suspend fun reportIllust(
        session: PixivSession,
        illustId: Long,
        problemType: String?,
        message: String?,
    ) {
        val form = FormBody.Builder().add("illust_id", illustId.toString())
        problemType?.let { form.add("type_of_problem", it) }
        message?.let { form.add("message", it) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/illust/report", form.build())
    }

    suspend fun reportUser(
        session: PixivSession,
        userId: Long,
        problemType: String?,
        message: String?,
    ) {
        val form = FormBody.Builder().add("user_id", userId.toString())
        problemType?.let { form.add("type_of_problem", it) }
        message?.let { form.add("message", it) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/user/report", form.build())
    }

    suspend fun addNovelBookmark(
        session: PixivSession,
        novelId: Long,
        restrict: Restrict,
    ) {
        postAuthedForm(
            session,
            "https://app-api.pixiv.net/v2/novel/bookmark/add",
            FormBody
                .Builder()
                .add("novel_id", novelId.toString())
                .add("restrict", restrict.apiValue)
                .build(),
        )
    }

    suspend fun removeNovelBookmark(
        session: PixivSession,
        novelId: Long,
    ) {
        postAuthedForm(
            session,
            "https://app-api.pixiv.net/v1/novel/bookmark/delete",
            FormBody.Builder().add("novel_id", novelId.toString()).build(),
        )
    }

    suspend fun addNovelMarker(
        session: PixivSession,
        novelId: Long,
        page: Int,
    ) {
        require(page >= 1) { "小説のしおりページは1以上で指定してください。" }
        postAuthedForm(
            session,
            "https://app-api.pixiv.net/v1/novel/marker/add",
            FormBody
                .Builder()
                .add("novel_id", novelId.toString())
                .add("page", page.toString())
                .build(),
        )
    }

    suspend fun removeNovelMarker(
        session: PixivSession,
        novelId: Long,
    ) {
        postAuthedForm(
            session,
            "https://app-api.pixiv.net/v1/novel/marker/delete",
            FormBody.Builder().add("novel_id", novelId.toString()).build(),
        )
    }

    suspend fun notifications(session: PixivSession): NotificationListResult =
        getNotificationPage(session, pixivApiUrl("v1/notification/list"))

    suspend fun notificationViewMore(
        session: PixivSession,
        notificationId: Long,
    ): NotificationListResult =
        getNotificationPage(session, pixivApiUrl("v1/notification/view-more", "notification_id" to notificationId.toString()))

    suspend fun nextNotificationPage(
        session: PixivSession,
        nextUrl: String,
    ): NotificationListResult = getNotificationPage(session, nextUrl.toTrustedPixivApiUrl())

    private suspend fun getNotificationPage(
        session: PixivSession,
        url: HttpUrl,
    ): NotificationListResult =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitNotificationPage() }

    suspend fun stamps(session: PixivSession): List<PixivStamp> =
        Request
            .Builder()
            .url(pixivApiUrl("v1/stamps"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitStamps() }

    suspend fun trendingTagDetails(session: PixivSession): List<TrendingTag> =
        Request
            .Builder()
            .url(pixivApiUrl("v1/trending-tags/illust", "filter" to "for_android"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitTrendingTags() }

    suspend fun spotlightArticles(session: PixivSession): SpotlightResult =
        getSpotlightPage(session, pixivApiUrl("v1/spotlight/articles", "filter" to "for_android"))

    suspend fun nextSpotlightPage(
        session: PixivSession,
        nextUrl: String,
    ): SpotlightResult = getSpotlightPage(session, nextUrl.toTrustedPixivApiUrl())

    private suspend fun getSpotlightPage(
        session: PixivSession,
        url: HttpUrl,
    ): SpotlightResult =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitSpotlight() }

    suspend fun illustComments(
        session: PixivSession,
        illustId: Long,
        offset: Int? = null,
    ): CommentResponse =
        Request
            .Builder()
            .url(
                pixivApiUrl(
                    "v3/illust/comments",
                    "illust_id" to illustId.toString(),
                    "offset" to offset?.toString(),
                ),
            ).pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCommentPage() }

    suspend fun illustCommentReplies(
        session: PixivSession,
        commentId: Long,
        offset: Int? = null,
    ): CommentResponse =
        Request
            .Builder()
            .url(
                pixivApiUrl(
                    "v2/illust/comment/replies",
                    "comment_id" to commentId.toString(),
                    "offset" to offset?.toString(),
                ),
            ).pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCommentPage() }

    suspend fun novelComments(
        session: PixivSession,
        novelId: Long,
    ): CommentResponse =
        Request
            .Builder()
            .url(pixivApiUrl("v3/novel/comments", "novel_id" to novelId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCommentPage() }

    suspend fun novelCommentReplies(
        session: PixivSession,
        commentId: Long,
    ): CommentResponse =
        Request
            .Builder()
            .url(pixivApiUrl("v2/novel/comment/replies", "comment_id" to commentId.toString()))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCommentPage() }

    suspend fun nextCommentPage(
        session: PixivSession,
        nextUrl: String,
    ): CommentResponse =
        Request
            .Builder()
            .url(nextUrl.toTrustedPixivApiUrl())
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCommentPage() }

    suspend fun addIllustComment(
        session: PixivSession,
        illustId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        val form =
            FormBody
                .Builder()
                .add("illust_id", illustId.toString())
                .add("comment", comment)
        parentCommentId?.let { form.add("parent_comment_id", it.toString()) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/illust/comment/add", form.build())
    }

    suspend fun currentUserProfile(session: PixivSession): CurrentUserProfile =
        Request
            .Builder()
            .url(pixivApiUrl("v1/user/me/state"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitCurrentUserProfile() }

    suspend fun relatedUsers(
        session: PixivSession,
        userId: Long,
    ): RelatedUsersResult =
        getRelatedUsersPage(
            session,
            pixivApiUrl("v1/user/related", "seed_user_id" to userId.toString(), "filter" to "for_android"),
        )

    suspend fun nextRelatedUsersPage(
        session: PixivSession,
        nextUrl: String,
    ): RelatedUsersResult = getRelatedUsersPage(session, nextUrl.toTrustedPixivApiUrl())

    private suspend fun getRelatedUsersPage(
        session: PixivSession,
        url: HttpUrl,
    ): RelatedUsersResult =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitUserPreviewPage() }
            .let { page -> RelatedUsersResult(users = page.items, nextUrl = page.nextUrl) }

    suspend fun setUserWorkspace(
        session: PixivSession,
        workspace: UserWorkspace,
    ) {
        val form = FormBody.Builder()
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
        ).forEach { (key, value) -> value?.let { form.add(key, it) } }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/user/workspace/edit", form.build())
    }

    suspend fun setUserProfile(
        session: PixivSession,
        profile: UserProfileEdit,
    ): AccountEditResult {
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("gender", profile.gender.lowercase())
                .addFormDataPart("address", profile.address.toString())
                .addFormDataPart("job", profile.job.toString())
                .addFormDataPart("user_name", profile.userName)
                .addFormDataPart("webpage", profile.webpage)
                .addFormDataPart("twitter", profile.twitter)
                .addFormDataPart("comment", profile.comment)
                .addFormDataPart("birthday", profile.birthday)
                .apply {
                    profile.country?.let { addFormDataPart("country", it) }
                    profile.avatarJpeg?.let {
                        addFormDataPart("profile_image", "profile.jpeg", it.toRequestBody("image/jpeg".toMediaType()))
                    }
                }.build()
        return Request
            .Builder()
            .url("https://app-api.pixiv.net/v1/user/profile/edit")
            .pixivApiHeaders(session)
            .post(body)
            .build()
            .let { httpClient.newCall(it).awaitAccountEdit() }
    }

    suspend fun addIllustStampComment(
        session: PixivSession,
        illustId: Long,
        stampId: Long,
        parentCommentId: Long? = null,
    ) {
        val form =
            FormBody
                .Builder()
                .add("illust_id", illustId.toString())
                .add("stamp_id", stampId.toString())
        parentCommentId?.let { form.add("parent_comment_id", it.toString()) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/illust/comment/add", form.build())
    }

    suspend fun deleteIllustComment(
        session: PixivSession,
        commentId: Long,
    ) {
        postAuthedForm(
            session,
            "https://app-api.pixiv.net/v1/illust/comment/delete",
            FormBody.Builder().add("comment_id", commentId.toString()).build(),
        )
    }

    suspend fun isAiContentVisible(session: PixivSession): Boolean =
        Request
            .Builder()
            .url(pixivApiUrl("v1/user/ai-show-settings"))
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitOptionalBoolean() }
            ?: false

    suspend fun setAiContentVisible(
        session: PixivSession,
        visible: Boolean,
    ) {
        val saved =
            Request
                .Builder()
                .url("https://app-api.pixiv.net/v1/user/ai-show-settings/edit")
                .pixivApiHeaders(session)
                .post(FormBody.Builder().add("show_ai", visible.toString()).build())
                .build()
                .let { httpClient.newCall(it).awaitOptionalBoolean() }
        check(saved == visible) { "AI作品の表示設定を更新できませんでした。" }
    }

    suspend fun addNovelComment(
        session: PixivSession,
        novelId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        val form =
            FormBody
                .Builder()
                .add("novel_id", novelId.toString())
                .add("comment", comment)
        parentCommentId?.let { form.add("parent_comment_id", it.toString()) }
        postAuthedForm(session, "https://app-api.pixiv.net/v1/novel/comment/add", form.build())
    }

    suspend fun addWatchlistManga(
        session: PixivSession,
        seriesId: Long,
    ) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/watchlist/manga/add",
            body = FormBody.Builder().add("series_id", seriesId.toString()).build(),
        )
    }

    suspend fun removeWatchlistManga(
        session: PixivSession,
        seriesId: Long,
    ) {
        postAuthedForm(
            session = session,
            url = "https://app-api.pixiv.net/v1/watchlist/manga/delete",
            body = FormBody.Builder().add("series_id", seriesId.toString()).build(),
        )
    }

    suspend fun popularPreview(
        session: PixivSession,
        word: String,
    ): PageResult<Illust> =
        getIllustPage(
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

    suspend fun recommendedNovels(session: PixivSession): PageResult<NovelPreview> =
        getNovelPage(
            session = session,
            url = pixivApiUrl("v1/novel/recommended", "filter" to "for_android", "include_ranking_novels" to "true"),
        )

    suspend fun nextNovelPage(
        session: PixivSession,
        nextUrl: String,
    ): PageResult<NovelPreview> = getNovelPage(session, nextUrl.toTrustedPixivApiUrl())

    suspend fun novelText(
        session: PixivSession,
        novelId: Long,
    ): NovelTextContent =
        Request
            .Builder()
            .url(
                pixivApiUrl(
                    "webview/v2/novel",
                    "id" to novelId.toString(),
                    "viewer_version" to "20221031_ai",
                ),
            ).pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitNovelText(novelId) }

    private suspend fun getIllustPage(
        session: PixivSession,
        url: HttpUrl,
    ): PageResult<Illust> =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitIllustPage() }

    private suspend fun getNovelPage(
        session: PixivSession,
        url: HttpUrl,
    ): PageResult<NovelPreview> =
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .get()
            .build()
            .let { httpClient.newCall(it).awaitNovelPage() }

    private suspend fun postAuthedForm(
        session: PixivSession,
        url: String,
        body: FormBody,
    ) {
        Request
            .Builder()
            .url(url)
            .pixivApiHeaders(session)
            .post(body)
            .build()
            .let { httpClient.newCall(it).awaitComplete() }
    }

    private fun pixivApiUrl(
        path: String,
        vararg queryParameters: Pair<String, String?>,
    ): HttpUrl =
        HttpUrl
            .Builder()
            .scheme("https")
            .host("app-api.pixiv.net")
            .addPathSegments(path)
            .apply {
                queryParameters.forEach { (name, value) ->
                    value?.let { addQueryParameter(name, it) }
                }
            }.build()

    private fun pixivDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy-M-d"))
}
