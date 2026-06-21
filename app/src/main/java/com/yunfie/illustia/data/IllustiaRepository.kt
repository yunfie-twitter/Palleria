package com.yunfie.illustia.data

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
            bookmarkUserId = current.bookmarkUserId ?: nextSession.userId,
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
        return apiClient.ranking(requireSession(), mode)
    }

    suspend fun followingIllusts(restrict: Restrict): PageResult<Illust> {
        return apiClient.following(requireSession(), restrict)
    }

    suspend fun loadHome(kind: HomeFeedKind): PageResult<Illust> {
        val active = requireSession()
        return when (kind) {
            HomeFeedKind.Recommended -> apiClient.recommended(active)
            HomeFeedKind.Ranking -> apiClient.ranking(active) // Default to day
            HomeFeedKind.New -> apiClient.newest(active)
        }
    }

    suspend fun search(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
    ): PageResult<Illust> {
        return apiClient.search(requireSession(), word, sort, target, duration, bookmarkFilter, includeR18)
    }

    suspend fun searchUsers(word: String): PageResult<UserPreview> {
        return apiClient.searchUsers(requireSession(), word)
    }

    suspend fun followingUsers(restrict: Restrict): PageResult<UserPreview> {
        val userId = requireSession().userId
            ?: throw IllegalStateException("Pixiv user ID is not available.")
        return apiClient.followingUsers(requireSession(), userId, restrict)
    }

    suspend fun userDetail(userId: Long): UserProfile {
        return apiClient.userDetail(requireSession(), userId)
    }

    suspend fun userIllusts(userId: Long): PageResult<Illust> {
        return apiClient.userIllusts(requireSession(), userId)
    }

    suspend fun illustDetail(illustId: Long): Illust {
        return apiClient.illustDetail(requireSession(), illustId)
    }

    suspend fun relatedIllusts(illustId: Long): PageResult<Illust> {
        return apiClient.relatedIllusts(requireSession(), illustId)
    }

    suspend fun followUser(userId: Long, restrict: Restrict) {
        apiClient.followUser(requireSession(), userId, restrict)
    }

    suspend fun unfollowUser(userId: Long) {
        apiClient.unfollowUser(requireSession(), userId)
    }

    suspend fun bookmarks(userId: Long, restrict: Restrict): PageResult<Illust> {
        return apiClient.bookmarks(requireSession(), userId, restrict)
    }

    suspend fun nextPage(nextUrl: String): PageResult<Illust> {
        return apiClient.nextIllustPage(requireSession(), nextUrl)
    }

    suspend fun nextUserSearchPage(nextUrl: String): PageResult<UserPreview> {
        return apiClient.nextUserPreviewPage(requireSession(), nextUrl)
    }

    suspend fun toggleBookmark(illust: Illust, restrict: Restrict): Illust {
        val active = requireSession()
        return if (illust.isBookmarked) {
            apiClient.removeBookmark(active, illust.id)
            illust.copy(isBookmarked = false)
        } else {
            apiClient.addBookmark(active, illust.id, restrict)
            illust.copy(isBookmarked = true)
        }
    }

    private suspend fun requireSession(): PixivSession {
        session?.let { return it }
        val refreshToken = readSettings().refreshToken
        require(refreshToken.isNotBlank()) { "Pixiv refresh token が未設定です。" }
        return login(refreshToken)
    }
}
