package com.yunfie.illustia.data

import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore

class IllustiaRepository(
    private val settingsStore: SettingsStore,
    providedApiClient: PixivApiClient? = null,
) {
    private var session: PixivSession? = null
    private var cachedSettings: AppSettings? = null
    private val apiClient: PixivApiClient by lazy { providedApiClient ?: PixivApiClient() }

    suspend fun readSettings(): AppSettings {
        return cachedSettings ?: settingsStore.read().also { cachedSettings = it }
    }

    suspend fun saveSettings(settings: AppSettings) {
        cachedSettings = settings
        settingsStore.write(settings)
    }

    suspend fun login(refreshToken: String): PixivSession {
        val nextSession = apiClient.loginWithRefreshToken(refreshToken)
        persistSession(nextSession)
        return nextSession
    }

    suspend fun loginWithAuthorizationCode(code: String, codeVerifier: String): PixivSession {
        val nextSession = apiClient.loginWithAuthorizationCode(code, codeVerifier)
        persistSession(nextSession)
        return nextSession
    }

    private suspend fun persistSession(nextSession: PixivSession) {
        session = nextSession
        val current = settingsStore.read()
        val nextSettings = current.copy(
            refreshToken = nextSession.refreshToken,
            bookmarkUserId = nextSession.userId ?: current.bookmarkUserId,
        )
        cachedSettings = nextSettings
        settingsStore.write(nextSettings)
    }

    suspend fun logout() {
        session = null
        settingsStore.clearSensitive()
        cachedSettings = settingsStore.read()
    }

    suspend fun loadRanking(mode: String): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.ranking(session, mode) }
    }

    suspend fun followingIllusts(restrict: Restrict): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.following(session, restrict) }
    }

    suspend fun loadHome(kind: HomeFeedKind): PageResult<Illust> {
        return withSessionRetry { session ->
            when (kind) {
                HomeFeedKind.Recommended -> apiClient.recommended(session)
                HomeFeedKind.Ranking -> apiClient.ranking(session) // Default to day
                HomeFeedKind.New -> apiClient.newest(session)
            }
        }
    }

    suspend fun loadNovels(): PageResult<NovelPreview> {
        return withSessionRetry { session -> apiClient.recommendedNovels(session) }
    }

    suspend fun nextNovelPage(nextUrl: String): PageResult<NovelPreview> {
        return withSessionRetry { session -> apiClient.nextNovelPage(session, nextUrl) }
    }

    suspend fun loadNovelText(novelId: Long): NovelTextContent {
        return withSessionRetry { session -> apiClient.novelText(session, novelId) }
    }

    suspend fun search(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
    ): PageResult<Illust> {
        return withSessionRetry { session ->
            apiClient.search(session, word, sort, target, duration, bookmarkFilter, includeR18)
        }
    }

    suspend fun searchUsers(word: String): PageResult<UserPreview> {
        return withSessionRetry { session -> apiClient.searchUsers(session, word) }
    }

    suspend fun trendingTags(): List<String> {
        return withSessionRetry { session -> apiClient.trendingTags(session) }
    }

    suspend fun popularPreview(word: String): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.popularPreview(session, word) }
    }

    suspend fun searchAutocomplete(word: String): List<String> {
        return withSessionRetry { session -> apiClient.searchAutocomplete(session, word) }
    }

    suspend fun watchlistManga(): WatchlistMangaModel {
        return withSessionRetry { session -> apiClient.watchlistManga(session) }
    }

    suspend fun nextWatchlistMangaPage(nextUrl: String): WatchlistMangaModel {
        return withSessionRetry { session -> apiClient.nextWatchlistMangaPage(session, nextUrl) }
    }

    suspend fun illustSeries(illustSeriesId: Long): IllustSeriesWithIdModel {
        return withSessionRetry { session -> apiClient.illustSeries(session, illustSeriesId) }
    }

    suspend fun nextIllustSeriesPage(nextUrl: String): IllustSeriesWithIdModel {
        return withSessionRetry { session -> apiClient.nextIllustSeriesPage(session, nextUrl) }
    }

    suspend fun illustComments(illustId: Long): CommentResponse {
        return withSessionRetry { session -> apiClient.illustComments(session, illustId) }
    }

    suspend fun illustCommentReplies(commentId: Long): CommentResponse {
        return withSessionRetry { session -> apiClient.illustCommentReplies(session, commentId) }
    }

    suspend fun novelComments(novelId: Long): CommentResponse {
        return withSessionRetry { session -> apiClient.novelComments(session, novelId) }
    }

    suspend fun novelCommentReplies(commentId: Long): CommentResponse {
        return withSessionRetry { session -> apiClient.novelCommentReplies(session, commentId) }
    }

    suspend fun nextCommentPage(nextUrl: String): CommentResponse {
        return withSessionRetry { session -> apiClient.nextCommentPage(session, nextUrl) }
    }

    suspend fun addIllustComment(illustId: Long, comment: String, parentCommentId: Long? = null) {
        withSessionRetry { session -> apiClient.addIllustComment(session, illustId, comment, parentCommentId) }
    }

    suspend fun addNovelComment(novelId: Long, comment: String, parentCommentId: Long? = null) {
        withSessionRetry { session -> apiClient.addNovelComment(session, novelId, comment, parentCommentId) }
    }

    suspend fun watchlistMangaAdd(seriesId: Long) {
        withSessionRetry { session -> apiClient.addWatchlistManga(session, seriesId) }
    }

    suspend fun watchlistMangaDelete(seriesId: Long) {
        withSessionRetry { session -> apiClient.removeWatchlistManga(session, seriesId) }
    }

    suspend fun followingUsers(restrict: Restrict): PageResult<UserPreview> {
        return withSessionRetry { session ->
            val userId = session.userId
                ?: throw IllegalStateException("Pixiv user ID is not available.")
            apiClient.followingUsers(session, userId, restrict)
        }
    }

    suspend fun userDetail(userId: Long): UserProfile {
        return withSessionRetry { session -> apiClient.userDetail(session, userId) }
    }

    suspend fun userIllusts(userId: Long): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.userIllusts(session, userId) }
    }

    suspend fun illustDetail(illustId: Long): Illust {
        return withSessionRetry { session -> apiClient.illustDetail(session, illustId) }
    }

    suspend fun relatedIllusts(illustId: Long): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.relatedIllusts(session, illustId) }
    }

    suspend fun followUser(userId: Long, restrict: Restrict) {
        withSessionRetry { session ->
            apiClient.followUser(session, userId, restrict)
        }
    }

    suspend fun unfollowUser(userId: Long) {
        withSessionRetry { session -> apiClient.unfollowUser(session, userId) }
    }

    suspend fun bookmarks(userId: Long, restrict: Restrict): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.bookmarks(session, userId, restrict) }
    }

    suspend fun nextPage(nextUrl: String): PageResult<Illust> {
        return withSessionRetry { session -> apiClient.nextIllustPage(session, nextUrl) }
    }

    suspend fun nextUserSearchPage(nextUrl: String): PageResult<UserPreview> {
        return withSessionRetry { session -> apiClient.nextUserPreviewPage(session, nextUrl) }
    }

    suspend fun toggleBookmark(illust: Illust, restrict: Restrict): Illust {
        return withSessionRetry { session ->
            if (illust.isBookmarked) {
                apiClient.removeBookmark(session, illust.id)
                illust.copy(isBookmarked = false)
            } else {
                apiClient.addBookmark(session, illust.id, restrict)
                illust.copy(isBookmarked = true)
            }
        }
    }

    private suspend fun requireSession(): PixivSession {
        session?.let { return it }
        val refreshToken = readSettings().refreshToken
        require(refreshToken.isNotBlank()) { "Pixiv refresh token が未設定です。" }
        return login(refreshToken)
    }

    private suspend inline fun <T> withSessionRetry(
        crossinline block: suspend (PixivSession) -> T,
    ): T {
        var activeSession = requireSession()
        var attempt = 0
        while (attempt < 2) {
            try {
                return block(activeSession)
            } catch (error: Throwable) {
                when {
                    error.isPixivAuthExpired() -> {
                        if (attempt == 0) {
                            activeSession = refreshSession()
                            attempt++
                            continue
                        }
                        throw error
                    }
                    error.isTransientConnectionIssue() -> {
                        if (attempt == 0) {
                            attempt++
                            continue
                        }
                        throw error
                    }
                    else -> throw error
                }
            }
        }
        throw IllegalStateException("Pixiv request failed unexpectedly.")
    }

    private suspend fun refreshSession(): PixivSession {
        val settings = readSettings()
        val refreshToken = settings.refreshToken
        require(refreshToken.isNotBlank()) { "Pixiv refresh token が未設定です。" }
        return login(refreshToken)
    }

    private fun Throwable.isPixivAuthExpired(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is PixivApiException && current.isAuthExpired()) return true
            current = current.cause
        }
        return false
    }

    private fun PixivApiException.isAuthExpired(): Boolean {
        if (statusCode == 401) return true
        if (statusCode != 400) return false
        val message = apiMessage.lowercase()
        return message.contains("oauth") ||
            message.contains("token") ||
            message.contains("invalid_grant") ||
            message.contains("invalid refresh")
    }

    private fun Throwable.isTransientConnectionIssue(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (message.contains("Connection closed before full header was received")) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
