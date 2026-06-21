package com.yunfie.illustia

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.data.HomeFeedKind
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.PixivApiClient
import com.yunfie.illustia.data.PixivApiException
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.data.Restrict
import com.yunfie.illustia.data.SearchBookmarkFilter
import com.yunfie.illustia.data.SearchDuration
import com.yunfie.illustia.data.SearchSort
import com.yunfie.illustia.data.SearchTarget
import com.yunfie.illustia.data.StoredAccount
import com.yunfie.illustia.data.UserPreview
import com.yunfie.illustia.data.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@Keep
class IllustiaViewModel(app: Application) : AndroidViewModel(app) {
    private val repository by lazy {
        IllustiaRepository(
            SettingsStore(getApplication<Application>().applicationContext),
            PixivApiClient((getApplication<Application>() as IllustiaApplication).sharedHttpClient),
        )
    }
    private val imageStore by lazy { NativeImageStore(getApplication<Application>().applicationContext) }
    private val downloadMutex = Mutex()
    private var searchJob: Job? = null
    private var detailExtrasJob: Job? = null
    private var loadingJob: Job? = null
    private val downloadClient: OkHttpClient by lazy {
        (getApplication<Application>() as IllustiaApplication).sharedHttpClient.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    private val _uiState = MutableStateFlow(IllustiaUiState())
    val uiState: StateFlow<IllustiaUiState> = _uiState
    val loadStateState: StateFlow<LoadState> = _uiState
        .map { it.loadState }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.loadState)

    private fun str(resId: Int): String = getApplication<Application>().getString(resId)
    private fun str(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)
    private val _navigationRequests = MutableSharedFlow<IllustiaNavigationRequest>(extraBufferCapacity = 16)
    val navigationRequests: SharedFlow<IllustiaNavigationRequest> = _navigationRequests
    val settingsState: StateFlow<AppSettings> = _uiState
        .map { it.settings }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.settings)
    val homeItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.homeItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.homeItems)
    val searchItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.searchItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.searchItems)
    val rankingItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.rankingItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.rankingItems)
    val bookmarkItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.bookmarkItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.bookmarkItems)
    val timelineItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.timelineItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.timelineItems)
    val watchlistItemsState: StateFlow<List<Illust>> = _uiState
        .map { it.watchlistItems }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.watchlistItems)
    val followingUsersState: StateFlow<List<UserPreview>> = _uiState
        .map { it.followingUsers }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.followingUsers)
    val homeChromeState: StateFlow<HomeChromeState> = _uiState
        .map { HomeChromeState(it.homeKind, it.homeNextUrl, it.timelineNextUrl) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeChromeState())
    val rankingChromeState: StateFlow<RankingChromeState> = _uiState
        .map { RankingChromeState(it.rankingMode, it.rankingNextUrl) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RankingChromeState())
    val bookmarkChromeState: StateFlow<BookmarkChromeState> = _uiState
        .map {
            BookmarkChromeState(
                bookmarkNextUrl = it.bookmarkNextUrl,
                timelineNextUrl = it.timelineNextUrl,
                watchlistNextUrl = it.watchlistNextUrl,
                activeWatchlistTag = it.activeWatchlistTag,
                followingUsersNextUrl = it.followingUsersNextUrl,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarkChromeState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = repository.readSettings()
            _uiState.update { it.withSettings(settings).copy(settingsLoaded = true) }
            if (settings.refreshToken.isNotBlank()) {
                refreshCurrentAccountProfile(settings)
                if (settings.startupScreen == "home") {
                    refreshHome()
                }
            }
        }
    }

    fun loadInitialHomeIfNeeded() {
        val state = _uiState.value
        if (
            state.settings.refreshToken.isBlank() ||
            state.settings.startupScreen != "home" ||
            state.homeItems.isNotEmpty() ||
            state.loadState == LoadState.Loading
        ) {
            return
        }
        refreshHome()
    }

    fun updateRefreshToken(value: String) {
        updateSettings { it.copy(refreshToken = value) }
    }

    fun updateBookmarkUserId(value: String) {
        updateSettings { it.copy(bookmarkUserId = value.toLongOrNull()) }
        viewModelScope.launch(Dispatchers.IO) {
            refreshCurrentAccountProfile(_uiState.value.settings)
        }
    }

    fun updateAppLanguage(value: String) {
        updateSettings { it.copy(appLanguage = value) }
    }

    fun updateAllowR18(value: Boolean) {
        updateSettings { it.copy(allowR18 = value) }
    }

    fun updateHighQuality(value: Boolean) {
        updateSettings { it.copy(highQualityImages = value) }
    }

    fun updateSmoothTransitions(value: Boolean) {
        updateSettings { it.copy(smoothTransitions = value) }
    }

    fun updatePrefetchImages(value: Boolean) {
        updateSettings { it.copy(prefetchImages = value) }
    }

    fun updateNotchOptimization(value: Boolean) {
        updateSettings { it.copy(notchOptimization = value) }
    }

    fun updateConfirmOnLongPressSave(value: Boolean) {
        updateSettings { it.copy(confirmOnLongPressSave = value) }
    }

    fun updateDoubleBackToExit(value: Boolean) {
        updateSettings { it.copy(doubleBackToExit = value) }
    }

    fun updateSwipeToSwitchWorks(value: Boolean) {
        updateSettings { it.copy(swipeToSwitchWorks = value) }
    }

    fun updateSecureWindow(value: Boolean) {
        updateSettings { it.copy(secureWindow = value) }
    }

    fun updateAmoledMode(value: Boolean) {
        updateSettings { it.copy(amoledMode = value) }
    }

    fun updateSkipConfirmOnDetailSave(value: Boolean) {
        updateSettings { it.copy(skipConfirmOnDetailSave = value) }
    }

    fun updateShowAiBadge(value: Boolean) {
        updateSettings { it.copy(showAiBadge = value) }
    }

    fun updateFollowOnLike(value: Boolean) {
        updateSettings { it.copy(followOnLike = value) }
    }

    fun updatePrivateBookmarkDefault(value: Boolean) {
        updateSettings { it.copy(privateBookmarkDefault = value) }
    }

    fun updateAutoDownloadOnBookmark(value: Boolean) {
        updateSettings { it.copy(autoDownloadOnBookmark = value) }
    }

    fun updateAutoBookmarkOnDownload(value: Boolean) {
        updateSettings { it.copy(autoBookmarkOnDownload = value) }
    }

    fun updateAutoTagOnBookmark(value: Boolean) {
        updateSettings { it.copy(autoTagOnBookmark = value) }
    }

    fun updateSimultaneousDownloads(value: Int) {
        updateSettings { it.copy(simultaneousDownloads = value.coerceIn(1, 4)) }
    }

    fun updateFeedPreviewQuality(value: String) {
        updateSettings { it.copy(feedPreviewQuality = value) }
    }

    fun updateIllustDetailQuality(value: String) {
        updateSettings { it.copy(illustDetailQuality = value) }
    }

    fun updateMangaDetailQuality(value: String) {
        updateSettings { it.copy(mangaDetailQuality = value) }
    }

    fun updateFullscreenQuality(value: String) {
        updateSettings { it.copy(fullscreenQuality = value) }
    }

    fun updateViewerThumbnailsInToolbar(value: Boolean) {
        updateSettings { it.copy(viewerThumbnailsInToolbar = value) }
    }

    fun updateStartupScreen(value: String) {
        updateSettings { it.copy(startupScreen = value) }
    }

    fun updateVerticalColumnCount(value: Int) {
        updateSettings { it.copy(verticalColumnCount = value.coerceIn(2, 4)) }
    }

    fun updateHorizontalColumnCount(value: Int) {
        updateSettings { it.copy(horizontalColumnCount = value.coerceIn(3, 6)) }
    }

    fun updatePixivImageProxyBaseUrl(value: String) {
        updateSettings { it.copy(pixivImageProxyBaseUrl = value) }
    }

    fun updateRestrict(value: Restrict) {
        updateSettings { it.copy(bookmarkRestrict = value) }
    }

    fun updateSearchSort(value: SearchSort) {
        updateSettings { it.copy(searchSort = value) }
        refreshActiveSearch()
    }

    fun updateSearchTarget(value: SearchTarget) {
        updateSettings { it.copy(searchTarget = value) }
        refreshActiveSearch()
    }

    fun updateSearchDuration(value: SearchDuration) {
        updateSettings { it.copy(searchDuration = value) }
        refreshActiveSearch()
    }

    fun updateSearchBookmarkFilter(value: SearchBookmarkFilter) {
        updateSettings { it.copy(searchBookmarkFilter = value) }
        refreshActiveSearch()
    }

    fun updateSearchUsersEnabled(value: Boolean) {
        updateSettings { it.copy(searchUsersEnabled = value) }
        refreshActiveSearch()
    }

    fun updateSearchDraft(value: String) {
        _uiState.update { it.copy(searchDraft = value) }
    }

    private fun refreshActiveSearch() {
        val word = _uiState.value.activeSearchWord
        if (word.isNotBlank()) {
            submitSearch(word)
        }
    }

    fun login() {
        val refreshToken = _uiState.value.settings.refreshToken
        runLoading {
            val session = repository.login(refreshToken)
            applyLoggedInSession(session.accessToken.isNotBlank(), str(R.string.msg_pixiv_connected))
            loadHomeInternal(_uiState.value.homeKind)
        }
    }

    fun openWebLogin() {
        _uiState.update {
            it.copy(
                webLoginRequest = createPixivWebLoginRequest(),
                showReloginRequiredDialog = false,
                message = null,
            )
        }
    }

    fun closeWebLogin() {
        _uiState.update { it.copy(webLoginRequest = null) }
    }

    fun dismissReloginRequiredDialog() {
        _uiState.update { it.copy(showReloginRequiredDialog = false) }
    }

    fun failWebLogin(message: String) {
        _uiState.update {
            it.copy(
                webLoginRequest = null,
                loadState = LoadState.Error(message),
            )
        }
    }

    fun completeWebLogin(code: String) {
        val request = _uiState.value.webLoginRequest ?: return
        runLoading {
            val session = repository.loginWithAuthorizationCode(code, request.codeVerifier)
            applyLoggedInSession(session.accessToken.isNotBlank(), str(R.string.msg_web_login_complete))
            loadHomeInternal(_uiState.value.homeKind)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            val nextSettings = repository.readSettings()
            _uiState.update {
                IllustiaUiState(
                    settings = nextSettings,
                    settingsLoaded = true,
                    message = str(R.string.msg_logged_out),
                )
            }
        }
    }

    fun selectHomeKind(kind: HomeFeedKind) {
        _uiState.update { it.copy(homeKind = kind) }
        refreshHome()
    }

    fun selectRankingMode(mode: String) {
        _uiState.update { it.copy(rankingMode = mode) }
        refreshRanking()
    }

    fun refreshHome() {
        runLoading {
            loadHomeInternal(_uiState.value.homeKind)
        }
    }

    fun refreshRanking() {
        runLoading {
            val page = repository.loadRanking(_uiState.value.rankingMode)
            val settings = _uiState.value.settings
            val items = withContext(Dispatchers.Default) {
                page.items.visibleWithSettings(settings)
            }
            _uiState.update {
                it.copy(
                    rankingItems = items,
                    rankingNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreRanking() {
        val nextUrl = _uiState.value.rankingNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            val settings = _uiState.value.settings
            _uiState.update {
                it.copy(
                    rankingItems = it.rankingItems.appendIllusts(page.items.visibleWithSettings(settings)),
                    rankingNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreHome() {
        val nextUrl = _uiState.value.homeNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            val settings = _uiState.value.settings
            _uiState.update {
                it.copy(
                    homeItems = it.homeItems.appendIllusts(page.items.visibleWithSettings(settings)),
                    homeNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun submitSearch(word: String = _uiState.value.searchDraft) {
        val normalized = word.trim()
        if (normalized.isBlank()) return
        when (val event = NativeIntentRouter.parseText(normalized)) {
            is NativeIntentEvent.Artwork -> {
                _uiState.update { it.copy(searchDraft = "") }
                openIllust(event.id)
                return
            }
            is NativeIntentEvent.User -> {
                _uiState.update { it.copy(searchDraft = "") }
                openUserPage(event.id)
                return
            }
            else -> Unit
        }
        val settings = _uiState.value.settings
        val history = (listOf(normalized) + settings.searchHistory)
            .distinct()
            .take(6)
        updateSettings { it.copy(searchHistory = history) }
        _uiState.update {
            it.copy(
                searchDraft = normalized,
                activeSearchWord = normalized,
                searchItems = emptyList(),
                searchNextUrl = null,
                userSearchItems = emptyList(),
                userSearchNextUrl = null,
            )
        }
        searchJob?.cancel()
        searchJob = runLoading {
            kotlinx.coroutines.coroutineScope {
                val pageDeferred = async {
                    val currentSettings = _uiState.value.settings
                    repository.search(
                        word = normalized,
                        sort = currentSettings.searchSort,
                        target = currentSettings.searchTarget,
                        duration = currentSettings.searchDuration,
                        bookmarkFilter = currentSettings.searchBookmarkFilter,
                        includeR18 = currentSettings.allowR18,
                    )
                }
                val usersDeferred = if (_uiState.value.settings.searchUsersEnabled) {
                    async { repository.searchUsers(normalized) }
                } else {
                    null
                }

                val page = pageDeferred.await()
                val users = usersDeferred?.await()

                _uiState.update {
                    it.copy(
                        searchItems = page.items.visibleWithSettings(it.settings),
                        searchNextUrl = page.nextUrl,
                        userSearchItems = users?.items.orEmpty(),
                        userSearchNextUrl = users?.nextUrl,
                    )
                }
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchDraft = "",
                activeSearchWord = "",
                searchItems = emptyList(),
                searchNextUrl = null,
                userSearchItems = emptyList(),
                userSearchNextUrl = null,
            )
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun handleIncomingIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "pixiv" && uri.host == "account" && uri.path == "/login") {
                uri.getQueryParameter("code")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::completeWebLogin)
                return
            }
        }
        when (val event = NativeIntentRouter.parse(intent)) {
            is NativeIntentEvent.Artwork -> openIllust(event.id)
            is NativeIntentEvent.User -> openUserPage(event.id)
            is NativeIntentEvent.Text -> submitSearch(event.value)
            is NativeIntentEvent.Image -> _uiState.update {
                it.copy(message = str(R.string.msg_shared_image_received))
            }
            null -> Unit
        }
    }

    fun handleClipboardText(value: String) {
        when (val event = NativeIntentRouter.parseText(value)) {
            is NativeIntentEvent.Artwork -> openIllust(event.id)
            is NativeIntentEvent.User -> openUserPage(event.id)
            else -> Unit
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearSearchHistory() {
        updateSettings { it.copy(searchHistory = emptyList()) }
    }

    fun clearFavoriteTags() {
        updateSettings { it.copy(favoriteTags = emptyList()) }
        _uiState.update { it.copy(message = str(R.string.msg_watchlist_tags_deleted)) }
    }

    fun clearMuteData() {
        updateSettings {
            it.copy(
                mutedIllusts = emptyList(),
                mutedUsers = emptyList(),
                mutedTags = emptyList(),
            )
        }
        _uiState.update { it.copy(message = str(R.string.msg_mute_data_deleted)) }
    }

    fun toggleFavoriteTag(rawTag: String) {
        val tag = rawTag.trim().removePrefix("#")
        if (tag.isBlank()) return
        val current = _uiState.value.settings.favoriteTags
        val next = if (tag in current) current.filterNot { it == tag } else (listOf(tag) + current).distinct().take(24)
        updateSettings { it.copy(favoriteTags = next) }
        _uiState.update { it.copy(message = if (tag in current) str(R.string.msg_watchlist_tag_removed, tag) else str(R.string.msg_watchlist_tag_added, tag)) }
    }

    fun refreshTimeline() {
        runLoading {
            val page = repository.followingIllusts(_uiState.value.settings.bookmarkRestrict)
            _uiState.update {
                it.copy(
                    timelineItems = page.items.visibleWithSettings(it.settings),
                    timelineNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreTimeline() {
        val nextUrl = _uiState.value.timelineNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    timelineItems = it.timelineItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    timelineNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadWatchlistTag(tag: String) {
        val normalized = tag.trim().removePrefix("#")
        if (normalized.isBlank()) return
        runLoading {
            val page = repository.search(
                word = normalized,
                sort = _uiState.value.settings.searchSort,
                target = _uiState.value.settings.searchTarget,
                duration = _uiState.value.settings.searchDuration,
                bookmarkFilter = _uiState.value.settings.searchBookmarkFilter,
                includeR18 = _uiState.value.settings.allowR18,
            )
            _uiState.update {
                it.copy(
                    activeWatchlistTag = normalized,
                    watchlistItems = page.items.visibleWithSettings(it.settings),
                    watchlistNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreWatchlist() {
        val nextUrl = _uiState.value.watchlistNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    watchlistItems = it.watchlistItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    watchlistNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun refreshFollowingUsers() {
        runLoading {
            val page = repository.followingUsers(_uiState.value.settings.bookmarkRestrict)
            _uiState.update {
                it.copy(
                    followingUsers = page.items,
                    followingUsersNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreFollowingUsers() {
        val nextUrl = _uiState.value.followingUsersNextUrl ?: return
        runLoading {
            val page = repository.nextUserSearchPage(nextUrl)
            _uiState.update {
                it.copy(
                    followingUsers = it.followingUsers + page.items,
                    followingUsersNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun clearViewHistory() {
        updateSettings { it.copy(viewHistory = emptyList()) }
    }

    fun exportManagedData(uri: Uri) {
        val settings = _uiState.value.settings
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val backup = JSONObject().apply {
                    put("format", "illustia-data-backup")
                    put("version", 1)
                    put("exportedAt", System.currentTimeMillis())
                    put("viewHistory", JSONArray().apply {
                        settings.viewHistory.forEach { illust ->
                            put(JSONObject().apply {
                                put("id", illust.id)
                                put("title", illust.title)
                                put("type", illust.type)
                                put("artistId", illust.artistId)
                                put("artistName", illust.artistName)
                                put("imageUrl", illust.imageUrl)
                                put("pageCount", illust.pageCount)
                            })
                        }
                    })
                    put("searchHistory", JSONArray(settings.searchHistory))
                    put("favoriteTags", JSONArray(settings.favoriteTags))
                    put("mutedIllusts", JSONArray(settings.mutedIllusts))
                    put("mutedUsers", JSONArray(settings.mutedUsers))
                    put("mutedTags", JSONArray(settings.mutedTags))
                }
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                    writer.write(backup.toString(2))
                } ?: error("Unable to open export destination")
            }.onSuccess {
                _uiState.update { it.copy(message = str(R.string.msg_data_exported)) }
            }.onFailure {
                _uiState.update { it.copy(message = str(R.string.error_data_export_failed)) }
            }
        }
    }

    fun importManagedData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val text = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open import source")
                val backup = JSONObject(text)
                require(backup.optString("format") == "illustia-data-backup")
                require(backup.optInt("version", 0) == 1)

                val current = _uiState.value.settings
                val viewHistory = backup.requireArray("viewHistory").jsonObjects().mapNotNull { item ->
                    val id = item.optLong("id", 0L).takeIf { it > 0L } ?: return@mapNotNull null
                    val imageUrl = item.optString("imageUrl")
                    Illust(
                        id = id,
                        title = item.optString("title"),
                        type = item.optString("type").ifBlank { "illust" },
                        caption = "",
                        artistId = item.optLong("artistId", 0L),
                        artistName = item.optString("artistName"),
                        artistAvatarUrl = null,
                        squareImageUrl = "",
                        mediumImageUrl = imageUrl,
                        imageUrl = imageUrl,
                        originalImageUrl = null,
                        tags = emptyList(),
                        pageCount = item.optInt("pageCount", 1).coerceAtLeast(1),
                        isBookmarked = false,
                    )
                }.distinctBy { it.id }.take(48)

                val imported = current.copy(
                    viewHistory = viewHistory,
                    searchHistory = backup.requireArray("searchHistory").strings().distinct().take(6),
                    favoriteTags = backup.requireArray("favoriteTags").strings().distinct().take(24),
                    mutedIllusts = backup.requireArray("mutedIllusts").longs().distinct(),
                    mutedUsers = backup.requireArray("mutedUsers").longs().distinct(),
                    mutedTags = backup.requireArray("mutedTags").strings().distinct(),
                )
                repository.saveSettings(imported)
                imported
            }.onSuccess { imported ->
                _uiState.update { it.withSettings(imported).copy(message = str(R.string.msg_data_imported)) }
            }.onFailure {
                _uiState.update { it.copy(message = str(R.string.error_data_import_failed)) }
            }
        }
    }

    fun openIllust(illust: Illust) {
        val history = (listOf(illust) + _uiState.value.settings.viewHistory)
            .distinctBy { it.id }
            .take(48)
        updateSettings { it.copy(viewHistory = history) }
        _uiState.update { it.copy(selectedIllust = illust, selectedIllustUser = null, relatedIllusts = emptyList()) }
        detailExtrasJob?.cancel()
        detailExtrasJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.coroutineScope {
                    val relatedDeferred = async { repository.relatedIllusts(illust.id) }
                    val userDeferred = illust.artistId.takeIf { it > 0L }?.let { artistId ->
                        async { repository.userDetail(artistId) }
                    }
                    val related = relatedDeferred.await()
                    val user = userDeferred?.await()
                    _uiState.update {
                        if (it.selectedIllust?.id != illust.id) {
                            it
                        } else {
                            it.copy(
                                relatedIllusts = related.items.visibleWithSettings(it.settings),
                                selectedIllustUser = user,
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update {
                    if (it.selectedIllust?.id == illust.id) {
                        it.copy(message = cleanErrorMessage(error, str(R.string.error_load_detail_failed)))
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun openIllust(illustId: Long) {
        findIllustById(illustId)?.let { illust ->
            openIllust(illust)
            return
        }
        runLoading {
            val illust = repository.illustDetail(illustId)
            openIllust(illust)
        }
    }

    fun closeIllust() {
        detailExtrasJob?.cancel()
        _uiState.update { it.copy(selectedIllust = null, selectedIllustUser = null) }
    }

    fun openImageViewer(illust: Illust, startPage: Int = 0) {
        _uiState.update { it.copy(imageViewerIllust = illust, imageViewerStartPage = startPage.coerceAtLeast(0)) }
    }

    fun closeImageViewer() {
        _uiState.update { it.copy(imageViewerIllust = null, imageViewerStartPage = 0) }
    }

    fun onIllustLongPress(illust: Illust) {
        _uiState.update { it.copy(longPressedIllust = illust) }
    }

    fun onIllustLongPress(illustId: Long, fallback: Illust? = null) {
        val illust = findIllustById(illustId) ?: fallback ?: return
        onIllustLongPress(illust)
    }

    fun closeIllustOptions() {
        _uiState.update { it.copy(longPressedIllust = null) }
    }

    fun openUser(user: UserPreview) {
        openUser(user.id)
    }

    fun openUser(userId: Long) {
        if (userId <= 0L) {
            _uiState.update { it.copy(message = str(R.string.error_load_artist_failed)) }
            return
        }
        runLoading {
            val profile = repository.userDetail(userId)
            val page = repository.userIllusts(userId)
            _uiState.update {
                it.copy(
                    selectedUser = profile,
                    selectedUserIllusts = page.items.visibleWithSettings(it.settings),
                    selectedUserNextUrl = page.nextUrl,
                    selectedUserBookmarks = emptyList(),
                    selectedUserBookmarksNextUrl = null,
                    showUserPage = false,
                    userPageFromSheet = false,
                )
            }
        }
    }

    fun closeUser() {
        _uiState.update {
            it.copy(
                selectedUser = null,
                selectedUserIllusts = emptyList(),
                selectedUserNextUrl = null,
                selectedUserBookmarks = emptyList(),
                selectedUserBookmarksNextUrl = null,
                showUserPage = false,
                userPageFromSheet = false,
            )
        }
    }

    fun openUserPage(user: UserPreview) {
        openUserPage(user.id)
    }

    fun openUserPage(userId: Long) {
        if (userId <= 0L) {
            _uiState.update { it.copy(message = str(R.string.error_load_artist_failed)) }
            return
        }
        runLoading {
            val profile = repository.userDetail(userId)
            val page = repository.userIllusts(userId)
            _uiState.update {
                it.copy(
                    selectedUser = profile,
                    selectedUserIllusts = page.items.visibleWithSettings(it.settings),
                    selectedUserNextUrl = page.nextUrl,
                    selectedUserBookmarks = emptyList(),
                    selectedUserBookmarksNextUrl = null,
                    showUserPage = true,
                    userPageFromSheet = false,
                )
            }
        }
    }

    fun closeUserPage() {
        _uiState.update {
            it.copy(
                showUserPage = false,
                userPageFromSheet = false,
                selectedUser = null,
                selectedUserIllusts = emptyList(),
                selectedUserNextUrl = null,
                selectedUserBookmarks = emptyList(),
                selectedUserBookmarksNextUrl = null,
            )
        }
    }

    fun collapseUserPageToSheet() {
        if (_uiState.value.userPageFromSheet) {
            _uiState.update { it.copy(showUserPage = false) }
        } else {
            closeUserPage()
        }
    }

    fun expandUserSheetToPage() {
        _uiState.update { it.copy(showUserPage = true, userPageFromSheet = true) }
    }

    fun toggleFollow(user: UserProfile) {
        runLoading {
            if (user.isFollowed) {
                repository.unfollowUser(user.id)
            } else {
                repository.followUser(user.id, _uiState.value.settings.bookmarkRestrict)
            }
            val updated = repository.userDetail(user.id)
            _uiState.update { state ->
                state.copy(
                    selectedUser = if (state.selectedUser?.id == user.id) updated else state.selectedUser,
                    selectedIllustUser = if (state.selectedIllustUser?.id == user.id) updated else state.selectedIllustUser,
                    userSearchItems = state.userSearchItems.map {
                        if (it.id == user.id) it.copy(isFollowed = updated.isFollowed) else it
                    }
                )
            }
        }
    }

    fun muteIllust(id: Long) {
        updateSettings { it.copy(mutedIllusts = (it.mutedIllusts + id).distinct()) }
        removeMutedFromVisibleLists()
    }

    fun muteUser(id: Long) {
        updateSettings { it.copy(mutedUsers = (it.mutedUsers + id).distinct()) }
        removeMutedFromVisibleLists()
    }

    fun muteTag(tag: String) {
        updateSettings { it.copy(mutedTags = (it.mutedTags + tag).distinct()) }
        removeMutedFromVisibleLists()
    }

    fun unmuteIllust(id: Long) {
        updateSettings { it.copy(mutedIllusts = it.mutedIllusts.filterNot { mutedId -> mutedId == id }) }
    }

    fun unmuteUser(id: Long) {
        updateSettings { it.copy(mutedUsers = it.mutedUsers.filterNot { mutedId -> mutedId == id }) }
    }

    fun unmuteTag(tag: String) {
        updateSettings { it.copy(mutedTags = it.mutedTags.filterNot { mutedTag -> mutedTag == tag }) }
    }

    fun saveImage(url: String, filename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            acquireDownloadSlot()
            _uiState.update { it.copy(loadState = LoadState.Loading, message = str(R.string.msg_image_saving, it.activeDownloads, it.settings.simultaneousDownloads.coerceIn(1, 4))) }
            try {
                downloadImageToGallery(url, filename)
                val currentIllust = _uiState.value.selectedIllust
                if (
                    _uiState.value.settings.autoBookmarkOnDownload &&
                    currentIllust != null &&
                    !currentIllust.isBookmarked &&
                    currentIllust.hasImageUrl(url)
                ) {
                    val settings = _uiState.value.settings
                    val restrict = if (settings.privateBookmarkDefault) Restrict.Private else settings.bookmarkRestrict
                    val updated = repository.toggleBookmark(currentIllust, restrict)
                    updateIllustEverywhere(updated)
                }
                _uiState.update { it.copy(loadState = LoadState.Loaded, message = str(R.string.msg_image_saved)) }
            } catch (error: Throwable) {
                if (isCancellation(error)) {
                    throw error
                }
                if (handleAuthExpired(error)) return@launch
                _uiState.update { it.copy(loadState = LoadState.Error(cleanErrorMessage(error, str(R.string.error_save_failed)))) }
            } finally {
                releaseDownloadSlot()
            }
        }
    }

    fun saveImages(urls: List<String>, filenamePrefix: String) {
        val targets = urls.filter { it.isNotBlank() }
        if (targets.isEmpty()) {
            _uiState.update { it.copy(message = str(R.string.msg_no_saveable_images)) }
            return
        }
        targets.forEachIndexed { index, url ->
            saveImage(url, "${filenamePrefix}_p$index")
        }
        _uiState.update { it.copy(message = str(R.string.msg_save_started, targets.size)) }
    }

    private suspend fun acquireDownloadSlot() {
        while (true) {
            val acquired = downloadMutex.withLock {
                val state = _uiState.value
                val limit = state.settings.simultaneousDownloads.coerceIn(1, 4)
                if (state.activeDownloads < limit) {
                    _uiState.update { it.copy(activeDownloads = it.activeDownloads + 1) }
                    true
                } else {
                    false
                }
            }
            if (acquired) return
            delay(140)
        }
    }

    private suspend fun releaseDownloadSlot() {
        downloadMutex.withLock {
            _uiState.update { it.copy(activeDownloads = (it.activeDownloads - 1).coerceAtLeast(0)) }
        }
    }

    private fun downloadImageToGallery(url: String, filename: String) {
        val requestUrl = proxyPixivImageUrl(url, _uiState.value.settings.pixivImageProxyBaseUrl)
        val request = Request.Builder()
            .url(requestUrl)
            .header("Referer", "https://www.pixiv.net/")
            .header("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Palleria)")
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception(str(R.string.error_save_failed) + " (${response.code})")
            }
            val body = response.body
            imageStore.save(
                input = body.byteStream(),
                name = filename,
                sourceUrl = requestUrl,
                responseMimeType = body.contentType()?.toString(),
            )
        }
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().cacheDir.deleteRecursively()
                _uiState.update { it.copy(message = str(R.string.msg_cache_deleted), loadState = LoadState.Loaded) }
            } catch (error: Throwable) {
                if (isCancellation(error)) {
                    throw error
                }
                _uiState.update { it.copy(loadState = LoadState.Error(cleanErrorMessage(error, str(R.string.error_cache_delete_failed)))) }
            }
        }
    }

    fun openSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.Settings)
    }

    fun openGeneralSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.GeneralSettings)
    }

    fun openImageSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.ImageSettings)
    }

    fun openBookmarkSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.BookmarkSettings)
    }

    fun openAccountSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AccountSettings)
    }

    fun openAccountLoginMethod() {
        closeAccountSwitcher()
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AccountLoginMethod)
    }

    fun openDataSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.DataSettings)
    }

    fun openViewHistory() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.ViewHistory)
    }

    fun closeViewHistory() {
    }

    fun openMuteSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.MuteSettings)
    }

    fun closeMuteSettings() {
    }

    fun openAppData() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AppData)
    }

    fun closeAppData() {
    }

    fun openAbout() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.About)
    }

    fun closeAbout() {
    }

    fun openFavoriteTags() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.FavoriteTags)
    }

    fun closeFavoriteTags() {
    }

    fun openAccountSwitcher() {
        _uiState.update { it.copy(showAccountSwitcher = true) }
    }

    fun closeAccountSwitcher() {
        _uiState.update { it.copy(showAccountSwitcher = false) }
    }

    fun switchAccount(index: Int) {
        val accounts = _uiState.value.settings.accounts
        if (index < 0 || index >= accounts.size) return
        val account = accounts[index]
        updateSettings { it.copy(refreshToken = account.refreshToken, activeAccountIndex = index, bookmarkUserId = account.userId) }
        closeAccountSwitcher()
        login()
    }

    fun removeAccount(index: Int) {
        val current = _uiState.value.settings
        val mutable = current.accounts.toMutableList()
        if (index < 0 || index >= mutable.size) return
        val removedAccount = mutable.removeAt(index)
        val removedActiveAccount = current.activeAccountIndex == index ||
                current.refreshToken == removedAccount.refreshToken
        val newIndex = when {
            removedActiveAccount && mutable.isNotEmpty() -> index.coerceAtMost(mutable.lastIndex)
            removedActiveAccount -> -1
            current.activeAccountIndex > index -> current.activeAccountIndex - 1
            else -> current.activeAccountIndex
        }
        val nextAccount = mutable.getOrNull(newIndex)
        val nextSettings = current.copy(
            accounts = mutable,
            activeAccountIndex = newIndex,
            refreshToken = if (removedActiveAccount) nextAccount?.refreshToken.orEmpty() else current.refreshToken,
            bookmarkUserId = if (removedActiveAccount) nextAccount?.userId else current.bookmarkUserId,
        )

        _uiState.update {
            it.withSettings(nextSettings).copy(
                currentAccount = if (removedActiveAccount) null else it.currentAccount,
                sessionReady = if (removedActiveAccount) false else it.sessionReady,
                showAccountSwitcher = !removedActiveAccount,
                loadState = if (removedActiveAccount) LoadState.Idle else it.loadState,
            )
        }

        if (nextAccount == null && removedActiveAccount) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.logout()
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                repository.saveSettings(nextSettings)
                if (removedActiveAccount) {
                    login()
                }
            }
        }
    }

    fun saveCurrentAccount() {
        val current = _uiState.value
        val account = current.currentAccount ?: return
        val token = current.settings.refreshToken
        if (token.isBlank()) return
        val stored = StoredAccount(
            name = account.name,
            account = account.account,
            profileImageUrl = account.profileImageUrl,
            refreshToken = token,
            userId = account.id,
        )
        val mutable = current.settings.accounts.toMutableList()
        val existingIndex = mutable.indexOfFirst { it.refreshToken == token }
        if (existingIndex >= 0) {
            mutable[existingIndex] = stored
        } else {
            mutable.add(stored)
        }
        updateSettings { it.copy(accounts = mutable, activeAccountIndex = mutable.indexOfFirst { it.refreshToken == token }) }
    }

    fun loadMoreSearch() {
        val nextUrl = _uiState.value.searchNextUrl ?: return
        searchJob?.cancel()
        searchJob = runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    searchItems = it.searchItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    searchNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreUserSearch() {
        val nextUrl = _uiState.value.userSearchNextUrl ?: return
        runLoading {
            val page = repository.nextUserSearchPage(nextUrl)
            _uiState.update {
                it.copy(
                    userSearchItems = it.userSearchItems.appendUserPreviews(page.items),
                    userSearchNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreUserIllusts() {
        val nextUrl = _uiState.value.selectedUserNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    selectedUserIllusts = it.selectedUserIllusts.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    selectedUserNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadSelectedUserBookmarks() {
        val user = _uiState.value.selectedUser ?: return
        if (_uiState.value.selectedUserBookmarks.isNotEmpty()) return
        runLoading {
            val page = repository.bookmarks(user.id, Restrict.Public)
            _uiState.update {
                it.copy(
                    selectedUserBookmarks = page.items.visibleWithSettings(it.settings),
                    selectedUserBookmarksNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreSelectedUserBookmarks() {
        val nextUrl = _uiState.value.selectedUserBookmarksNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    selectedUserBookmarks = it.selectedUserBookmarks.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    selectedUserBookmarksNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun refreshBookmarks() {
        val userId = _uiState.value.settings.bookmarkUserId
        if (userId == null) {
            _uiState.update {
                it.copy(loadState = LoadState.Error(str(R.string.error_pixiv_user_id_not_set)))
            }
            return
        }
        runLoading {
            val page = repository.bookmarks(userId, _uiState.value.settings.bookmarkRestrict)
            _uiState.update {
                it.copy(
                    bookmarkItems = page.items.visibleWithSettings(it.settings),
                    bookmarkNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreBookmarks() {
        val nextUrl = _uiState.value.bookmarkNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            _uiState.update {
                it.copy(
                    bookmarkItems = it.bookmarkItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                    bookmarkNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun toggleBookmark(illust: Illust) {
        if (illust.isBookmarked) {
            _uiState.update { it.copy(pendingBookmarkRemoval = illust) }
            return
        }
        performToggleBookmark(illust)
    }

    fun toggleBookmark(illustId: Long, fallback: Illust? = null) {
        val illust = findIllustById(illustId) ?: fallback ?: return
        toggleBookmark(illust)
    }

    fun cancelBookmarkRemoval() {
        _uiState.update { it.copy(pendingBookmarkRemoval = null) }
    }

    fun confirmBookmarkRemoval() {
        val illust = _uiState.value.pendingBookmarkRemoval ?: return
        _uiState.update { it.copy(pendingBookmarkRemoval = null) }
        performToggleBookmark(illust)
    }

    private fun performToggleBookmark(illust: Illust) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = _uiState.value.settings
                val restrict = if (settings.privateBookmarkDefault) Restrict.Private else settings.bookmarkRestrict
                val updated = repository.toggleBookmark(illust, restrict)
                if (updated.isBookmarked) {
                    showMessage(str(R.string.msg_bookmark_added))
                    if (settings.followOnLike && illust.artistId > 0L) {
                        repository.followUser(illust.artistId, settings.bookmarkRestrict)
                    }
                    if (settings.autoTagOnBookmark && illust.tags.isNotEmpty()) {
                        val nextTags = (illust.tags.take(3) + settings.favoriteTags).distinct().take(24)
                        updateSettings { it.copy(favoriteTags = nextTags) }
                    }
                    if (settings.autoDownloadOnBookmark) {
                        saveImage(updated.originalImageUrl ?: updated.imageUrl, "illustia_${updated.id}")
                    }
                } else {
                    showMessage(str(R.string.msg_bookmark_removed))
                }
                updateIllustEverywhere(updated)
            } catch (error: Throwable) {
                if (isCancellation(error)) {
                    throw error
                }
                if (handleAuthExpired(error)) return@launch
                _uiState.update { it.copy(message = cleanErrorMessage(error, str(R.string.error_bookmark_failed))) }
            }
        }
    }

    private suspend fun loadHomeInternal(kind: HomeFeedKind) {
        val page = repository.loadHome(kind)
        val settings = _uiState.value.settings
        val items = withContext(Dispatchers.Default) {
            page.items.visibleWithSettings(settings)
        }
        _uiState.update {
            it.copy(
                sessionReady = true,
                homeItems = items,
                homeNextUrl = page.nextUrl,
            )
        }
    }

    private suspend fun applyLoggedInSession(sessionReady: Boolean, message: String) {
        val nextSettings = repository.readSettings()
        _uiState.update {
            it.copy(
                settings = nextSettings,
                sessionReady = sessionReady,
                webLoginRequest = null,
                message = message,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            refreshCurrentAccountProfile(nextSettings)
        }
    }

    private suspend fun refreshCurrentAccountProfile(settings: AppSettings) {
        val userId = settings.bookmarkUserId
        if (settings.refreshToken.isBlank() || userId == null) {
            _uiState.update { it.copy(currentAccount = null) }
            return
        }
        runCatching { repository.userDetail(userId) }
            .onSuccess { profile ->
                val activeSettings = _uiState.value.settings
                if (activeSettings.refreshToken != settings.refreshToken || activeSettings.bookmarkUserId != userId) {
                    return@onSuccess
                }
                _uiState.update { it.copy(currentAccount = profile) }
                saveCurrentAccount()
            }
            .onFailure {
                _uiState.update { it.copy(currentAccount = null) }
            }
    }

    private fun updateSettings(block: (AppSettings) -> AppSettings) {
        val next = block(_uiState.value.settings)
        _uiState.update { it.withSettings(next) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(next)
        }
    }

    private fun runLoading(block: suspend () -> Unit): Job {
        loadingJob?.cancel()
        val job = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loadState = LoadState.Loading, message = null) }
            try {
                block()
                _uiState.update { it.copy(loadState = LoadState.Loaded) }
            } catch (error: Throwable) {
                if (isCancellation(error)) {
                    throw error
                }
                if (handleAuthExpired(error)) return@launch
                _uiState.update {
                    it.copy(loadState = LoadState.Error(cleanErrorMessage(error)))
                }
            }
        }
        loadingJob = job
        job.invokeOnCompletion {
            if (loadingJob === job) loadingJob = null
            if (searchJob === job) searchJob = null
        }
        return job
    }

    private fun findIllustById(illustId: Long): Illust? {
        val state = _uiState.value
        return state.selectedIllust?.takeIf { it.id == illustId }
            ?: state.homeItems.firstOrNull { it.id == illustId }
            ?: state.timelineItems.firstOrNull { it.id == illustId }
            ?: state.rankingItems.firstOrNull { it.id == illustId }
            ?: state.bookmarkItems.firstOrNull { it.id == illustId }
            ?: state.watchlistItems.firstOrNull { it.id == illustId }
            ?: state.searchItems.firstOrNull { it.id == illustId }
            ?: state.relatedIllusts.firstOrNull { it.id == illustId }
            ?: state.selectedUserIllusts.firstOrNull { it.id == illustId }
            ?: state.selectedUserBookmarks.firstOrNull { it.id == illustId }
    }

    override fun onCleared() {
        searchJob?.cancel()
        detailExtrasJob?.cancel()
        loadingJob?.cancel()
        super.onCleared()
    }

    private fun isCancellation(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            val name = current.javaClass.name
            if (name.endsWith(".CancellationException") || name.contains("CancellationException")) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun cleanErrorMessage(e: Throwable, fallback: String = str(R.string.error_generic)): String {
        val message = e.message
        if (message.isNullOrBlank() || message.contains("CancellationException")) {
            return fallback
        }
        return message
    }

    private suspend fun handleAuthExpired(error: Throwable): Boolean {
        val apiError = error.findPixivApiException()
        if (apiError?.isAuthExpired() != true) return false
        repository.logout()
        val nextSettings = repository.readSettings()
        _uiState.update {
            it.copy(
                settings = nextSettings,
                sessionReady = false,
                showReloginRequiredDialog = true,
                loadState = LoadState.Idle,
                message = null,
            )
        }
        return true
    }

    private fun Throwable.findPixivApiException(): PixivApiException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is PixivApiException) return current
            current = current.cause
        }
        return null
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

    private fun updateIllustEverywhere(updated: Illust) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { state ->
                val newHome = state.homeItems.replaceIllustIfPresent(updated)
                val newSearch = state.searchItems.replaceIllustIfPresent(updated)
                val newTimeline = state.timelineItems.replaceIllustIfPresent(updated)
                val newWatchlist = state.watchlistItems.replaceIllustIfPresent(updated)
                val newRanking = state.rankingItems.replaceIllustIfPresent(updated)
                val newRelated = state.relatedIllusts.replaceIllustIfPresent(updated)
                val newBookmarks = if (updated.isBookmarked) {
                    state.bookmarkItems.replaceOrAppend(updated)
                } else {
                    state.bookmarkItems.removeIllustIfPresent(updated.id)
                }
                val newUserIllusts = state.selectedUserIllusts.replaceIllustIfPresent(updated)
                val newUserBookmarks = state.selectedUserBookmarks.replaceIllustIfPresent(updated)
                val newSelected = if (state.selectedIllust?.id == updated.id) updated else state.selectedIllust

                if (newHome === state.homeItems &&
                    newSearch === state.searchItems &&
                    newTimeline === state.timelineItems &&
                    newWatchlist === state.watchlistItems &&
                    newRanking === state.rankingItems &&
                    newRelated === state.relatedIllusts &&
                    newBookmarks === state.bookmarkItems &&
                    newUserIllusts === state.selectedUserIllusts &&
                    newUserBookmarks === state.selectedUserBookmarks &&
                    newSelected === state.selectedIllust
                ) {
                    state
                } else {
                    state.copy(
                        homeItems = newHome,
                        searchItems = newSearch,
                        timelineItems = newTimeline,
                        watchlistItems = newWatchlist,
                        rankingItems = newRanking,
                        relatedIllusts = newRelated,
                        bookmarkItems = newBookmarks,
                        selectedUserIllusts = newUserIllusts,
                        selectedUserBookmarks = newUserBookmarks,
                        selectedIllust = newSelected,
                    )
                }
            }
        }
    }

    private fun List<UserPreview>.appendUserPreviews(next: List<UserPreview>): List<UserPreview> {
        if (next.isEmpty()) return this
        val existing = asSequence().map { it.id }.toHashSet()
        return this + next.filter { existing.add(it.id) }
    }

    private fun Illust.hasImageUrl(url: String): Boolean {
        return imageUrl == url ||
                mediumImageUrl == url ||
                originalImageUrl == url ||
                mediumImagePages.any { it == url } ||
                imagePages.any { it == url } ||
                originalImagePages.any { it == url }
    }

    private fun removeMutedFromVisibleLists() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    homeItems = it.homeItems.visibleWith(it),
                    searchItems = it.searchItems.visibleWith(it),
                    timelineItems = it.timelineItems.visibleWith(it),
                    watchlistItems = it.watchlistItems.visibleWith(it),
                    rankingItems = it.rankingItems.visibleWith(it),
                    bookmarkItems = it.bookmarkItems.visibleWith(it),
                    relatedIllusts = it.relatedIllusts.visibleWith(it),
                    selectedUserIllusts = it.selectedUserIllusts.visibleWith(it),
                    selectedUserBookmarks = it.selectedUserBookmarks.visibleWith(it),
                )
            }
        }
    }

    private fun createPixivWebLoginRequest(): PixivWebLoginRequest {
        val verifier = randomUrlSafeString(32)
        val challenge = verifier.sha256Base64Url()
        return PixivWebLoginRequest(
            authorizationUrl = HttpUrl.Builder()
                .scheme("https")
                .host("app-api.pixiv.net")
                .addPathSegments("web/v1/login")
                .addQueryParameter("code_challenge", challenge)
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("client", "pixiv-android")
                .build()
                .toString(),
            codeVerifier = verifier,
        )
    }

    private fun randomUrlSafeString(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun String.sha256Base64Url(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun JSONObject.requireArray(name: String): JSONArray {
        require(has(name))
        return getJSONArray(name)
    }

    private fun JSONArray.strings(): List<String> = buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }

    private fun JSONArray.longs(): List<Long> = buildList {
        for (index in 0 until length()) {
            optLong(index, 0L).takeIf { it > 0L }?.let(::add)
        }
    }

    private fun JSONArray.jsonObjects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}
