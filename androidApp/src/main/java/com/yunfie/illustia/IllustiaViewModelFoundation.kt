package com.yunfie.illustia

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.PixivApiException
import com.yunfie.illustia.data.isPixivRateLimited
import com.yunfie.illustia.data.proxyPixivImageUrl
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
import com.yunfie.illustia.platform.AndroidStringProvider
import com.yunfie.illustia.platform.AppInfo
import com.yunfie.illustia.platform.AppLogger
import com.yunfie.illustia.platform.AppPaths
import com.yunfie.illustia.platform.AppServices
import com.yunfie.illustia.platform.AppUpdateEvent
import com.yunfie.illustia.platform.BackgroundWork
import com.yunfie.illustia.platform.Connectivity
import com.yunfie.illustia.platform.DeviceProfile
import com.yunfie.illustia.platform.DocumentIo
import com.yunfie.illustia.platform.ImageCache
import com.yunfie.illustia.platform.MainThreadChecker
import com.yunfie.illustia.platform.MonotonicClock
import com.yunfie.illustia.platform.StringProvider
import com.yunfie.illustia.platform.Telemetry
import com.yunfie.illustia.platform.ViewModelDeps
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.isDynamicColorAvailable
import com.yunfie.illustia.settings.store.STARTUP_LOGGED_IN_TOKEN
import com.yunfie.illustia.ui.app.AppRoute
import com.yunfie.illustia.ui.app.AppTab
import com.yunfie.illustia.ui.app.DetailEntrySnapshot
import com.yunfie.illustia.ui.app.SearchEntrySnapshot
import com.yunfie.illustia.ui.scroll.ScrollPositionProbe
import com.yunfie.illustia.updater.AppReleaseInfo
import com.yunfie.illustia.updater.AppUpdaterRepository
import com.yunfie.illustia.updater.UpdateCheckState
import com.yunfie.illustia.updater.UpdateInstallMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MIN_SMART_CACHE_PREFETCH = 4
private const val MAX_SMART_CACHE_PREFETCH = 16
private const val FEED_SEEN_STATE_UPDATE_DELAY_MS = 1500L
private const val SMART_CACHE_WARM_DELAY_MS = 2000L
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private val HTTP_SERVER_ERROR_RANGE = 500..599

private data class SettingsPersistenceRequest(
    val settings: AppSettings,
    val baseSettings: AppSettings? = null,
    val notifyLiveWallpaper: Boolean,
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

internal fun AppSettings.withHydratedRoomCollections(full: AppSettings): AppSettings =
    copy(
        refreshToken =
            if (full.refreshToken.isNotBlank() && full.refreshToken != STARTUP_LOGGED_IN_TOKEN) {
                full.refreshToken
            } else {
                refreshToken
            },
        discordToken = if (full.discordToken.isNotBlank()) full.discordToken else discordToken,
        searchHistory = full.searchHistory,
        favoriteTags = full.favoriteTags,
        viewHistory = full.viewHistory,
        accounts = if (full.accounts.isNotEmpty()) full.accounts else accounts,
        mutedIllusts = if (full.mutedIllusts.isNotEmpty()) full.mutedIllusts else mutedIllusts,
        mutedUsers = if (full.mutedUsers.isNotEmpty()) full.mutedUsers else mutedUsers,
        mutedTags = if (full.mutedTags.isNotEmpty()) full.mutedTags else mutedTags,
        seenFeedIllusts = if (full.seenFeedIllusts.isNotEmpty()) full.seenFeedIllusts else seenFeedIllusts,
    )

/** Shared state, lifecycle, persistence, and cross-feature helpers for ViewModel modules. */
@Suppress("LargeClass", "TooManyFunctions")
abstract class IllustiaViewModelFoundation(
    protected val deps: ViewModelDeps,
    protected val strings: StringProvider = AndroidStringProvider(deps.application),
) : ViewModel() {
    /**
     * ViewModel の生存期間に紐づくコルーチンスコープ。生成側（ファクトリ）が渡す。
     * 各モジュールの 70 箇所ある `viewModelScope.launch { }` はこのプロパティを指す。
     */
    protected val viewModelScope: CoroutineScope = deps.scope

    /** `AndroidViewModel` を継承しないため、Application はここから明示的に公開する。 */
    protected val application get() = deps.application

    protected fun <T> getApplication(): T {
        @Suppress("UNCHECKED_CAST")
        return deps.application as T
    }

    protected val illustiaApplication = deps.illustiaApplication
    protected val logger: AppLogger get() = deps.logger
    protected val clock: MonotonicClock get() = deps.clock
    protected val mainThread: MainThreadChecker get() = deps.mainThread
    protected val connectivity: Connectivity get() = deps.connectivity
    protected val appPaths: AppPaths get() = deps.appPaths
    protected val appInfo: AppInfo get() = deps.appInfo
    protected val backgroundWork: BackgroundWork get() = deps.backgroundWork
    protected val imageCache: ImageCache get() = deps.imageCache
    protected val documentIo: DocumentIo get() = deps.documentIo
    protected val deviceProfile: DeviceProfile get() = deps.deviceProfile
    protected val telemetry: Telemetry get() = deps.telemetry
    protected val appServices: AppServices get() = deps.appServices
    protected val settingsStore by lazy {
        illustiaApplication?.settingsStore
            ?: deps.settings
    }

    protected val repository by lazy {
        illustiaApplication?.repository ?: deps.repository
    }

    fun uiRepository(): IllustiaRepository = repository

    protected val imageStore by lazy { NativeImageStore(getApplication<Application>().applicationContext) }
    protected val downloadMutex = Mutex()

    // Wakeup for queued downloads: releasing a slot sends one permit and a parked
    // acquireDownloadSlot() receives it, so waiters suspend instead of spinning on yield().
    // BUFFERED (not CONFLATED) so simultaneous releases wake distinct waiters.
    protected val downloadSlotReleased = Channel<Unit>(Channel.BUFFERED)
    protected var searchJob: Job? = null
    protected var detailExtrasJob: Job? = null

    /** コメントシート等が作品詳細の上に重なっている間は詳細の追加取得を行わない。 */
    protected var detailExtrasPaused: Boolean = false

    /** pause 中に開かれた作品。再開時にこの作品から取得をやり直す。 */
    protected var pendingDetailExtrasIllust: Illust? = null
    protected var loadingJob: Job? = null
    internal val userProfileRequests = UserProfileRequests(viewModelScope)
    internal val userProfileCache = UserProfileCache()

    internal fun isCurrentProfileRequest(session: UserProfileRequests.Session): Boolean =
        userProfileRequests.isCurrent(session) &&
            _uiState.value.selectedUserId == session.userId &&
            _uiState.value.settings.refreshToken == session.accountKey &&
            !_uiState.value.userPageDismissed

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
        const val STARTUP_VERSION_WORK_DELAY_MS = 500L
        const val MAX_SEEN_FEED_ILLUSTS = 2_000
        const val MAX_CACHED_GRID_STATES = 16
    }

    /**
     * ホームフィードのスクロール位置。UI が設置する。
     * ViewModel は「先頭から離れているか」だけを見たいので、Compose の状態型ではなく
     * この小さな関数インターフェースを介して参照する。
     */
    @Volatile
    var homeScrollProbe: ScrollPositionProbe = ScrollPositionProbe.Never
    internal var activeTab: AppTab? = null
    internal val navigationBackStack = androidx.compose.runtime.mutableStateListOf<NavKey>(AppRoute.Main)
    internal val detailSnapshots = androidx.compose.runtime.mutableStateMapOf<Long, DetailEntrySnapshot>()
    internal val searchSnapshots = androidx.compose.runtime.mutableStateMapOf<String, SearchEntrySnapshot>()
    internal val selectedWatchlistSeriesIds = androidx.compose.runtime.mutableStateListOf<Long>()

    abstract fun loadInitialHomeIfNeeded()

    fun clearDetailSnapshots() {
        userProfileRequests.close()
        userProfileCache.clear()
        detailSnapshots.clear()
        searchSnapshots.clear()
        clearScrollStates()
    }

    fun clearScrollStates() {
        illustDetailPagerPages.clear()
        illustDetailRelatedSectionsReady.clear()
    }

    protected val illustDetailPagerPages: MutableMap<Long, Int> =
        object : java.util.LinkedHashMap<Long, Int>(MAX_CACHED_GRID_STATES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Int>?): Boolean = size > MAX_CACHED_GRID_STATES
        }
    protected val illustDetailRelatedSectionsReady: MutableSet<Long> = mutableSetOf()
    protected val downloadClient: OkHttpClient by lazy {
        deps.sharedHttpClient
            .newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    internal val appStateStore = AppStateStore()
    internal val _uiState: AppStateStore = appStateStore
    val uiState: StateFlow<IllustiaUiState> = appStateStore.state

    val searchState: StateFlow<SearchUiState> =
        uiState
            .map { it.searchUiState }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = uiState.value.searchUiState,
            )

    val rankingState: StateFlow<RankingUiState> =
        uiState
            .map { it.rankingUiState }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = uiState.value.rankingUiState,
            )

    @Suppress("VariableNaming")
    internal val _calculatorState = MutableStateFlow(CalculatorUiState())
    val calculatorState: StateFlow<CalculatorUiState> = _calculatorState.asStateFlow()
    protected val deferredStartupDataStarted = AtomicBoolean(false)

    protected fun str(resId: Int): String = strings.get(resId)

    protected fun str(
        resId: Int,
        vararg args: Any,
    ): String = strings.get(resId, *args)

    protected val _navigationRequests = MutableSharedFlow<IllustiaNavigationRequest>(extraBufferCapacity = 16)
    val navigationRequests: SharedFlow<IllustiaNavigationRequest> = _navigationRequests

    @Suppress("VariableNaming")
    protected val _detailNavigationRequests = Channel<Long>(Channel.BUFFERED)
    val detailNavigationRequests = _detailNavigationRequests.receiveAsFlow()

    /**
     * Synchronous navigation hook installed by the UI. Taps go through this so the route is
     * pushed in the same callback instead of after a state round-trip and a recomposition,
     * which made the detail page appear late. Off the main thread it falls back to the channel.
     */
    @Volatile
    var detailNavigator: ((Long) -> Unit)? = null

    protected fun requestDetailNavigation(illustId: Long) {
        val navigator = detailNavigator
        if (navigator != null && mainThread.isMainThread()) {
            navigator(illustId)
        } else {
            _detailNavigationRequests.trySend(illustId)
        }
    }

    fun currentSelectedIllust(): Illust? = _uiState.value.selectedIllust

    @Suppress("VariableNaming")
    protected val _userNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val userNavigationRequests: SharedFlow<Long> = _userNavigationRequests
    internal val tabScrollToTopRequests = MutableSharedFlow<AppTab>(extraBufferCapacity = 16)
    val appUpdaterRepository: AppUpdaterRepository by lazy {
        AppUpdaterRepository(
            context = getApplication(),
            httpClient = (getApplication<Application>() as IllustiaApplication).sharedHttpClient,
        )
    }

    @Suppress("VariableNaming")
    protected val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    @Suppress("VariableNaming")
    protected val _activeDownloads = MutableStateFlow(0)
    val activeDownloadsState: StateFlow<Int> = _activeDownloads.asStateFlow()
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
    val messageState: StateFlow<String?> =
        _uiState
            .map { it.message }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.message)
    val centeredErrorState: StateFlow<String?> =
        _uiState
            .map { it.centeredError }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _uiState.value.centeredError)

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
            if (!shouldLock && !normalizedStartupSettings.privacyModeEnabled && normalizedStartupSettings.refreshToken.isNotBlank()) {
                prefetchHomeFeedOnStartup(_uiState.value.homeKind)
            }
            resumePendingNativeIntentIfReady()
            // Nonessential package/version work must not gate the first frame or feed request.
            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(STARTUP_VERSION_WORK_DELAY_MS)
                val currentVersionCode =
                    appInfo.versionCode() ?: 133
                val lastSeenVersionCode = normalizedStartupSettings.lastSeenAppVersionCode
                val isUpdated = lastSeenVersionCode in 1 until currentVersionCode
                if (isUpdated) {
                    appUpdaterRepository.cleanUpdateApks()
                }
                val postUpdateMsg =
                    if (isUpdated) {
                        val currentVersionName = appUpdaterRepository.getCurrentVersionName()
                        str(R.string.post_update_notice, currentVersionName)
                    } else {
                        null
                    }

                if (postUpdateMsg != null) _uiState.update { it.copy(message = it.message ?: postUpdateMsg) }
                if (lastSeenVersionCode != currentVersionCode) {
                    updateSettings { it.copy(lastSeenAppVersionCode = currentVersionCode) }
                }
            }
        }
    }

    private fun canApplyDownloadProgress(state: UpdateCheckState): Boolean =
        state !is UpdateCheckState.ReadyToInstall && state !is UpdateCheckState.Installing

    protected fun observeAppUpdateDownloadWorkerProgress() {
        viewModelScope.launch {
            backgroundWork.appUpdateProgress().collect { event ->
                when (event) {
                    is AppUpdateEvent.Progress -> {
                        if (canApplyDownloadProgress(_updateCheckState.value)) {
                            _updateCheckState.value =
                                UpdateCheckState.Downloading(
                                    progress = event.progress,
                                    downloadedBytes = event.downloadedBytes,
                                    totalBytes = event.totalBytes,
                                )
                        }
                    }

                    is AppUpdateEvent.Completed -> {
                        _updateCheckState.value =
                            UpdateCheckState.ReadyToInstall(
                                apkPath = event.apkPath,
                                release = event.release,
                            )
                    }

                    is AppUpdateEvent.Failed -> {
                        if (canApplyDownloadProgress(_updateCheckState.value)) {
                            _updateCheckState.value = UpdateCheckState.Error(event.error, event.release)
                            _uiState.update { it.copy(message = str(R.string.update_download_failed)) }
                        }
                    }

                    null -> {
                        if (_updateCheckState.value is UpdateCheckState.Downloading) {
                            _updateCheckState.value = UpdateCheckState.Idle
                        }
                    }
                }
            }
        }
    }

    /** Hooks implemented by feature modules that participate in startup/session flows. */
    protected abstract fun prefetchHomeFeedOnStartup(kind: HomeFeedKind)

    protected abstract fun resumePendingNativeIntentIfReady()

    abstract fun logout()

    abstract fun saveCurrentAccount()

    abstract fun openAccountSwitcher()

    protected suspend fun loadHomeInternal(
        kind: HomeFeedKind,
        forceRefresh: Boolean = false,
        deferIncomingIfScrolled: Boolean = false,
    ) {
        telemetry.traceAsync("feed.home.load", "feed.home") {
            val generation = repository.accountGeneration
            val page = repository.loadHome(kind, forceRefresh = forceRefresh)
            val currentSettings = _uiState.value.settings
            val settings =
                if (currentSettings.refreshToken == STARTUP_LOGGED_IN_TOKEN) {
                    repository.readFeedSettings()
                } else {
                    currentSettings
                }
            val items =
                withContext(Dispatchers.Default) {
                    page.items.visibleWithSettings(settings).preferUnseenFeedItems(settings)
                }
            _uiState.update {
                if (repository.accountGeneration != generation || it.homeKind != kind) return@update it
                if (it.appLocked || it.privacyLocked) return@update it
                val visibleItems = items.visibleWithSettings(it.settings)
                val shouldDeferIncoming =
                    deferIncomingIfScrolled &&
                        it.homeItems.isNotEmpty() &&
                        homeScrollProbe.isScrolledAwayFromStart()
                if (shouldDeferIncoming) {
                    val pendingItems = newHomeItemsToStage(visibleItems, it.homeItems, it.pendingHomeItems)
                    if (pendingItems.isEmpty()) {
                        return@update it.copy(homeNextUrl = page.nextUrl)
                    }
                    return@update it.copy(
                        sessionReady = true,
                        pendingHomeItems = pendingItems,
                        pendingHomeNextUrl = page.nextUrl,
                    )
                }
                val nextHomeItems = if (it.homeItems == visibleItems) it.homeItems else visibleItems
                it.copy(
                    sessionReady = true,
                    homeItems = nextHomeItems,
                    homeNextUrl = page.nextUrl,
                    pendingHomeItems = emptyList(),
                    pendingHomeNextUrl = null,
                )
            }
            if (repository.accountGeneration == generation) rememberFeedItems(items)
        }
    }

    protected suspend fun List<Illust>.preferUnseenFeedItems(settings: AppSettings): List<Illust> {
        val seen = settings.seenFeedIllusts.toHashSet()
        return if (seen.isEmpty()) this else sortedBy { it.id in seen }
    }

    protected fun queueSettingsPersistence(
        next: AppSettings,
        previous: AppSettings,
    ) {
        if (next != previous) {
            settingsPersistenceRequests.trySend(
                SettingsPersistenceRequest(
                    settings = next,
                    baseSettings = previous,
                    notifyLiveWallpaper = false,
                ),
            )
        }
    }

    protected fun rememberFeedItems(items: List<Illust>) {
        if (items.isEmpty()) return
        warmSmartCache(items)
        val shownIds = items.map { it.id }
        synchronized(settingsUpdateLock) {
            val previous = _uiState.value.settings
            val merged = LinkedHashSet<Long>(shownIds.size + previous.seenFeedIllusts.size)
            merged.addAll(shownIds)
            merged.addAll(previous.seenFeedIllusts)
            val maxLimit =
                if (deviceProfile.isLowRamDevice) {
                    600
                } else {
                    MAX_SEEN_FEED_ILLUSTS
                }
            val nextSeen = merged.take(maxLimit)
            if (nextSeen != previous.seenFeedIllusts) {
                val next = previous.copy(seenFeedIllusts = nextSeen)
                queueSettingsPersistence(next, previous)
                viewModelScope.launch(Dispatchers.Default) {
                    delay(FEED_SEEN_STATE_UPDATE_DELAY_MS)
                    _uiState.update { state ->
                        if (state.settings.seenFeedIllusts != nextSeen) {
                            state.copy(settings = state.settings.copy(seenFeedIllusts = nextSeen))
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    protected fun warmSmartCache(items: List<Illust>) {
        val settings = _uiState.value.settings
        if (!settings.smartCacheEnabled) return
        if (settings.smartCacheWifiOnly) {
            if (!connectivity.isWifiConnected()) return
        }
        val proxyBaseUrl = settings.pixivImageProxyBaseUrl
        viewModelScope.launch(Dispatchers.IO) {
            delay(SMART_CACHE_WARM_DELAY_MS)
            items
                .asSequence()
                .take(settings.smartCacheItemCount.coerceIn(MIN_SMART_CACHE_PREFETCH, MAX_SMART_CACHE_PREFETCH))
                .map { illust ->
                    illust.mediumImagePages.firstOrNull() ?: illust.mediumImageUrl.ifBlank { illust.imageUrl }
                }.filter(String::isNotBlank)
                .distinct()
                .forEach { url ->
                    imageCache.prefetch(proxyPixivImageUrl(url, proxyBaseUrl))
                }
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
            appServices.refreshRankingWidget()
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
            val previous = _uiState.value.settings
            val next = block(previous)
            if (next == previous) return
            val filtersUnchanged = previous.filtersMatch(next)
            _uiState.update { state ->
                if (filtersUnchanged) state.copy(settings = next) else state.withSettings(next)
            }
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

    protected suspend fun persistSettingsUpdates() {
        val debounceMs = deviceProfile.dataStoreDebounceMs
        while (true) {
            var request = settingsPersistenceRequests.receive()
            val baseSettings = request.baseSettings
            var notifyLiveWallpaper = request.notifyLiveWallpaper

            while (true) {
                val next =
                    kotlinx.coroutines.withTimeoutOrNull<SettingsPersistenceRequest>(debounceMs) {
                        settingsPersistenceRequests.receive()
                    } ?: break
                notifyLiveWallpaper = notifyLiveWallpaper || next.notifyLiveWallpaper
                request = next
            }

            try {
                repository.saveSettings(request.settings, baseSettings)
                appServices.reconcileAccounts(request.settings.accounts)
                if (notifyLiveWallpaper) {
                    appServices.notifyLiveWallpaperSettingsChanged()
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
                    telemetry.recordException(error, tag = "viewmodel_run_loading")
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
        userProfileRequests.close()
        recommendedTagsJob?.cancel()
        recommendedTagsExpiryJob?.cancel()
    }

    protected fun isCancellation(e: Throwable): Boolean = e.isCancellationFailure()

    protected fun cleanErrorMessage(
        e: Throwable,
        fallback: String = str(R.string.error_generic),
    ): String {
        val message = e.message
        return when {
            message.isNullOrBlank() || message.contains("CancellationException") -> fallback

            e.isPixivRateLimited() -> str(R.string.error_rate_limited)

            e.isTimeoutFailure() -> str(R.string.error_timeout)

            // Never surface raw API payloads such as "Pixiv API error 500: {...}". They are
            // meaningless to users, so map the status to a message that explains what happened.
            e.containsPixivApiFailure() -> apiFailureMessage(e, fallback)

            // Internal exception messages are JSON/log noise, not something to show a user.
            e.message?.isInternalNoiseMessage() == true -> fallback

            else -> message
        }
    }

    // Maps an HTTP status to something a user can act on, while still hiding the raw payload.
    private fun apiFailureMessage(
        e: Throwable,
        fallback: String,
    ): String =
        when (e.findPixivApiException()?.statusCode) {
            HTTP_BAD_REQUEST -> str(R.string.error_bad_request)
            HTTP_FORBIDDEN -> str(R.string.error_forbidden)
            HTTP_NOT_FOUND -> str(R.string.error_not_found)
            in HTTP_SERVER_ERROR_RANGE -> str(R.string.error_server_error)
            else -> fallback
        }

    private fun Throwable.isTimeoutFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SocketTimeoutException) return true
            current = current.cause
        }
        return false
    }

    // Catches raw exception strings and developer-facing text that must never reach the UI.
    private fun String.isInternalNoiseMessage(): Boolean =
        startsWith("com.yunfie.") ||
            startsWith("java.") ||
            startsWith("android.") ||
            contains("Exception") ||
            startsWith("{") ||
            startsWith("[")

    protected fun Throwable.containsPixivApiFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is PixivApiException) return true
            current = current.cause
        }
        return false
    }

    protected fun isOffline(): Boolean = connectivity.isOffline()

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

    protected fun profileFailureMessage(
        error: Throwable,
        fallback: String = str(R.string.error_load_artist_failed),
    ): String {
        if (error.isNetworkFailure()) {
            return fallback
        }
        return cleanErrorMessage(error, fallback)
    }

    protected fun Throwable.isNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is PixivApiException) return false
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
                timelineLoadState = LoadState.Idle,
                followingUsersLoadState = LoadState.Idle,
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
            userProfileCache.updateIllust(updated)
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
                relatedIllustsLoaded = _uiState.value.relatedIllustsLoaded,
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
            val includePrerelease = _uiState.value.settings.includePrereleaseUpdates
            appUpdaterRepository
                .fetchLatestRelease(includePrerelease = includePrerelease)
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
        _updateCheckState.value = UpdateCheckState.Downloading(0f, 0L, release.apkSize)
        backgroundWork.startAppUpdateDownload(release)
        _uiState.update { it.copy(message = str(R.string.update_notification_downloaded_desc)) } // Using a placeholder for started download
    }

    fun installUpdate(apkFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateCheckState.value = UpdateCheckState.Installing
            val method = UpdateInstallMethod.fromValue(_uiState.value.settings.updateInstallMethod)
            if (method == UpdateInstallMethod.SHIZUKU) {
                backgroundWork.cancelAppUpdateWork()
            }
            appUpdaterRepository
                .installApk(apkFile, method)
                .onSuccess {
                    if (method == UpdateInstallMethod.SHIZUKU) {
                        appUpdaterRepository.cleanUpdateApks()
                    }
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

    fun updateIncludePrereleaseUpdates(enabled: Boolean) {
        updateSettings { it.copy(includePrereleaseUpdates = enabled) }
    }

    fun cancelDownloadUpdate() {
        appUpdaterRepository.cancelDownload()
        backgroundWork.cancelAppUpdateWork()
        _updateCheckState.value = UpdateCheckState.Idle
    }

    fun updateCheckUpdatesInBackground(enabled: Boolean) {
        updateSettings { it.copy(checkUpdatesInBackground = enabled) }
        if (enabled && (_uiState.value.settings.notifyNewVersion || _uiState.value.settings.autoDownloadUpdates)) {
            backgroundWork.schedulePeriodicUpdateCheck()
        } else {
            backgroundWork.cancelPeriodicUpdateCheck()
        }
    }

    fun updateNotifyNewVersion(enabled: Boolean) {
        updateSettings { it.copy(notifyNewVersion = enabled) }
        if (_uiState.value.settings.checkUpdatesInBackground && (enabled || _uiState.value.settings.autoDownloadUpdates)) {
            backgroundWork.schedulePeriodicUpdateCheck()
        } else {
            backgroundWork.cancelPeriodicUpdateCheck()
        }
    }

    fun updateAutoDownloadUpdates(enabled: Boolean) {
        updateSettings { it.copy(autoDownloadUpdates = enabled) }
        if (_uiState.value.settings.checkUpdatesInBackground && (enabled || _uiState.value.settings.notifyNewVersion)) {
            backgroundWork.schedulePeriodicUpdateCheck()
        } else {
            backgroundWork.cancelPeriodicUpdateCheck()
        }
    }

    fun updateAutoDownloadWifiOnly(enabled: Boolean) {
        updateSettings { it.copy(autoDownloadWifiOnly = enabled) }
    }
}
