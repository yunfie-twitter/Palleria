package com.yunfie.illustia.data

import com.yunfie.illustia.isCancellationFailure
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SESSION_EXPIRY_SKEW_MILLIS
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
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.rebaseSyncedCollections
import com.yunfie.illustia.settings.store.STARTUP_LOGGED_IN_TOKEN
import com.yunfie.illustia.settings.syncedCollections
import com.yunfie.illustia.settings.withSyncedCollections
import com.yunfie.illustia.visibleWithSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

private const val CACHE_TTL_SHORT_MILLIS = 3 * 60 * 1000L // 3 minutes
private const val CACHE_TTL_MEDIUM_MILLIS = 5 * 60 * 1000L // 5 minutes
private const val CACHE_TTL_STANDARD_MILLIS = 10 * 60 * 1000L // 10 minutes
private const val CACHE_TTL_SERIES_MILLIS = 15 * 60 * 1000L // 15 minutes
private const val CACHE_TTL_LONG_MILLIS = 30 * 60 * 1000L // 30 minutes
private const val CACHE_TTL_HOUR_MILLIS = 60 * 60 * 1000L // 1 hour
private const val HTTP_TOO_MANY_REQUESTS = 429

@Suppress("LargeClass")
class IllustiaRepository(
    private val settingsStore: SettingsStore,
) {
    @Volatile
    private var session: PixivSession? = null
    private val sessionMutex = Mutex()
    private val homeSnapshot = HomeFeedSnapshot(settingsStore.homeCacheDirectory())
    private val accountRevision = AtomicLong()
    private val credentialsRevision = AtomicLong()
    internal val accountGeneration: Long get() = accountRevision.get()

    internal suspend fun readHomeSnapshot(kind: HomeFeedKind): PageResult<Illust>? {
        val accountHash = settingsStore.readStartupAccountHash()
        val snapshot = if (accountHash.isNotBlank()) homeSnapshot.readWithAccountHash(kind, accountHash) else null
        val filters = cachedSettings ?: settingsStore.readFeedSettings()
        return snapshot?.let { it.copy(items = it.items.visibleWithSettings(filters)) }
    }

    internal suspend fun readFeedSettings(): AppSettings = settingsStore.readFeedSettings()

    @Volatile
    private var cachedSettings: AppSettings? = null

    @Volatile
    private var cachedSettingsRevision = 0L
    private val settingsCacheMutex = Mutex()

    @Volatile
    private var apiClientMode: NetworkMode = NetworkMode.Standard

    @Volatile
    private var apiClientInstance: PixivApiClient? = null

    private val apiClient: PixivApiClient
        get() {
            val current = apiClientInstance
            if (current != null) return current
            return synchronized(this) {
                apiClientInstance ?: PixivApiClient(apiClientMode).also { apiClientInstance = it }
            }
        }

    val apiCache = PixivApiCache()

    fun clearApiCache() {
        apiCache.clear()
    }

    internal suspend inline fun <reified T : Any> withApiCache(
        key: String,
        ttlMillis: Long = CACHE_TTL_STANDARD_MILLIS,
        forceRefresh: Boolean = false,
        crossinline block: suspend () -> T,
    ): T {
        if (!forceRefresh) {
            val cached = apiCache.get<T>(key)
            if (cached != null) return cached
        }
        val generation = accountGeneration
        return try {
            val result = block()
            if (accountGeneration == generation) apiCache.put(key, result, ttlMillis = ttlMillis)
            result
        } catch (expectedFailure: Exception) {
            if (expectedFailure.isCancellationFailure()) throw expectedFailure
            val error = expectedFailure
            val fallback =
                if (error.isPixivRateLimited() || error.isTransientConnectionIssue()) {
                    apiCache.getStale<T>(key)
                } else {
                    null
                }
            fallback ?: throw error
        }
    }

    suspend fun readSettings(viewHistoryLimit: Int? = null): AppSettings {
        val settings =
            settingsCacheMutex.withLock {
                cachedSettings?.takeIf { cachedSettingsRevision == credentialsRevision.get() } ?: run {
                    var revision: Long
                    var loaded: AppSettings
                    do {
                        revision = credentialsRevision.get()
                        loaded = settingsStore.read(viewHistoryLimit)
                    } while (revision != credentialsRevision.get())
                    loaded.also {
                        cachedSettings = it
                        cachedSettingsRevision = revision
                    }
                }
            }
        ensureApiClient(NetworkMode.fromCode(settings.pixivNetworkMode))
        return settings
    }

    suspend fun readFullViewHistory(): List<Illust> {
        val fullHistory = settingsStore.readFullViewHistory()
        settingsCacheMutex.withLock {
            cachedSettings = cachedSettings?.copy(viewHistory = fullHistory)
        }
        return fullHistory
    }

    suspend fun readStartupSettings(): AppSettings = settingsStore.readStartup()

    suspend fun saveSettings(
        settings: AppSettings,
        baseSettings: AppSettings? = null,
    ) {
        val written = settingsStore.write(settings, baseSettings)
        val cached =
            settingsCacheMutex.withLock {
                val current = cachedSettings ?: settingsStore.read()
                val base = baseSettings ?: current
                val collections =
                    rebaseSyncedCollections(
                        base.syncedCollections(),
                        settings.syncedCollections(),
                        current.syncedCollections(),
                    )
                val enabled =
                    if (base.pallaSyncEnabled == settings.pallaSyncEnabled) {
                        current.pallaSyncEnabled
                    } else {
                        settings.pallaSyncEnabled
                    }
                val serverUrl =
                    if (base.pallaSyncServerUrl == settings.pallaSyncServerUrl) {
                        current.pallaSyncServerUrl
                    } else {
                        settings.pallaSyncServerUrl
                    }
                written
                    .copy(
                        pallaSyncEnabled = enabled,
                        pallaSyncServerUrl = serverUrl,
                    ).withSyncedCollections(collections)
                    .also { cachedSettings = it }
            }
        ensureApiClient(NetworkMode.fromCode(cached.pixivNetworkMode))
    }

    suspend fun saveSettingsFromSync(settings: AppSettings) {
        val synced = settings.syncedCollections()
        settingsStore.writeSyncedCollections(synced)
        updateCachedSyncedCollections(synced)
    }

    internal suspend fun updateCachedSyncedCollections(synced: SyncedCollectionsSnapshot) {
        settingsCacheMutex.withLock {
            val current = cachedSettings ?: settingsStore.read()
            cachedSettings = current.withSyncedCollections(synced)
        }
    }

    internal suspend fun updateCachedPallaSyncEnabled(enabled: Boolean) {
        settingsCacheMutex.withLock {
            val current = cachedSettings ?: settingsStore.read()
            cachedSettings = current.copy(pallaSyncEnabled = enabled)
        }
    }

    suspend fun login(refreshToken: String): PixivSession =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            clearApiCache()
            loginInternal(refreshToken)
        }

    private suspend fun loginInternal(refreshToken: String): PixivSession {
        ensureApiClient(NetworkMode.fromCode(settingsStore.readAuth().networkMode))
        val nextSession = apiClient.loginWithRefreshToken(refreshToken)
        persistSession(nextSession)
        return nextSession
    }

    suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            clearApiCache()
            ensureApiClient(NetworkMode.fromCode(settingsStore.readAuth().networkMode))
            val nextSession = apiClient.loginWithAuthorizationCode(code, codeVerifier)
            persistSession(nextSession)
            nextSession
        }

    private suspend fun persistSession(nextSession: PixivSession) {
        settingsStore.persistAuth(nextSession)
        session = nextSession
        credentialsRevision.incrementAndGet()
        cachedSettings = null
    }

    suspend fun logout() =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            session = null
            clearApiCache()
            settingsStore.clearSensitive()
            cachedSettings =
                settingsCacheMutex
                    .withLock { settingsStore.read().also { cachedSettings = it } }
                    .also { ensureApiClient(NetworkMode.fromCode(it.pixivNetworkMode)) }
        }

    suspend fun loadRanking(
        mode: String,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("ranking:$mode", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.ranking(session, mode) }
        }

    suspend fun followingIllusts(
        restrict: Restrict,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("following:$restrict", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.following(session, restrict) }
        }

    suspend fun loadHome(
        kind: HomeFeedKind,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> {
        val generation = accountGeneration
        return withApiCache("home_feed:$generation:${kind.name}", CACHE_TTL_SHORT_MILLIS, forceRefresh) {
            withSessionRetry { active ->
                val page =
                    when (kind) {
                        HomeFeedKind.Recommended -> apiClient.recommended(active)
                        HomeFeedKind.Ranking -> apiClient.ranking(active)
                        HomeFeedKind.New -> apiClient.newest(active)
                    }
                sessionMutex.withLock {
                    if (accountGeneration == generation && settingsStore.readAuth().refreshToken == active.refreshToken) {
                        homeSnapshot.write(kind, active.refreshToken, page)
                    }
                }
                page
            }
        }
    }

    suspend fun loadNovels(forceRefresh: Boolean = false): PageResult<NovelPreview> =
        withApiCache("novels:recommended", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.recommendedNovels(session) }
        }

    suspend fun nextNovelPage(nextUrl: String): PageResult<NovelPreview> =
        withSessionRetry { session -> apiClient.nextNovelPage(session, nextUrl) }

    suspend fun loadNovelText(
        novelId: Long,
        forceRefresh: Boolean = false,
    ): NovelTextContent =
        withApiCache("novel_text:$novelId", CACHE_TTL_HOUR_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.novelText(session, novelId) }
        }

    suspend fun search(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        type: String? = null,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("search:$word:$sort:$target:$duration:$bookmarkFilter:$includeR18:$type", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                apiClient.search(session, word, sort, target, duration, bookmarkFilter, includeR18, type = type)
            }
        }

    suspend fun searchNovels(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        forceRefresh: Boolean = false,
    ): PageResult<NovelPreview> =
        withApiCache("search_novels:$word:$sort:$target:$duration:$bookmarkFilter:$includeR18", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                apiClient.searchNovels(session, word, sort, target, duration, bookmarkFilter, includeR18)
            }
        }

    suspend fun searchUsers(
        word: String,
        forceRefresh: Boolean = false,
    ): PageResult<UserPreview> =
        withApiCache("search_users:$word", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.searchUsers(session, word) }
        }

    suspend fun trendingTags(forceRefresh: Boolean = false): List<String> =
        withApiCache("trending_tags", CACHE_TTL_LONG_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.trendingTags(session) }
        }

    suspend fun popularPreview(
        word: String,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("popular_preview:$word", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.popularPreview(session, word) }
        }

    suspend fun searchAutocomplete(
        word: String,
        forceRefresh: Boolean = false,
    ): List<String> {
        val trimmed = word.trim()
        if (trimmed.isBlank()) return emptyList()
        return withApiCache("autocomplete:${trimmed.lowercase()}", CACHE_TTL_LONG_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.searchAutocomplete(session, trimmed) }
        }
    }

    suspend fun watchlistManga(forceRefresh: Boolean = false): WatchlistMangaModel =
        withApiCache("watchlist_manga", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.watchlistManga(session) }
        }

    suspend fun nextWatchlistMangaPage(nextUrl: String): WatchlistMangaModel =
        withSessionRetry { session -> apiClient.nextWatchlistMangaPage(session, nextUrl) }

    suspend fun illustSeries(
        illustSeriesId: Long,
        forceRefresh: Boolean = false,
    ): IllustSeriesWithIdModel =
        withApiCache("illust_series:$illustSeriesId", CACHE_TTL_SERIES_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.illustSeries(session, illustSeriesId) }
        }

    suspend fun nextIllustSeriesPage(nextUrl: String): IllustSeriesWithIdModel =
        withSessionRetry { session -> apiClient.nextIllustSeriesPage(session, nextUrl) }

    suspend fun illustComments(
        illustId: Long,
        offset: Int? = null,
    ): CommentResponse = withSessionRetry { session -> apiClient.illustComments(session, illustId, offset) }

    suspend fun illustCommentReplies(
        commentId: Long,
        offset: Int? = null,
    ): CommentResponse = withSessionRetry { session -> apiClient.illustCommentReplies(session, commentId, offset) }

    suspend fun novelComments(novelId: Long): CommentResponse = withSessionRetry { session -> apiClient.novelComments(session, novelId) }

    suspend fun novelCommentReplies(commentId: Long): CommentResponse =
        withSessionRetry { session -> apiClient.novelCommentReplies(session, commentId) }

    suspend fun nextCommentPage(nextUrl: String): CommentResponse =
        withSessionRetry { session -> apiClient.nextCommentPage(session, nextUrl) }

    suspend fun addIllustComment(
        illustId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        withSessionRetry { session -> apiClient.addIllustComment(session, illustId, comment, parentCommentId) }
    }

    suspend fun addIllustStampComment(
        illustId: Long,
        stampId: Long,
        parentCommentId: Long? = null,
    ) {
        withSessionRetry { session ->
            apiClient.addIllustStampComment(session, illustId, stampId, parentCommentId)
        }
    }

    suspend fun deleteIllustComment(commentId: Long) {
        withSessionRetry { session -> apiClient.deleteIllustComment(session, commentId) }
    }

    suspend fun isAiContentVisible(): Boolean = withSessionRetry { session -> apiClient.isAiContentVisible(session) }

    suspend fun setAiContentVisible(visible: Boolean) {
        withSessionRetry { session -> apiClient.setAiContentVisible(session, visible) }
    }

    suspend fun addNovelComment(
        novelId: Long,
        comment: String,
        parentCommentId: Long? = null,
    ) {
        withSessionRetry { session -> apiClient.addNovelComment(session, novelId, comment, parentCommentId) }
    }

    suspend fun watchlistMangaAdd(seriesId: Long) {
        withSessionRetry { session -> apiClient.addWatchlistManga(session, seriesId) }
        apiCache.remove("watchlist_manga")
        apiCache.remove("illust_series:$seriesId")
    }

    suspend fun watchlistMangaDelete(seriesId: Long) {
        withSessionRetry { session -> apiClient.removeWatchlistManga(session, seriesId) }
        apiCache.remove("watchlist_manga")
        apiCache.remove("illust_series:$seriesId")
    }

    suspend fun followingUsers(
        restrict: Restrict,
        forceRefresh: Boolean = false,
    ): PageResult<UserPreview> =
        withApiCache("following_users:$restrict", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                val userId =
                    session.userId
                        ?: throw IllegalStateException("Pixiv user ID is not available.")
                apiClient.followingUsers(session, userId, restrict)
            }
        }

    suspend fun userDetail(
        userId: Long,
        forceRefresh: Boolean = false,
    ): UserProfile =
        withApiCache("user_detail:$userId", CACHE_TTL_SERIES_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.userDetail(session, userId) }
        }

    suspend fun userFollowDetail(
        userId: Long,
        forceRefresh: Boolean = false,
    ): UserFollowDetail =
        withApiCache("user_follow_detail:$userId", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.userFollowDetail(session, userId) }
        }

    suspend fun createWebSocket(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): PixivWebSocketClient = apiClient.createWebSocket(requireSession(), url, headers)

    suspend fun currentUserProfile(): CurrentUserProfile = withSessionRetry { session -> apiClient.currentUserProfile(session) }

    suspend fun relatedUsers(userId: Long): RelatedUsersResult = withSessionRetry { session -> apiClient.relatedUsers(session, userId) }

    suspend fun nextRelatedUsersPage(nextUrl: String): RelatedUsersResult =
        withSessionRetry { session -> apiClient.nextRelatedUsersPage(session, nextUrl) }

    suspend fun setUserWorkspace(workspace: UserWorkspace) {
        withSessionRetry { session -> apiClient.setUserWorkspace(session, workspace) }
    }

    suspend fun setUserProfile(profile: UserProfileEdit): AccountEditResult =
        withSessionRetry { session -> apiClient.setUserProfile(session, profile) }

    suspend fun trendingTagDetails(): List<TrendingTag> = withSessionRetry { session -> apiClient.trendingTagDetails(session) }

    suspend fun spotlightArticles(): SpotlightResult = withSessionRetry { session -> apiClient.spotlightArticles(session) }

    suspend fun nextSpotlightPage(nextUrl: String): SpotlightResult =
        withSessionRetry { session -> apiClient.nextSpotlightPage(session, nextUrl) }

    suspend fun reportIllust(
        illustId: Long,
        problemType: String? = null,
        message: String? = null,
    ) {
        withSessionRetry { session -> apiClient.reportIllust(session, illustId, problemType, message) }
    }

    suspend fun reportUser(
        userId: Long,
        problemType: String? = null,
        message: String? = null,
    ) {
        withSessionRetry { session -> apiClient.reportUser(session, userId, problemType, message) }
    }

    suspend fun addNovelBookmark(
        novelId: Long,
        restrict: Restrict,
    ) {
        withSessionRetry { session -> apiClient.addNovelBookmark(session, novelId, restrict) }
    }

    suspend fun removeNovelBookmark(novelId: Long) {
        withSessionRetry { session -> apiClient.removeNovelBookmark(session, novelId) }
    }

    suspend fun addNovelMarker(
        novelId: Long,
        page: Int,
    ) {
        withSessionRetry { session -> apiClient.addNovelMarker(session, novelId, page) }
    }

    suspend fun removeNovelMarker(novelId: Long) {
        withSessionRetry { session -> apiClient.removeNovelMarker(session, novelId) }
    }

    suspend fun notifications(): NotificationListResult = withSessionRetry { session -> apiClient.notifications(session) }

    suspend fun notificationViewMore(notificationId: Long): NotificationListResult =
        withSessionRetry { session -> apiClient.notificationViewMore(session, notificationId) }

    suspend fun nextNotificationPage(nextUrl: String): NotificationListResult =
        withSessionRetry { session -> apiClient.nextNotificationPage(session, nextUrl) }

    suspend fun stamps(): List<PixivStamp> = withSessionRetry { session -> apiClient.stamps(session) }

    suspend fun userIllusts(userId: Long): PageResult<Illust> = withSessionRetry { session -> apiClient.userIllusts(session, userId) }

    suspend fun illustDetail(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): Illust =
        withApiCache("illust_detail:$illustId", CACHE_TTL_SERIES_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.illustDetail(session, illustId) }
        }

    suspend fun ugoiraMetadata(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): UgoiraMetadataResponse =
        withApiCache("ugoira_meta:$illustId", CACHE_TTL_LONG_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.ugoiraMetadata(session, illustId) }
        }

    suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback = apiClient.prepareUgoira(url, cacheDir, frames)

    suspend fun relatedIllusts(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("related_illusts:$illustId", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.relatedIllusts(session, illustId) }
        }

    suspend fun followUser(
        userId: Long,
        restrict: Restrict,
    ) {
        withSessionRetry { session ->
            apiClient.followUser(session, userId, restrict)
        }
        apiCache.removeByPrefix("following:")
        apiCache.removeByPrefix("following_users:")
        apiCache.remove("user_detail:$userId")
        apiCache.remove("user_follow_detail:$userId")
    }

    suspend fun unfollowUser(userId: Long) {
        withSessionRetry { session -> apiClient.unfollowUser(session, userId) }
        apiCache.removeByPrefix("following:")
        apiCache.removeByPrefix("following_users:")
        apiCache.remove("user_detail:$userId")
        apiCache.remove("user_follow_detail:$userId")
    }

    suspend fun bookmarks(
        userId: Long,
        restrict: Restrict,
        forceRefresh: Boolean = false,
    ): PageResult<Illust> =
        withApiCache("bookmarks:$userId:$restrict", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.bookmarks(session, userId, restrict) }
        }

    suspend fun nextPage(nextUrl: String): PageResult<Illust> = withSessionRetry { session -> apiClient.nextIllustPage(session, nextUrl) }

    suspend fun nextUserSearchPage(nextUrl: String): PageResult<UserPreview> =
        withSessionRetry { session -> apiClient.nextUserPreviewPage(session, nextUrl) }

    suspend fun toggleBookmark(
        illust: Illust,
        restrict: Restrict,
    ): Illust =
        withSessionRetry { session ->
            if (illust.isBookmarked) {
                apiClient.removeBookmark(session, illust.id)
                apiCache.removeByPrefix("bookmarks:")
                apiCache.remove("illust_detail:${illust.id}")
                illust.copy(isBookmarked = false)
            } else {
                apiClient.addBookmark(session, illust.id, restrict)
                apiCache.removeByPrefix("bookmarks:")
                apiCache.remove("illust_detail:${illust.id}")
                illust.copy(isBookmarked = true)
            }
        }

    private suspend fun requireSession(): PixivSession {
        val currentSession = session
        if (currentSession != null &&
            (
                currentSession.expiresAtMillis == 0L ||
                    currentSession.expiresAtMillis > System.currentTimeMillis() + SESSION_EXPIRY_SKEW_MILLIS
            )
        ) {
            return currentSession
        }
        return sessionMutex.withLock {
            val lockedSession = session
            if (lockedSession != null &&
                (
                    lockedSession.expiresAtMillis == 0L ||
                        lockedSession.expiresAtMillis > System.currentTimeMillis() + SESSION_EXPIRY_SKEW_MILLIS
                )
            ) {
                return@withLock lockedSession
            }
            val auth = settingsStore.readAuth()
            require(
                auth.refreshToken.isNotBlank() && auth.refreshToken != STARTUP_LOGGED_IN_TOKEN,
            ) { "Pixiv refresh token が未設定です。" }
            ensureApiClient(NetworkMode.fromCode(auth.networkMode))
            session?.takeIf {
                it.refreshToken == auth.refreshToken &&
                    (it.expiresAtMillis == 0L || it.expiresAtMillis > System.currentTimeMillis() + SESSION_EXPIRY_SKEW_MILLIS)
            }
                ?: auth.session?.also { session = it }
                ?: loginInternal(auth.refreshToken)
        }
    }

    private suspend inline fun <T> withSessionRetry(crossinline block: suspend (PixivSession) -> T): T {
        val generation = accountGeneration
        var activeSession = requireSession()
        var attempt = 0
        while (attempt < 2) {
            if (generation != accountGeneration) throw CancellationException("Account changed during request")
            try {
                return block(activeSession)
            } catch (expectedFailure: Exception) {
                if (expectedFailure.isCancellationFailure()) throw expectedFailure
                if (generation != accountGeneration) throw CancellationException("Account changed during request")
                val error = expectedFailure
                when {
                    error.isPixivAuthExpired() -> {
                        if (attempt == 0) {
                            activeSession = refreshSession(activeSession)
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

                    else -> {
                        throw error
                    }
                }
            }
        }
        throw IllegalStateException("Pixiv request failed unexpectedly.")
    }

    private suspend fun refreshSession(failed: PixivSession): PixivSession =
        sessionMutex.withLock {
            val refreshToken = settingsStore.readAuth().refreshToken
            session?.takeIf { it.accessToken != failed.accessToken && it.refreshToken == refreshToken }?.let { return@withLock it }
            require(refreshToken.isNotBlank() && refreshToken != STARTUP_LOGGED_IN_TOKEN) { "Pixiv refresh token が未設定です。" }
            loginInternal(refreshToken)
        }

    private fun ensureApiClient(mode: NetworkMode) {
        if (apiClientMode == mode && apiClientInstance != null) return
        synchronized(this) {
            if (apiClientMode != mode || apiClientInstance == null) {
                apiClientInstance = PixivApiClient(mode)
                apiClientMode = mode
            }
        }
    }
}
