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
import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.RelatedUsersResult
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import com.yunfie.illustia.platform.FileSystem
import com.yunfie.illustia.platform.currentTimeMillis
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.STARTUP_TOKEN_PLACEHOLDER
import com.yunfie.illustia.visibleWithSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

private const val CACHE_TTL_SHORT_MILLIS = 3 * 60 * 1000L
private const val CACHE_TTL_MEDIUM_MILLIS = 5 * 60 * 1000L
private const val CACHE_TTL_STANDARD_MILLIS = 10 * 60 * 1000L
private const val CACHE_TTL_SERIES_MILLIS = 15 * 60 * 1000L
private const val CACHE_TTL_LONG_MILLIS = 30 * 60 * 1000L
private const val CACHE_TTL_AUTOCOMPLETE_MILLIS = 60 * 1000L
private const val CACHE_TTL_HOUR_MILLIS = 60 * 60 * 1000L
private const val HTTP_NOT_FOUND = 404
private const val HTTP_GONE = 410
private const val TRENDING_TAGS_CACHE_KEY = "trending_tags"

/**
 * Pixiv リポジトリのプラットフォーム非依存な本体。
 *
 * Android 版 `IllustiaRepository` と同じオーケストレーション（キャッシュ、inflight 合流、
 * オフライン時の古いフォールバック、セッション更新リトライ）をそのまま共通化したもの。
 * 永続化だけを [RepositoryPersistence] に、HTTP エンジンだけを [engineFactory] に委ねる。
 */
@Suppress("LargeClass", "TooManyFunctions")
abstract class IllustiaRepositoryCore(
    private val persistence: RepositoryPersistence,
    fileSystem: FileSystem,
    engineFactory: (NetworkMode) -> HttpClientEngine,
) : IllustiaRepositoryApi {
    private val clientLock = SynchronizedObject()

    @Volatile
    private var session: PixivSession? = null
    private val sessionMutex = Mutex()
    private val homeSnapshot = HomeFeedSnapshotStore(fileSystem, persistence.homeCacheDirectory()) { currentTimeMillis() }
    private val accountRevision = AtomicLongCounter()
    private val credentialsRevision = AtomicLongCounter()
    override val accountGeneration: Long get() = accountRevision.get()

    @Volatile
    private var cachedSettings: AppSettings? = null

    @Volatile
    private var cachedSettingsRevision = 0L
    private val settingsCacheMutex = Mutex()

    @Volatile
    private var apiClientMode: NetworkMode = NetworkMode.Standard

    @Volatile
    private var apiClientInstance: PixivApiClient? = null

    private val engineFactory = engineFactory

    @Volatile
    private var ktorClient: HttpClient = createPixivKtorClient(engineFactory(apiClientMode))

    private val apiClient: PixivApiClient
        get() {
            val current = apiClientInstance
            if (current != null) return current
            return synchronized(clientLock) {
                apiClientInstance ?: PixivApiClient(ktorClient).also { apiClientInstance = it }
            }
        }

    /**
     * うごイラ ZIP の展開はプラットフォームのアーカイブ API（Android: `ZipFile`）に依存するため、
     * 共通コアでは持たず実装側へ委ねる。
     */
    abstract override suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback

    /** うごイラ取得など、署名付きで生のバイトを落とす用途向け。 */
    protected val httpClient: HttpClient get() = ktorClient

    val apiCache = PixivApiCache()

    private val inFlightLock = kotlinx.atomicfu.locks.SynchronizedObject()
    private val inFlightRequests = mutableMapOf<String, Deferred<Any>>()

    // Shared in-flight requests are keyed by cache key, so concurrent callers with the same key
    // join one network round-trip instead of each firing their own and racing the cache write.
    private val inFlightScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val staleFallbacks = StaleFallbackTracker()

    internal fun wasServedStale(key: String): Boolean = staleFallbacks.isStale(key)

    fun clearApiCache() {
        apiCache.clear()
    }

    override suspend fun readHomeSnapshot(kind: HomeFeedKind): PageResult<Illust>? {
        val accountHash = persistence.readStartupAccountHash()
        val snapshot =
            if (accountHash.isNotBlank()) {
                homeSnapshot.readWithAccountHash(kind, accountHash)
            } else {
                null
            }
        val filters = cachedSettings ?: persistence.readFeedSettings()
        return snapshot?.let { it.copy(items = it.items.visibleWithSettings(filters)) }
    }

    override suspend fun readFeedSettings(): AppSettings = persistence.readFeedSettings()

    internal suspend fun <T : Any> withApiCache(
        key: String,
        ttlMillis: Long = CACHE_TTL_STANDARD_MILLIS,
        forceRefresh: Boolean = false,
        block: suspend () -> T,
    ): T {
        if (!forceRefresh) {
            val cached = apiCache.get<T>(key)
            if (cached != null) {
                // A fresh cache hit is real data again, so clear any earlier stale marking.
                staleFallbacks.clear(key)
                return cached
            }
        }
        val generation = accountGeneration
        return try {
            val deferred =
                if (forceRefresh) {
                    inFlightScope.async { block() }
                } else {
                    joinOrStartInFlightRequest(key) { block() }
                }

            // The shared request is untyped by design; the key fully determines the payload type.
            @Suppress("UNCHECKED_CAST")
            val result = deferred.await() as T
            if (accountGeneration == generation) apiCache.put(key, result, ttlMillis = ttlMillis)
            staleFallbacks.clear(key)
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
            if (fallback != null) staleFallbacks.markStale(key)
            fallback ?: throw error
        }
    }

    private suspend fun joinOrStartInFlightRequest(
        key: String,
        block: suspend () -> Any,
    ): Deferred<Any> {
        kotlinx.atomicfu.locks
            .synchronized(inFlightLock) { inFlightRequests[key] }
            ?.let { return it }
        val started =
            inFlightScope.async(start = CoroutineStart.LAZY) {
                try {
                    block()
                } finally {
                    kotlinx.atomicfu.locks.synchronized(inFlightLock) { inFlightRequests.remove(key) }
                }
            }
        val previous =
            kotlinx.atomicfu.locks.synchronized(inFlightLock) {
                val existing = inFlightRequests[key]
                if (existing == null) inFlightRequests[key] = started
                existing
            }
        return if (previous != null) {
            started.cancel()
            previous
        } else {
            started.start()
            started
        }
    }

    override suspend fun readSettings(viewHistoryLimit: Int?): AppSettings {
        val settings =
            settingsCacheMutex.withLock {
                cachedSettings?.takeIf { cachedSettingsRevision == credentialsRevision.get() } ?: run {
                    var revision: Long
                    var loaded: AppSettings
                    do {
                        revision = credentialsRevision.get()
                        loaded = persistence.readSettings(viewHistoryLimit)
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

    override suspend fun readFullViewHistory(): List<Illust> {
        val fullHistory = persistence.readFullViewHistory()
        settingsCacheMutex.withLock {
            cachedSettings = cachedSettings?.copy(viewHistory = fullHistory)
        }
        return fullHistory
    }

    override suspend fun readStartupSettings(): AppSettings = persistence.readStartupSettings()

    override suspend fun saveSettings(
        settings: AppSettings,
        baseSettings: AppSettings?,
    ) {
        val written = persistence.writeSettings(settings, baseSettings)
        settingsCacheMutex.withLock { cachedSettings = written }
        ensureApiClient(NetworkMode.fromCode(written.pixivNetworkMode))
    }

    override suspend fun login(refreshToken: String): PixivSession =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            clearApiCache()
            loginInternal(refreshToken)
        }

    private suspend fun loginInternal(refreshToken: String): PixivSession {
        ensureApiClient(NetworkMode.fromCode(persistence.readAuth().networkMode))
        val nextSession = apiClient.loginWithRefreshToken(refreshToken)
        persistSession(nextSession)
        return nextSession
    }

    override suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            clearApiCache()
            ensureApiClient(NetworkMode.fromCode(persistence.readAuth().networkMode))
            val nextSession = apiClient.loginWithAuthorizationCode(code, codeVerifier)
            persistSession(nextSession)
            nextSession
        }

    private suspend fun persistSession(nextSession: PixivSession) {
        persistence.persistAuth(nextSession)
        session = nextSession
        credentialsRevision.incrementAndGet()
        cachedSettings = null
    }

    override suspend fun logout() =
        sessionMutex.withLock {
            accountRevision.incrementAndGet()
            homeSnapshot.clear()
            session = null
            clearApiCache()
            persistence.clearSensitive()
            cachedSettings =
                settingsCacheMutex
                    .withLock { persistence.readSettings().also { cachedSettings = it } }
                    .also { ensureApiClient(NetworkMode.fromCode(it.pixivNetworkMode)) }
        }

    override suspend fun loadRanking(
        mode: String,
        forceRefresh: Boolean,
    ): PageResult<Illust> =
        withApiCache("ranking:$mode", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.ranking(session, mode) }
        }

    override suspend fun followingIllusts(
        restrict: Restrict,
        forceRefresh: Boolean,
    ): PageResult<Illust> =
        withApiCache("following:$restrict", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.following(session, restrict) }
        }

    override suspend fun loadHome(
        kind: HomeFeedKind,
        forceRefresh: Boolean,
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
                    if (accountGeneration == generation && persistence.readAuth().refreshToken == active.refreshToken) {
                        homeSnapshot.write(kind, active.refreshToken, page)
                    }
                }
                page
            }
        }
    }

    override suspend fun loadNovels(forceRefresh: Boolean): PageResult<NovelPreview> =
        withApiCache("novels:recommended", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.recommendedNovels(session) }
        }

    override suspend fun nextNovelPage(nextUrl: String): PageResult<NovelPreview> =
        withSessionRetry { session -> apiClient.nextNovelPage(session, nextUrl) }

    override suspend fun loadNovelText(
        novelId: Long,
        forceRefresh: Boolean,
    ): NovelTextContent =
        withApiCache("novel_text:$novelId", CACHE_TTL_HOUR_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.novelText(session, novelId) }
        }

    override suspend fun search(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        type: String?,
        forceRefresh: Boolean,
    ): PageResult<Illust> =
        withApiCache("search:$word:$sort:$target:$duration:$bookmarkFilter:$includeR18:$type", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                apiClient.search(session, word, sort, target, duration, bookmarkFilter, includeR18, type = type)
            }
        }

    override suspend fun searchNovels(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        forceRefresh: Boolean,
    ): PageResult<NovelPreview> =
        withApiCache("search_novels:$word:$sort:$target:$duration:$bookmarkFilter:$includeR18", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                apiClient.searchNovels(session, word, sort, target, duration, bookmarkFilter, includeR18)
            }
        }

    override suspend fun searchUsers(
        word: String,
        forceRefresh: Boolean,
    ): PageResult<UserPreview> =
        withApiCache("search_users:$word", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.searchUsers(session, word) }
        }

    override suspend fun trendingTags(forceRefresh: Boolean): List<String> =
        withApiCache(TRENDING_TAGS_CACHE_KEY, CACHE_TTL_LONG_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.trendingTags(session) }
        }

    // True when the last trendingTags() call was answered by the stale offline fallback, so its
    // result must not be recorded as a freshly fetched cache entry.
    override fun trendingTagsServedStale(): Boolean = wasServedStale(TRENDING_TAGS_CACHE_KEY)

    override suspend fun popularPreview(
        word: String,
        forceRefresh: Boolean,
    ): PageResult<Illust> =
        withApiCache("popular_preview:$word", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.popularPreview(session, word) }
        }

    override suspend fun searchAutocomplete(
        word: String,
        forceRefresh: Boolean,
    ): List<String> {
        val trimmed = word.trim()
        if (trimmed.isBlank()) return emptyList()
        // Autocomplete goes stale fast (trending words move), so cache it for a minute at most
        // rather than the 30 minutes used for stable content.
        return withApiCache("autocomplete:${trimmed.lowercase()}", CACHE_TTL_AUTOCOMPLETE_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.searchAutocomplete(session, trimmed) }
        }
    }

    override suspend fun watchlistManga(forceRefresh: Boolean): WatchlistMangaModel =
        withApiCache("watchlist_manga", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.watchlistManga(session) }
        }

    override suspend fun nextWatchlistMangaPage(nextUrl: String): WatchlistMangaModel =
        withSessionRetry { session -> apiClient.nextWatchlistMangaPage(session, nextUrl) }

    override suspend fun illustSeries(illustSeriesId: Long): IllustSeriesWithIdModel =
        withApiCache("illust_series:$illustSeriesId", CACHE_TTL_SERIES_MILLIS, false) {
            withSessionRetry { session -> apiClient.illustSeries(session, illustSeriesId) }
        }

    override suspend fun nextIllustSeriesPage(nextUrl: String): IllustSeriesWithIdModel =
        withSessionRetry { session -> apiClient.nextIllustSeriesPage(session, nextUrl) }

    override suspend fun illustComments(
        illustId: Long,
        offset: Int?,
        forceRefresh: Boolean,
    ): CommentResponse =
        withApiCache("comments:illust:$illustId:${offset ?: 0}", CACHE_TTL_SHORT_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.illustComments(session, illustId, offset) }
        }

    /** 期限内キャッシュ済みのコメント先頭ページ（未取得・期限切れなら null）。 */
    override fun illustCommentsSnapshot(illustId: Long): CommentResponse? = apiCache.get("comments:illust:$illustId:0")

    override suspend fun illustCommentReplies(
        commentId: Long,
        offset: Int?,
        forceRefresh: Boolean,
    ): CommentResponse =
        withApiCache("comments:illust_replies:$commentId:${offset ?: 0}", CACHE_TTL_SHORT_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.illustCommentReplies(session, commentId, offset) }
        }

    override suspend fun novelComments(
        novelId: Long,
        forceRefresh: Boolean,
    ): CommentResponse =
        withApiCache("comments:novel:$novelId", CACHE_TTL_SHORT_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.novelComments(session, novelId) }
        }

    override suspend fun novelCommentReplies(
        commentId: Long,
        forceRefresh: Boolean,
    ): CommentResponse =
        withApiCache("comments:novel_replies:$commentId", CACHE_TTL_SHORT_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.novelCommentReplies(session, commentId) }
        }

    override suspend fun nextCommentPage(nextUrl: String): CommentResponse =
        withSessionRetry { session -> apiClient.nextCommentPage(session, nextUrl) }

    override suspend fun addIllustComment(
        illustId: Long,
        comment: String,
        parentCommentId: Long?,
    ) {
        withSessionRetry { session -> apiClient.addIllustComment(session, illustId, comment, parentCommentId) }
        apiCache.remove("comments:illust:$illustId:0")
        parentCommentId?.let { apiCache.remove("comments:illust_replies:$it:0") }
    }

    override suspend fun addIllustStampComment(
        illustId: Long,
        stampId: Long,
        parentCommentId: Long?,
    ) {
        withSessionRetry { session ->
            apiClient.addIllustStampComment(session, illustId, stampId, parentCommentId)
        }
        apiCache.remove("comments:illust:$illustId:0")
        parentCommentId?.let { apiCache.remove("comments:illust_replies:$it:0") }
    }

    override suspend fun addNovelComment(
        novelId: Long,
        comment: String,
        parentCommentId: Long?,
    ) {
        withSessionRetry { session -> apiClient.addNovelComment(session, novelId, comment, parentCommentId) }
        apiCache.remove("comments:novel:$novelId")
        parentCommentId?.let { apiCache.remove("comments:novel_replies:$it") }
    }

    override suspend fun watchlistMangaAdd(seriesId: Long) {
        withSessionRetry { session -> apiClient.addWatchlistManga(session, seriesId) }
        apiCache.remove("watchlist_manga")
        apiCache.remove("illust_series:$seriesId")
    }

    override suspend fun watchlistMangaDelete(seriesId: Long) {
        withSessionRetry { session -> apiClient.removeWatchlistManga(session, seriesId) }
        apiCache.remove("watchlist_manga")
        apiCache.remove("illust_series:$seriesId")
    }

    override suspend fun followingUsers(
        restrict: Restrict,
        forceRefresh: Boolean,
    ): PageResult<UserPreview> =
        withApiCache("following_users:$restrict", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session ->
                val userId =
                    session.userId
                        ?: throw IllegalStateException("Pixiv user ID is not available.")
                apiClient.followingUsers(session, userId, restrict)
            }
        }

    override suspend fun userDetail(
        userId: Long,
        forceRefresh: Boolean,
    ): UserProfile =
        withApiCache("user_detail:$userId", CACHE_TTL_SERIES_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.userDetail(session, userId) }
        }

    override suspend fun currentUserProfile(): CurrentUserProfile = withSessionRetry { session -> apiClient.currentUserProfile(session) }

    override suspend fun relatedUsers(userId: Long): RelatedUsersResult =
        withSessionRetry { session -> apiClient.relatedUsers(session, userId) }

    override suspend fun nextRelatedUsersPage(nextUrl: String): RelatedUsersResult =
        withSessionRetry { session -> apiClient.nextRelatedUsersPage(session, nextUrl) }

    override suspend fun setUserProfile(profile: UserProfileEdit): AccountEditResult =
        withSessionRetry { session -> apiClient.setUserProfile(session, profile) }

    override suspend fun reportIllust(
        illustId: Long,
        problemType: String?,
        message: String?,
    ) {
        withSessionRetry { session -> apiClient.reportIllust(session, illustId, problemType, message) }
    }

    override suspend fun reportUser(
        userId: Long,
        problemType: String?,
        message: String?,
    ) {
        withSessionRetry { session -> apiClient.reportUser(session, userId, problemType, message) }
    }

    override suspend fun addNovelBookmark(
        novelId: Long,
        restrict: Restrict,
    ) {
        withSessionRetry { session -> apiClient.addNovelBookmark(session, novelId, restrict) }
    }

    override suspend fun removeNovelBookmark(novelId: Long) {
        withSessionRetry { session -> apiClient.removeNovelBookmark(session, novelId) }
    }

    override suspend fun notifications(): NotificationListResult = withSessionRetry { session -> apiClient.notifications(session) }

    override suspend fun notificationViewMore(notificationId: Long): NotificationListResult =
        withSessionRetry { session -> apiClient.notificationViewMore(session, notificationId) }

    override suspend fun nextNotificationPage(nextUrl: String): NotificationListResult =
        withSessionRetry { session -> apiClient.nextNotificationPage(session, nextUrl) }

    override suspend fun stamps(): List<PixivStamp> = withSessionRetry { session -> apiClient.stamps(session) }

    override suspend fun userIllusts(userId: Long): PageResult<Illust> =
        withSessionRetry { session -> apiClient.userIllusts(session, userId) }

    override suspend fun illustDetail(
        illustId: Long,
        forceRefresh: Boolean,
    ): Illust =
        withApiCache("illust_detail:$illustId", CACHE_TTL_SERIES_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.illustDetail(session, illustId) }
        }

    override suspend fun ugoiraMetadata(
        illustId: Long,
        forceRefresh: Boolean,
    ): UgoiraMetadataResponse =
        withApiCache("ugoira_meta:$illustId", CACHE_TTL_LONG_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.ugoiraMetadata(session, illustId) }
        }

    override suspend fun relatedIllusts(
        illustId: Long,
        forceRefresh: Boolean,
    ): PageResult<Illust> =
        withApiCache("related_illusts:$illustId", CACHE_TTL_STANDARD_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.relatedIllusts(session, illustId) }
        }

    override suspend fun followUser(
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

    override suspend fun unfollowUser(userId: Long) {
        withSessionRetry { session -> apiClient.unfollowUser(session, userId) }
        apiCache.removeByPrefix("following:")
        apiCache.removeByPrefix("following_users:")
        apiCache.remove("user_detail:$userId")
        apiCache.remove("user_follow_detail:$userId")
    }

    override suspend fun bookmarks(
        userId: Long,
        restrict: Restrict,
        forceRefresh: Boolean,
        tag: String?,
    ): PageResult<Illust> =
        withApiCache("bookmarks:$userId:$restrict:$tag", CACHE_TTL_MEDIUM_MILLIS, forceRefresh) {
            withSessionRetry { session -> apiClient.bookmarks(session, userId, restrict, tag) }
        }

    override suspend fun illustBookmarkTags(
        userId: Long,
        restrict: Restrict,
    ): List<BookmarkTag> = withSessionRetry { session -> apiClient.illustBookmarkTags(session, userId, restrict) }

    override suspend fun nextPage(nextUrl: String): PageResult<Illust> =
        withSessionRetry { session -> apiClient.nextIllustPage(session, nextUrl) }

    override suspend fun nextUserSearchPage(nextUrl: String): PageResult<UserPreview> =
        withSessionRetry { session -> apiClient.nextUserPreviewPage(session, nextUrl) }

    override suspend fun toggleBookmark(
        illust: Illust,
        restrict: Restrict,
    ): Illust {
        val bookmarked = !illust.isBookmarked
        setBookmarkState(illust.id, bookmarked, restrict)
        return illust.copy(isBookmarked = bookmarked)
    }

    /**
     * Applies one idempotent bookmark state.
     *
     * A retried request can lose its response, so the desired state is sent instead of a toggle.
     */
    internal suspend fun setBookmarkState(
        illustId: Long,
        bookmarked: Boolean,
        restrict: Restrict,
        tags: List<String> = emptyList(),
    ) {
        withSessionRetry { session ->
            if (bookmarked) {
                apiClient.addBookmark(session, illustId, restrict, tags)
            } else {
                try {
                    apiClient.removeBookmark(session, illustId)
                } catch (failure: PixivApiException) {
                    // The desired state is already true; a retried delete can report either.
                    if (failure.statusCode != HTTP_NOT_FOUND && failure.statusCode != HTTP_GONE) throw failure
                }
            }
        }
        apiCache.removeByPrefix("bookmarks:")
        apiCache.remove("illust_detail:$illustId")
    }

    suspend fun deleteIllustComment(commentId: Long) {
        withSessionRetry { session -> apiClient.deleteIllustComment(session, commentId) }
        apiCache.removeByPrefix("comments:illust:")
        apiCache.removeByPrefix("comments:illust_replies:")
    }

    suspend fun isAiContentVisible(): Boolean = withSessionRetry { session -> apiClient.isAiContentVisible(session) }

    suspend fun setAiContentVisible(visible: Boolean) {
        withSessionRetry { session -> apiClient.setAiContentVisible(session, visible) }
    }

    private suspend fun requireSession(): PixivSession {
        val now = currentTimeMillis()
        val currentSession = session
        if (currentSession != null &&
            (currentSession.expiresAtMillis == 0L || currentSession.expiresAtMillis > now + SESSION_EXPIRY_SKEW_MILLIS)
        ) {
            return currentSession
        }
        return sessionMutex.withLock {
            val lockedNow = currentTimeMillis()
            val lockedSession = session
            if (lockedSession != null &&
                (lockedSession.expiresAtMillis == 0L || lockedSession.expiresAtMillis > lockedNow + SESSION_EXPIRY_SKEW_MILLIS)
            ) {
                return@withLock lockedSession
            }
            val auth = persistence.readAuth()
            require(
                auth.refreshToken.isNotBlank() && auth.refreshToken != STARTUP_TOKEN_PLACEHOLDER,
            ) { "Pixiv refresh token が未設定です。" }
            ensureApiClient(NetworkMode.fromCode(auth.networkMode))
            loginInternal(auth.refreshToken)
        }
    }

    private suspend fun <T> withSessionRetry(block: suspend (PixivSession) -> T): T {
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
            val refreshToken = persistence.readAuth().refreshToken
            session?.takeIf { it.accessToken != failed.accessToken && it.refreshToken == refreshToken }?.let { return@withLock it }
            require(refreshToken.isNotBlank() && refreshToken != STARTUP_TOKEN_PLACEHOLDER) { "Pixiv refresh token が未設定です。" }
            loginInternal(refreshToken)
        }

    private fun ensureApiClient(mode: NetworkMode) {
        if (apiClientMode == mode && apiClientInstance != null) return
        synchronized(clientLock) {
            if (apiClientMode != mode || apiClientInstance == null) {
                val client = createPixivKtorClient(engineFactory(mode))
                apiClientInstance = PixivApiClient(client)
                ktorClient = client
                apiClientMode = mode
            }
        }
    }
}

/** プラットフォーム非依存の単純なカウンタ（`AtomicLong` の代替）。 */
internal class AtomicLongCounter {
    private val lock = kotlinx.atomicfu.locks.SynchronizedObject()
    private var value = 0L

    fun get(): Long = kotlinx.atomicfu.locks.synchronized(lock) { value }

    fun incrementAndGet(): Long =
        kotlinx.atomicfu.locks.synchronized(lock) {
            value += 1
            value
        }
}
