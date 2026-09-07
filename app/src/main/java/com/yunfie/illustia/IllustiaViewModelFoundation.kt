package com.yunfie.illustia

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.yunfie.illustia.account.PalleriaAccount
import com.yunfie.illustia.data.FeatureRepositories
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.data.PixivApiException
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.isDynamicColorAvailable
import com.yunfie.illustia.settings.withSyncedCollections
import com.yunfie.illustia.ui.app.AppRoute
import com.yunfie.illustia.ui.app.AppTab
import com.yunfie.illustia.ui.app.DetailEntrySnapshot
import com.yunfie.illustia.updater.AppReleaseInfo
import com.yunfie.illustia.updater.AppUpdaterRepository
import com.yunfie.illustia.updater.UpdateCheckState
import com.yunfie.illustia.updater.UpdateInstallMethod
import com.yunfie.illustia.widget.RankingWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private data class SettingsPersistenceRequest(
    val settings: AppSettings,
    val baseSettings: AppSettings? = null,
    val notifyLiveWallpaper: Boolean,
    val fromSync: Boolean = false,
)

private fun AppSettings.hasLiveWallpaperChangesComparedTo(other: AppSettings): Boolean =
    liveWallpaperSource != other.liveWallpaperSource ||
        liveWallpaperSourceFolder != other.liveWallpaperSourceFolder ||
        liveWallpaperChangeMode != other.liveWallpaperChangeMode ||
        liveWallpaperIntervalMinutes != other.liveWallpaperIntervalMinutes ||
        liveWallpaperOrder != other.liveWallpaperOrder ||
        liveWallpaperScaleMode != other.liveWallpaperScaleMode ||
        liveWallpaperBackground != other.liveWallpaperBackground ||
        liveWallpaperCrossfade != other.liveWallpaperCrossfade ||
        liveWallpaperExcludeSensitive != other.liveWallpaperExcludeSensitive

internal fun AppSettings.replaceSyncedCollections(synced: SyncedCollectionsSnapshot): AppSettings = withSyncedCollections(synced)

internal fun AppSettings.withHydratedRoomCollections(full: AppSettings): AppSettings =
    copy(
        searchHistory = full.searchHistory,
        favoriteTags = full.favoriteTags,
        viewHistory = full.viewHistory,
        accounts = full.accounts,
    )

/** Shared state, lifecycle, persistence, and cross-feature helpers for ViewModel modules. */
abstract class IllustiaViewModelFoundation(
    app: Application,
    protected val managedDataRepository: ManagedDataRepository = ManagedDataRepository(app.contentResolver),
) : AndroidViewModel(app) {
    protected val illustiaApplication = app as? IllustiaApplication
    protected val settingsStore by lazy {
        illustiaApplication?.settingsStore
            ?: SettingsStore(getApplication<Application>().applicationContext)
    }
    protected val repository by lazy {
        illustiaApplication?.repository ?: IllustiaRepository(settingsStore)
    }
    internal val featureRepositories by lazy { FeatureRepositories(repository) }

    fun uiRepository(): IllustiaRepository = repository

    protected val imageStore by lazy { NativeImageStore(getApplication<Application>().applicationContext) }
    protected val downloadMutex = Mutex()
    protected var searchJob: Job? = null
    protected var detailExtrasJob: Job? = null
    protected var loadingJob: Job? = null
    protected var userPageLoadJob: Job? = null
    protected var closeUserPageJob: Job? = null
    protected var privacyUnlockJob: Job? = null
    protected var autoLockJob: Job? = null
    protected var appLockRecoveryLogin = false
    protected var recommendedTagsJob: Job? = null
    protected var recommendedTagsExpiryJob: Job? = null
    protected var savedLibraryJob: Job? = null
    internal var profileReturnDetail: DetailSnapshot? = null
    internal var searchSnapshot: SearchSnapshot? = null
    internal var userPageSnapshot: UserPageSnapshot? = null
    protected var pendingNativeIntentEvent: NativeIntentEvent? = null
    private val settingsUpdateLock = Any()
    private val settingsPersistenceRequests = Channel<SettingsPersistenceRequest>(Channel.UNLIMITED)

    protected companion object {
        val RECOMMENDED_TAG_CACHE_TTL_MILLIS = TimeUnit.MINUTES.toMillis(30)
        const val MAX_SEEN_FEED_ILLUSTS = 2_000
    }

    val bookmarkTimelineGridState = LazyGridState()
    val bookmarkMainGridState = LazyGridState()
    val bookmarkFollowingGridState = LazyGridState()
    val homeFeedGridState = LazyGridState()
    val homeTimelineGridState = LazyGridState()
    val searchResultGridState = LazyGridState()
    val searchBrowseGridState = LazyGridState()
    internal var activeTab: AppTab? = null
    internal val navigationBackStack = androidx.compose.runtime.mutableStateListOf<NavKey>(AppRoute.Main)
    internal val detailSnapshots = androidx.compose.runtime.mutableStateMapOf<Long, DetailEntrySnapshot>()
    internal val selectedWatchlistSeriesIds = androidx.compose.runtime.mutableStateListOf<Long>()
    protected val rankingGridStates = mutableMapOf<String, LazyGridState>()
    protected val userProfileGridStates = mutableMapOf<Long, LazyGridState>()
    protected val downloadClient: OkHttpClient by lazy {
        (getApplication<Application>() as IllustiaApplication)
            .sharedHttpClient
            .newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    internal val appStateStore = AppStateStore()
    internal val _uiState: AppStateStore = appStateStore
    val uiState: StateFlow<IllustiaUiState> = appStateStore.state
    protected val deferredStartupDataStarted = AtomicBoolean(false)
    protected val initialSyncRevision = SettingsStore.syncUpdates.value?.revision ?: 0L
    protected val initialPallaSyncStateRevision =
        SettingsStore.pallaSyncEnabledUpdates.value?.revision ?: 0L

    protected fun str(resId: Int): String = getApplication<Application>().getString(resId)

    protected fun str(
        resId: Int,
        vararg args: Any,
    ): String = getApplication<Application>().getString(resId, *args)

    protected val _navigationRequests = MutableSharedFlow<IllustiaNavigationRequest>(extraBufferCapacity = 16)
    val navigationRequests: SharedFlow<IllustiaNavigationRequest> = _navigationRequests
    protected val _detailNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val detailNavigationRequests: SharedFlow<Long> = _detailNavigationRequests
    protected val _userNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val userNavigationRequests: SharedFlow<Long> = _userNavigationRequests
    val appUpdaterRepository: AppUpdaterRepository by lazy { AppUpdaterRepository(getApplication()) }
    protected val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()
    val settingsState: StateFlow<AppSettings> =
        _uiState
            .map { it.settings }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.settings)
    val appLockedState: StateFlow<Boolean> =
        _uiState
            .map { it.appLocked }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.appLocked)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            persistSettingsUpdates()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val startupSettings = repository.readStartupSettings()
            val normalizedStartupSettings =
                if (startupSettings.useDynamicColor && !isDynamicColorAvailable()) {
                    startupSettings.copy(useDynamicColor = false)
                } else {
                    startupSettings
                }
            val shouldLock = normalizedStartupSettings.appLockEnabled && settingsStore.hasPinSet()
            _uiState.update {
                it.withSettings(normalizedStartupSettings).copy(
                    settingsLoaded = true,
                    appLocked = shouldLock,
                    privacyLocked = normalizedStartupSettings.privacyModeEnabled,
                    showLockRecoveryDialog = normalizedStartupSettings.appLockFailCount >= 12,
                )
            }
            resumePendingNativeIntentIfReady()
        }
    }

    /** Hooks implemented by feature modules that participate in startup/session flows. */
    protected abstract fun resumePendingNativeIntentIfReady()

    abstract fun logout()

    abstract fun saveCurrentAccount()

    protected suspend fun loadHomeInternal(kind: HomeFeedKind) {
        val page = repository.loadHome(kind)
        val settings = _uiState.value.settings
        val items =
            withContext(Dispatchers.Default) {
                page.items.visibleWithSettings(settings).preferUnseenFeedItems(settings)
            }
        _uiState.update {
            it.copy(
                sessionReady = true,
                homeItems = items,
                homeNextUrl = page.nextUrl,
            )
        }
        rememberFeedItems(items)
    }

    protected fun List<Illust>.preferUnseenFeedItems(settings: AppSettings): List<Illust> {
        if (settings.seenFeedIllusts.isEmpty()) return this
        val seen = settings.seenFeedIllusts.toHashSet()
        return sortedBy { it.id in seen }
    }

    protected fun rememberFeedItems(items: List<Illust>) {
        if (items.isEmpty()) return
        warmSmartCache(items)
        val shownIds = items.map { it.id }
        updateSettings { settings ->
            settings.copy(
                seenFeedIllusts =
                    (shownIds + settings.seenFeedIllusts)
                        .distinct()
                        .take(MAX_SEEN_FEED_ILLUSTS),
            )
        }
    }

    protected fun warmSmartCache(items: List<Illust>) {
        val settings = _uiState.value.settings
        if (!settings.smartCacheEnabled) return
        val context = getApplication<Application>().applicationContext
        if (settings.smartCacheWifiOnly) {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return
        }
        val loader = SingletonImageLoader.get(context)
        items
            .asSequence()
            .take(settings.smartCacheItemCount.coerceIn(4, 30))
            .flatMap { illust ->
                (
                    illust.mediumImagePages.ifEmpty {
                        listOf(illust.mediumImageUrl.ifBlank { illust.imageUrl })
                    }
                ).asSequence()
            }.filter(String::isNotBlank)
            .distinct()
            .forEach { url ->
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
    }

    protected fun AppSettings.resolveLoggedInAccount(): UserProfile? {
        val stored =
            accounts
                .getOrNull(activeAccountIndex)
                ?.takeIf { it.refreshToken == refreshToken }
                ?: accounts.firstOrNull { it.refreshToken == refreshToken }
        return stored?.toUserProfile()
    }

    protected fun StoredAccount.toUserProfile(): UserProfile =
        UserProfile(
            id = userId,
            name = name,
            account = account,
            profileImageUrl = profileImageUrl,
            backgroundImageUrl = null,
            comment = "",
            isFollowed = false,
        )

    protected suspend fun applyLoggedInSession(
        sessionReady: Boolean,
        message: String? = null,
    ) {
        val nextSettings = repository.readSettings()
        _uiState.update {
            it.copy(
                settings = nextSettings,
                sessionReady = sessionReady,
                webLoginRequest = null,
                currentAccount = it.currentAccount ?: nextSettings.resolveLoggedInAccount(),
                message = message,
            )
        }
        resumePendingNativeIntentIfReady()
        viewModelScope.launch(Dispatchers.IO) {
            refreshCurrentAccountProfile(nextSettings)
        }
        refreshRankingWidget()
    }

    protected suspend fun refreshRankingWidget() {
        runCatching {
            val context = getApplication<Application>().applicationContext
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RankingWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val intent =
                android.content.Intent(context, RankingWidgetProvider::class.java).apply {
                    action = RankingWidgetProvider.ACTION_REFRESH_RANKING_WIDGET
                }
            context.sendBroadcast(intent)
        }
    }

    protected suspend fun refreshCurrentAccountProfile(settings: AppSettings) {
        val userId = settings.bookmarkUserId
        if (settings.refreshToken.isBlank() || userId == null) {
            _uiState.update { current ->
                current.copy(currentAccount = current.currentAccount ?: settings.resolveLoggedInAccount())
            }
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
            }.onFailure { expectedFailure ->
                if (isCancellation(expectedFailure)) throw expectedFailure
                _uiState.update { current ->
                    current.copy(currentAccount = current.currentAccount ?: settings.resolveLoggedInAccount())
                }
            }
    }

    protected fun updateSettings(block: (AppSettings) -> AppSettings) {
        synchronized(settingsUpdateLock) {
            lateinit var previous: AppSettings
            lateinit var next: AppSettings
            _uiState.update { state ->
                previous = state.settings
                next = block(previous)
                if (next == previous) state else state.withSettings(next)
            }
            if (next != previous) {
                check(
                    settingsPersistenceRequests
                        .trySend(
                            SettingsPersistenceRequest(
                                settings = next,
                                baseSettings = previous,
                                notifyLiveWallpaper = previous.hasLiveWallpaperChangesComparedTo(next),
                            ),
                        ).isSuccess,
                ) {
                    "Settings persistence queue is unavailable"
                }
            }
        }
    }

    internal suspend fun applySyncedSettings(synced: SyncedCollectionsSnapshot) {
        synchronized(settingsUpdateLock) {
            lateinit var previous: AppSettings
            lateinit var replaced: AppSettings
            _uiState.update { state ->
                previous = state.settings
                replaced = previous.replaceSyncedCollections(synced)
                if (replaced == previous) state else state.withSettings(replaced)
            }
        }
        // The event applier has already persisted these collections. Only update
        // the repository's in-memory cache here to avoid a feedback sync write.
        repository.updateCachedSyncedCollections(synced)
    }

    protected suspend fun applyPallaSyncEnabledUpdate(enabled: Boolean) {
        synchronized(settingsUpdateLock) {
            _uiState.update { state ->
                val settings = state.settings
                if (settings.pallaSyncEnabled == enabled) {
                    state
                } else {
                    state.withSettings(settings.copy(pallaSyncEnabled = enabled))
                }
            }
        }
        // The coordinator already persisted this flag. Keep only the in-memory
        // repository mirror in sync and do not enqueue another settings write.
        repository.updateCachedPallaSyncEnabled(enabled)
    }

    protected suspend fun persistSettingsUpdates() {
        for (request in settingsPersistenceRequests) {
            try {
                if (request.fromSync) {
                    repository.saveSettingsFromSync(request.settings)
                } else {
                    repository.saveSettings(request.settings, request.baseSettings)
                }
                PalleriaAccount.reconcile(getApplication(), request.settings.accounts)
                if (request.notifyLiveWallpaper) {
                    val application = getApplication<Application>()
                    application.sendBroadcast(
                        Intent(com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService.ACTION_SETTINGS_CHANGED)
                            .setPackage(application.packageName),
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                _uiState.update { it.copy(message = cleanErrorMessage(error)) }
            }
        }
    }

    protected fun runLoading(block: suspend () -> Unit): Job {
        loadingJob?.cancel()
        val job =
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(loadState = LoadState.Loading, message = null) }
                try {
                    block()
                    _uiState.update { it.copy(loadState = LoadState.Loaded) }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) {
                        throw error
                    }
                    if (handleAuthExpired(error)) return@launch
                    _uiState.update {
                        it.copy(loadState = LoadState.Error(loadFailureMessage(it, error)))
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

    protected fun findIllustById(illustId: Long): Illust? {
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
        settingsPersistenceRequests.close()
        searchJob?.cancel()
        detailExtrasJob?.cancel()
        loadingJob?.cancel()
        userPageLoadJob?.cancel()
        recommendedTagsJob?.cancel()
        recommendedTagsExpiryJob?.cancel()
    }

    protected fun isCancellation(e: Throwable): Boolean = e.isCancellationFailure()

    protected fun cleanErrorMessage(
        e: Throwable,
        fallback: String = str(R.string.error_generic),
    ): String {
        val message = e.message
        if (message.isNullOrBlank() || message.contains("CancellationException")) {
            return fallback
        }
        return message
    }

    protected fun loadFailureMessage(
        state: IllustiaUiState,
        error: Throwable,
        fallback: String = str(R.string.error_generic),
    ): String {
        if (!error.isNetworkFailure()) {
            return cleanErrorMessage(error, fallback)
        }
        return if (state.hasCachedContent()) {
            str(R.string.offline_cache_displayed)
        } else {
            str(R.string.offline_no_cache)
        }
    }

    protected fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException) return true
            current = current.cause
        }
        return false
    }

    protected fun IllustiaUiState.hasCachedContent(): Boolean =
        homeItems.isNotEmpty() ||
            novelItems.isNotEmpty() ||
            searchItems.isNotEmpty() ||
            searchNovelItems.isNotEmpty() ||
            userSearchItems.isNotEmpty() ||
            timelineItems.isNotEmpty() ||
            watchlistItems.isNotEmpty() ||
            rankingItems.isNotEmpty() ||
            bookmarkItems.isNotEmpty() ||
            selectedIllust != null ||
            selectedNovel != null ||
            selectedNovelText != null ||
            selectedUser != null ||
            selectedUserIllusts.isNotEmpty() ||
            selectedUserBookmarks.isNotEmpty() ||
            selectedRelatedUsers.isNotEmpty() ||
            relatedIllusts.isNotEmpty() ||
            followingUsers.isNotEmpty()

    internal fun snapshotSearchState(): SearchSnapshot = _uiState.value.toSearchSnapshot()

    internal fun snapshotUserPageState(): UserPageSnapshot = _uiState.value.toUserPageSnapshot()

    protected fun restoreUserPageSnapshot() {
        val snapshot = userPageSnapshot ?: return
        _uiState.update { it.restore(snapshot) }
    }

    protected suspend fun loadRecommendedTagImage(tag: String): String? {
        val settings = _uiState.value.settings
        return runCatching {
            repository
                .popularPreview(tag)
                .items
                .visibleWithSettings(settings)
                .randomOrNull()
                ?.squareImageUrl
                ?.takeIf { it.isNotBlank() }
                ?: repository
                    .search(
                        word = tag,
                        sort = SearchSort.PopularDesc,
                        target = SearchTarget.PartialTags,
                        duration = SearchDuration.All,
                        bookmarkFilter = SearchBookmarkFilter.None,
                        includeR18 = settings.allowR18,
                    ).items
                    .visibleWithSettings(settings)
                    .randomOrNull()
                    ?.squareImageUrl
                    ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    protected suspend fun handleAuthExpired(error: Throwable): Boolean {
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

    protected fun Throwable.findPixivApiException(): PixivApiException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is PixivApiException) return current
            current = current.cause
        }
        return null
    }

    protected fun PixivApiException.isAuthExpired(): Boolean {
        if (statusCode == 401) return true
        if (statusCode != 400) return false
        val message = apiMessage.lowercase()
        return message.contains("oauth") ||
            message.contains("token") ||
            message.contains("invalid_grant") ||
            message.contains("invalid refresh")
    }

    protected fun updateIllustEverywhere(updated: Illust) {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.withUpdatedIllust(updated) }
        }
    }

    protected fun captureProfileReturnDetail() {
        val current = _uiState.value.selectedIllust
        if (current == null) {
            if (!_uiState.value.showUserPage) {
                profileReturnDetail = null
            }
            return
        }
        if (_uiState.value.showUserPage && profileReturnDetail?.illust?.id == current.id) return
        profileReturnDetail =
            DetailSnapshot(
                illust = current,
                user = _uiState.value.selectedIllustUser,
                firstComment = _uiState.value.selectedIllustFirstComment,
                relatedIllusts = _uiState.value.relatedIllusts,
            )
    }

    protected fun List<UserPreview>.appendUserPreviews(next: List<UserPreview>): List<UserPreview> {
        if (next.isEmpty()) return this
        val existing = asSequence().map { it.id }.toHashSet()
        return this + next.filter { existing.add(it.id) }
    }

    protected fun removeMutedFromVisibleLists() {
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    homeItems = it.homeItems.visibleWith(it),
                    searchItems = it.searchItems.visibleWithMutedTagsVisible(it.settings),
                    timelineItems = it.timelineItems.visibleWith(it),
                    watchlistItems = it.watchlistItems.visibleWith(it),
                    rankingItems = it.rankingItems.visibleWithMutedTagsVisible(it.settings),
                    bookmarkItems = it.bookmarkItems.visibleWith(it),
                    relatedIllusts = it.relatedIllusts.visibleWith(it),
                    selectedUserIllusts = it.selectedUserIllusts.visibleWith(it),
                    selectedUserBookmarks = it.selectedUserBookmarks.visibleWith(it),
                )
            }
        }
    }

    protected fun createPixivWebLoginRequest(): PixivWebLoginRequest {
        val verifier = randomUrlSafeString(32)
        val challenge = verifier.sha256Base64Url()
        return PixivWebLoginRequest(
            authorizationUrl =
                HttpUrl
                    .Builder()
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

    fun checkForUpdates(silent: Boolean = false) {
        if (_updateCheckState.value is UpdateCheckState.Checking || _updateCheckState.value is UpdateCheckState.Downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Checking
            appUpdaterRepository
                .fetchLatestRelease()
                .onSuccess { release ->
                    if (release != null && appUpdaterRepository.isNewerVersion(release.versionName)) {
                        _updateCheckState.value = UpdateCheckState.UpdateAvailable(release)
                    } else {
                        _updateCheckState.value = UpdateCheckState.UpToDate(appUpdaterRepository.getCurrentVersionName())
                    }
                }.onFailure { error ->
                    _updateCheckState.value = UpdateCheckState.Error(error.message ?: "Failed to check for updates")
                    if (!silent) {
                        _uiState.update { it.copy(message = str(R.string.update_check_failed)) }
                    }
                }
        }
    }

    fun downloadUpdate(release: AppReleaseInfo) {
        if (_updateCheckState.value is UpdateCheckState.Downloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Downloading(0f, 0L, release.apkSize)
            appUpdaterRepository
                .downloadApk(release) { progress, downloaded, total ->
                    _updateCheckState.value = UpdateCheckState.Downloading(progress, downloaded, total)
                }.onSuccess { file ->
                    _updateCheckState.value = UpdateCheckState.ReadyToInstall(file, release)
                }.onFailure { error ->
                    _updateCheckState.value = UpdateCheckState.Error(error.message ?: "Download failed")
                    _uiState.update { it.copy(message = str(R.string.update_download_failed)) }
                }
        }
    }

    fun installUpdate(apkFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Installing
            val method = UpdateInstallMethod.fromValue(_uiState.value.settings.updateInstallMethod)
            appUpdaterRepository
                .installApk(apkFile, method)
                .onSuccess {
                    _updateCheckState.value = UpdateCheckState.Idle
                }.onFailure { error ->
                    _updateCheckState.value = UpdateCheckState.Error(error.message ?: "Installation failed")
                    _uiState.update { it.copy(message = str(R.string.update_install_failed, error.message.orEmpty())) }
                }
        }
    }

    fun updateUpdateInstallMethod(method: UpdateInstallMethod) {
        updateSettings { it.copy(updateInstallMethod = method.value) }
        if (method == UpdateInstallMethod.SHIZUKU) {
            appUpdaterRepository.requestShizukuPermission()
        }
    }

    fun updateClipboardAutoDetect(enabled: Boolean) {
        updateSettings { it.copy(autoDetectClipboard = enabled) }
    }

    fun updateAutoCheckUpdateOnStartup(enabled: Boolean) {
        updateSettings { it.copy(autoCheckUpdateOnStartup = enabled) }
    }
}
