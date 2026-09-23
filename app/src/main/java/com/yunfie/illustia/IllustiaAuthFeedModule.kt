package com.yunfie.illustia

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.SearchWorkType
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.ui.app.SearchEntrySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TARGET_INITIAL_SEARCH_COUNT = 30
private const val MAX_SEARCH_PAGES_ACCUMULATION = 20

/** Authentication, native intents, search, feeds, timelines, and managed-data transfer. */
@Suppress("LargeClass", "TooManyFunctions")
abstract class IllustiaAuthFeedModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaSettingsSecurityModule(app, managedDataRepository) {
    abstract fun openIllust(illustId: Long)

    abstract fun openUserPage(userId: Long)

    fun login() {
        val refreshToken = _uiState.value.settings.refreshToken
        runLoading {
            val session = repository.login(refreshToken)
            applyLoggedInSession(session.accessToken.isNotBlank())
            loadHomeInternal(_uiState.value.homeKind)
        }
    }

    fun openWebLogin() {
        appLockRecoveryLogin = false
        _uiState.update {
            it.copy(
                webLoginRequest = createPixivWebLoginRequest(),
                showReloginRequiredDialog = false,
                message = null,
            )
        }
    }

    fun closeWebLogin() {
        appLockRecoveryLogin = false
        _uiState.update { it.copy(webLoginRequest = null) }
    }

    fun dismissReloginRequiredDialog() {
        _uiState.update { it.copy(showReloginRequiredDialog = false) }
    }

    fun failWebLogin(message: String) {
        appLockRecoveryLogin = false
        _uiState.update {
            it.copy(
                webLoginRequest = null,
                loadState = LoadState.Error(message),
            )
        }
    }

    fun completeWebLogin(code: String) {
        val request = _uiState.value.webLoginRequest ?: return
        val wasRecovery = appLockRecoveryLogin && _uiState.value.appLocked
        appLockRecoveryLogin = false
        runLoading {
            val session = repository.loginWithAuthorizationCode(code, request.codeVerifier)
            if (wasRecovery) {
                val state = _uiState.value
                val currentAccId = state.currentAccount?.id
                val settings = state.settings
                val resolvedAccount = settings.resolveLoggedInAccount()
                val resolvedId = resolvedAccount?.id
                val accounts = settings.accounts
                val firstAccount = accounts.firstOrNull()
                val firstStoredId = firstAccount?.userId
                val expectedUserId = currentAccId ?: resolvedId ?: firstStoredId
                val isMatchingUser =
                    if (expectedUserId != null) {
                        session.userId == expectedUserId
                    } else if (accounts.isNotEmpty()) {
                        val sessionUserId = session.userId
                        sessionUserId != null && accounts.any { it.userId == sessionUserId }
                    } else {
                        true
                    }
                if (!isMatchingUser) {
                    _uiState.update {
                        it.copy(
                            webLoginRequest = null,
                            message = str(R.string.app_lock_recovery_account_mismatch),
                        )
                    }
                    return@runLoading
                }
            }
            applyLoggedInSession(session.accessToken.isNotBlank(), str(R.string.msg_web_login_complete))
            loadHomeInternal(_uiState.value.homeKind)
            if (wasRecovery) {
                disableAppLock()
                resetLockFailCount()
            }
        }
    }

    override fun logout() {
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
            refreshRankingWidget()
        }
    }

    fun selectHomeKind(kind: HomeFeedKind) {
        _uiState.update { it.copy(homeKind = kind) }
        refreshHome()
    }

    fun selectRankingMode(mode: String) {
        _uiState.update {
            it.copy(
                rankingMode = mode,
                rankingItems = it.rankingModeItems[mode] ?: it.rankingItems,
                rankingNextUrl = it.rankingModeNextUrls[mode],
            )
        }
        loadRankingModeIfNeeded(mode)
    }

    override fun refreshHome(forceRefresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isHomeRefreshing = forceRefresh,
                    loadState = if (it.homeItems.isEmpty()) LoadState.Loading else it.loadState,
                )
            }
            try {
                GlitchTipTelemetry.traceAsync("feed.home.refresh", "feed.home") {
                    loadHomeInternal(_uiState.value.homeKind, forceRefresh = forceRefresh)
                }
                _uiState.update { it.copy(isHomeRefreshing = false, loadState = LoadState.Loaded) }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_home_refresh")
                _uiState.update {
                    it.copy(
                        isHomeRefreshing = false,
                        loadState = LoadState.Error(loadFailureMessage(it, error)),
                    )
                }
            }
        }
    }

    fun refreshNovels(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isNovelRefreshing = forceRefresh,
                    loadState = if (it.novelItems.isEmpty()) LoadState.Loading else it.loadState,
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.novels.refresh", "feed.novels") {
                        repository.loadNovels(forceRefresh = forceRefresh)
                    }
                _uiState.update {
                    it.copy(
                        novelItems = page.items.visibleWithSettings(it.settings),
                        novelNextUrl = page.nextUrl,
                        isNovelRefreshing = false,
                        loadState = LoadState.Loaded,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_novels_refresh")
                _uiState.update {
                    it.copy(
                        isNovelRefreshing = false,
                        loadState = LoadState.Error(loadFailureMessage(it, error)),
                    )
                }
            }
        }
    }

    fun loadMoreNovels() {
        val nextUrl = _uiState.value.novelNextUrl ?: return
        if (_uiState.value.isNovelPaginating) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isNovelPaginating = true) }
            try {
                val page = repository.nextNovelPage(nextUrl)
                _uiState.update {
                    it.copy(
                        novelItems = it.novelItems + page.items.visibleWithSettings(it.settings),
                        novelNextUrl = page.nextUrl,
                        isNovelPaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_novels_load_more")
                _uiState.update {
                    it.copy(
                        isNovelPaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    fun openNovel(novel: NovelPreview) {
        _uiState.update { it.copy(selectedNovel = novel, selectedNovelText = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val text = repository.loadNovelText(novel.id)
                _uiState.update {
                    if (it.selectedNovel?.id != novel.id) it else it.copy(selectedNovelText = text)
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update {
                    if (it.selectedNovel?.id == novel.id) {
                        it.copy(
                            message = cleanErrorMessage(error, getApplication<Application>().getString(R.string.error_novel_load_failed)),
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun openNovelById(
        novelId: Long,
        title: String = "",
    ) {
        val existing = _uiState.value.novelItems.firstOrNull { it.id == novelId }
        val novel =
            existing ?: NovelPreview(
                id = novelId,
                title = title,
                caption = "",
                userId = 0L,
                userName = "",
                userAccount = "",
                coverUrl = "",
                pageCount = 1,
                textLength = 0,
                isBookmarked = false,
                totalBookmarks = 0,
                totalView = 0,
            )
        openNovel(novel)
    }

    fun updateNovelProgress(
        novelId: Long,
        page: Int,
        totalPages: Int,
        status: com.yunfie.illustia.models.NovelReadingStatus? = null,
    ) {
        updateSettings { current ->
            val existing = current.novelProgress[novelId]
            val resolvedStatus =
                status ?: when {
                    page >= totalPages - 1 && totalPages > 1 -> com.yunfie.illustia.models.NovelReadingStatus.Completed
                    existing != null && existing.status != com.yunfie.illustia.models.NovelReadingStatus.Unread -> existing.status
                    else -> com.yunfie.illustia.models.NovelReadingStatus.Reading
                }
            val record =
                com.yunfie.illustia.models.NovelReadingProgress(
                    novelId = novelId,
                    lastReadPage = page,
                    totalPages = totalPages,
                    updatedAt = System.currentTimeMillis(),
                    status = resolvedStatus,
                )
            current.copy(novelProgress = current.novelProgress + (novelId to record))
        }
    }

    fun setNovelReadingStatus(
        novelId: Long,
        status: com.yunfie.illustia.models.NovelReadingStatus,
    ) {
        updateSettings { current ->
            val existing = current.novelProgress[novelId]
            val record =
                existing?.copy(status = status, updatedAt = System.currentTimeMillis())
                    ?: com.yunfie.illustia.models.NovelReadingProgress(
                        novelId = novelId,
                        lastReadPage = 0,
                        totalPages = 1,
                        updatedAt = System.currentTimeMillis(),
                        status = status,
                    )
            current.copy(novelProgress = current.novelProgress + (novelId to record))
        }
    }

    fun closeNovel() {
        _uiState.update { it.copy(selectedNovel = null, selectedNovelText = null) }
    }

    fun loadRankingModeIfNeeded(mode: String = _uiState.value.rankingMode) {
        val state = _uiState.value
        if (!state.rankingModeItems[mode].isNullOrEmpty()) return
        if (state.rankingModeLoadStates[mode] is LoadState.Loading) return
        refreshRanking(mode)
    }

    fun refreshRanking(
        mode: String = _uiState.value.rankingMode,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isRankingRefreshing = it.isRankingRefreshing + (mode to forceRefresh),
                    rankingModeLoadStates =
                        it.rankingModeLoadStates +
                            (
                                mode to
                                    if (it.rankingModeItems[mode].isNullOrEmpty()) {
                                        LoadState.Loading
                                    } else {
                                        LoadState.Idle
                                    }
                            ),
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.ranking.refresh", "feed.ranking") {
                        repository.loadRanking(mode, forceRefresh = forceRefresh)
                    }
                val settings = _uiState.value.settings
                val items =
                    withContext(Dispatchers.Default) {
                        page.items.visibleWithMutedTagsVisible(settings)
                    }
                _uiState.update { current ->
                    val updatedItems = current.rankingModeItems + (mode to items)
                    val updatedNextUrls = current.rankingModeNextUrls + (mode to page.nextUrl)
                    val updatedLoadStates = current.rankingModeLoadStates + (mode to LoadState.Idle)
                    current.copy(
                        isRankingRefreshing = current.isRankingRefreshing + (mode to false),
                        rankingModeItems = updatedItems,
                        rankingModeNextUrls = updatedNextUrls,
                        rankingModeLoadStates = updatedLoadStates,
                        rankingItems = if (current.rankingMode == mode) items else current.rankingItems,
                        rankingNextUrl = if (current.rankingMode == mode) page.nextUrl else current.rankingNextUrl,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_ranking_refresh")
                _uiState.update { current ->
                    current.copy(
                        isRankingRefreshing = current.isRankingRefreshing + (mode to false),
                        rankingModeLoadStates = current.rankingModeLoadStates + (mode to LoadState.Error(cleanErrorMessage(error))),
                    )
                }
            }
        }
    }

    fun loadMoreRanking(mode: String = _uiState.value.rankingMode) {
        val nextUrl = _uiState.value.rankingModeNextUrls[mode] ?: _uiState.value.rankingNextUrl ?: return
        if (_uiState.value.isRankingPaginating[mode] == true) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isRankingPaginating = it.isRankingPaginating + (mode to true),
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.ranking.load_more", "feed.ranking") {
                        repository.nextPage(nextUrl)
                    }
                val settings = _uiState.value.settings
                _uiState.update { current ->
                    val currentList = current.rankingModeItems[mode] ?: current.rankingItems
                    val nextList = currentList.appendIllusts(page.items.visibleWithMutedTagsVisible(settings))
                    val updatedItems = current.rankingModeItems + (mode to nextList)
                    val updatedNextUrls = current.rankingModeNextUrls + (mode to page.nextUrl)
                    current.copy(
                        isRankingPaginating = current.isRankingPaginating + (mode to false),
                        rankingModeItems = updatedItems,
                        rankingModeNextUrls = updatedNextUrls,
                        rankingItems = if (current.rankingMode == mode) nextList else current.rankingItems,
                        rankingNextUrl = if (current.rankingMode == mode) page.nextUrl else current.rankingNextUrl,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_ranking_load_more")
                _uiState.update { current ->
                    current.copy(
                        isRankingPaginating = current.isRankingPaginating + (mode to false),
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    fun loadMoreHome() {
        val nextUrl = _uiState.value.homeNextUrl ?: return
        if (_uiState.value.isHomePaginating) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isHomePaginating = true) }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.home.load_more", "feed.home") {
                        repository.nextPage(nextUrl)
                    }
                val currentSettings = _uiState.value.settings
                val additions = page.items.visibleWithSettings(currentSettings).preferUnseenFeedItems(currentSettings)
                val shownIds = additions.map { it.id }
                val merged = LinkedHashSet<Long>(shownIds.size + currentSettings.seenFeedIllusts.size)
                merged.addAll(shownIds)
                merged.addAll(currentSettings.seenFeedIllusts)
                val updatedSettings = currentSettings.copy(seenFeedIllusts = merged.take(MAX_SEEN_FEED_ILLUSTS))

                _uiState.update {
                    it.copy(
                        homeItems = it.homeItems.appendIllusts(additions),
                        homeNextUrl = page.nextUrl,
                        isHomePaginating = false,
                        settings = updatedSettings,
                    )
                }
                queueSettingsPersistence(updatedSettings, currentSettings)
                warmSmartCache(additions)
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_home_load_more")
                _uiState.update {
                    it.copy(
                        isHomePaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    private fun handleSearchIntent(normalized: String): Boolean {
        val event = NativeIntentRouter.parseText(normalized) ?: return false
        return when (event) {
            is NativeIntentEvent.Artwork -> {
                _uiState.update { it.copy(searchDraft = "") }
                openIllust(event.id)
                true
            }

            is NativeIntentEvent.User -> {
                _uiState.update { it.copy(searchDraft = "") }
                openUserPage(event.id)
                true
            }

            else -> {
                false
            }
        }
    }

    private fun updateSearchStateBeforeQuery(
        normalized: String,
        forceRefresh: Boolean,
    ) {
        if (!forceRefresh) {
            _uiState.update {
                it.copy(
                    searchDraft = normalized,
                    activeSearchWord = normalized,
                    searchItems = emptyList(),
                    searchNextUrl = null,
                    searchNovelItems = emptyList(),
                    searchNovelNextUrl = null,
                    userSearchItems = emptyList(),
                    userSearchNextUrl = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    searchDraft = normalized,
                    activeSearchWord = normalized,
                )
            }
        }
    }

    override fun submitSearch(
        word: String,
        forceRefresh: Boolean,
    ) {
        val normalized = word.trim()
        if (normalized.isBlank()) {
            clearSearchResults()
            return
        }
        if (handleSearchIntent(normalized)) return

        val settings = _uiState.value.settings
        if (settings.saveSearchHistory) {
            val history =
                (listOf(normalized) + settings.searchHistory)
                    .distinct()
                    .take(6)
            updateSettings { it.copy(searchHistory = history) }
        }
        searchSnapshot = snapshotSearchState()
        updateSearchStateBeforeQuery(normalized, forceRefresh)
        searchJob?.cancel()
        val job = executeSearchJob(normalized, forceRefresh)
        searchJob = job
        job.invokeOnCompletion {
            if (searchJob === job) {
                searchJob = null
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun executeSearchJob(
        normalized: String,
        forceRefresh: Boolean,
    ): Job =
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loadState = LoadState.Loading, isSearchRefreshing = forceRefresh, message = null) }
            try {
                GlitchTipTelemetry.traceAsync("search.query", "search") {
                    kotlinx.coroutines.coroutineScope {
                        val currentSettings = _uiState.value.settings
                        val workType = currentSettings.searchWorkType
                        val pageDeferred =
                            if (!workType.isNovel) {
                                async {
                                    repository.search(
                                        word = normalized,
                                        sort = currentSettings.searchSort,
                                        target = currentSettings.searchTarget,
                                        duration = currentSettings.searchDuration,
                                        bookmarkFilter = currentSettings.searchBookmarkFilter,
                                        includeR18 = currentSettings.allowR18,
                                        type = workType.apiType,
                                        forceRefresh = forceRefresh,
                                    )
                                }
                            } else {
                                null
                            }
                        val novelPageDeferred =
                            if (workType.isNovel) {
                                async {
                                    repository.searchNovels(
                                        word = normalized,
                                        sort = currentSettings.searchSort,
                                        target = currentSettings.searchTarget,
                                        duration = currentSettings.searchDuration,
                                        bookmarkFilter = currentSettings.searchBookmarkFilter,
                                        includeR18 = currentSettings.allowR18,
                                        forceRefresh = forceRefresh,
                                    )
                                }
                            } else {
                                null
                            }
                        val usersDeferred =
                            if (currentSettings.searchUsersEnabled) {
                                async { repository.searchUsers(normalized, forceRefresh = forceRefresh) }
                            } else {
                                null
                            }

                        val page = pageDeferred?.await()
                        val novelPage = novelPageDeferred?.await()
                        val users = usersDeferred?.await()

                        val initialFiltered =
                            page
                                ?.items
                                ?.filter { illust -> workType.acceptsIllustType(illust.type) }
                                ?.visibleWithMutedTagsVisible(currentSettings)
                                .orEmpty()

                        var searchNextUrl = page?.nextUrl
                        val collectedItems = initialFiltered.toMutableList()

                        if (workType != SearchWorkType.Artworks && !workType.isNovel &&
                            collectedItems.size < TARGET_INITIAL_SEARCH_COUNT
                        ) {
                            var loopCount = 0
                            while (
                                collectedItems.size < TARGET_INITIAL_SEARCH_COUNT &&
                                searchNextUrl != null &&
                                loopCount < MAX_SEARCH_PAGES_ACCUMULATION
                            ) {
                                loopCount++
                                try {
                                    val nextPage = repository.nextPage(searchNextUrl)
                                    val nextFiltered =
                                        nextPage.items
                                            .filter { illust -> workType.acceptsIllustType(illust.type) }
                                            .visibleWithMutedTagsVisible(currentSettings)
                                    collectedItems.addAll(nextFiltered)
                                    searchNextUrl = nextPage.nextUrl
                                } catch (_: Exception) {
                                    break
                                }
                            }
                        }

                        _uiState.update {
                            it.copy(
                                searchItems = collectedItems,
                                searchNextUrl = searchNextUrl,
                                searchNovelItems = novelPage?.items?.visibleWithSettings(it.settings).orEmpty(),
                                searchNovelNextUrl = novelPage?.nextUrl,
                                userSearchItems =
                                    users
                                        ?.items
                                        ?.filterNot { user -> user.id in it.mutedUsersSet }
                                        .orEmpty(),
                                userSearchNextUrl = users?.nextUrl,
                                loadState = LoadState.Loaded,
                                isSearchRefreshing = false,
                            )
                        }
                    }
                }
                searchSnapshot = null
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "search_query", extras = mapOf("query" to normalized))
                val snapshot = searchSnapshot
                if (snapshot != null) {
                    _uiState.update {
                        it.copy(
                            searchDraft = snapshot.searchDraft,
                            activeSearchWord = snapshot.activeSearchWord,
                            searchItems = snapshot.searchItems,
                            searchNextUrl = snapshot.searchNextUrl,
                            searchNovelItems = snapshot.searchNovelItems,
                            searchNovelNextUrl = snapshot.searchNovelNextUrl,
                            userSearchItems = snapshot.userSearchItems,
                            userSearchNextUrl = snapshot.userSearchNextUrl,
                            loadState = LoadState.Error(loadFailureMessage(it, error)),
                            isSearchRefreshing = false,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            loadState = LoadState.Error(loadFailureMessage(it, error)),
                            isSearchRefreshing = false,
                        )
                    }
                }
            }
        }

    fun clearSearchResults() {
        searchJob?.cancel()
        searchSnapshot = null
        _uiState.update {
            it.copy(
                searchDraft = "",
                activeSearchWord = "",
                searchItems = emptyList(),
                searchNextUrl = null,
                searchNovelItems = emptyList(),
                searchNovelNextUrl = null,
                userSearchItems = emptyList(),
                userSearchNextUrl = null,
                isSearchRefreshing = false,
                isSearchPaginating = false,
                isUserSearchPaginating = false,
                loadState = LoadState.Idle,
            )
        }
    }

    internal fun restoreSearchResults(snapshot: SearchEntrySnapshot) {
        searchJob?.cancel()
        searchSnapshot = null
        _uiState.update {
            it.copy(
                searchDraft = snapshot.query,
                activeSearchWord = snapshot.query,
                searchItems = snapshot.searchItems,
                searchNextUrl = snapshot.searchNextUrl,
                searchNovelItems = snapshot.searchNovelItems,
                searchNovelNextUrl = snapshot.searchNovelNextUrl,
                userSearchItems = snapshot.userSearchItems,
                userSearchNextUrl = snapshot.userSearchNextUrl,
                loadState = LoadState.Loaded,
            )
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        intent.data?.let { uri ->
            // Always allow Pixiv OAuth callback (needed for recovery web login)
            if (uri.scheme == "pixiv" && uri.host == "account" && uri.path == "/login") {
                uri
                    .getQueryParameter("code")
                    ?.takeIf(String::isNotBlank)
                    ?.let(::completeWebLogin)
                return
            }
        }
        val parsedEvent = NativeIntentRouter.parse(intent)
        // Keep the request pending while either lock screen is active. It is dispatched only
        // after authentication, so external intents cannot bypass app or privacy locks.
        if (_uiState.value.appLocked || _uiState.value.privacyLocked) {
            if (parsedEvent != null) pendingNativeIntentEvent = parsedEvent
            return
        }
        intent
            ?.getStringExtra(com.yunfie.illustia.nativebridge.NativeIntentRouter.EXTRA_HANDOFF_URI)
            ?.takeIf(String::isNotBlank)
            ?.let(NativeIntentRouter::parseText)
            ?.let { event ->
                dispatchNativeIntentEvent(event)
                return
            }
        parsedEvent?.let(::dispatchNativeIntentEvent)
    }

    private fun dispatchNativeIntentEvent(event: NativeIntentEvent) {
        val state = _uiState.value
        if (
            !state.settingsLoaded ||
            state.appLocked ||
            state.privacyLocked ||
            (event is NativeIntentEvent.Text && state.settings.refreshToken.isBlank())
        ) {
            pendingNativeIntentEvent = event
            return
        }
        pendingNativeIntentEvent = null
        when (event) {
            is NativeIntentEvent.Artwork -> {
                openIllust(event.id)
            }

            is NativeIntentEvent.User -> {
                openUserPage(event.id)
            }

            is NativeIntentEvent.Text -> {
                submitSearch(event.value)
            }

            is NativeIntentEvent.Image -> {
                _uiState.update {
                    it.copy(message = str(R.string.msg_shared_image_received))
                }
            }
        }
    }

    protected override fun resumePendingNativeIntentIfReady() {
        val event = pendingNativeIntentEvent ?: return
        dispatchNativeIntentEvent(event)
    }

    fun handleClipboardText(value: String) {
        if (_uiState.value.appLocked) return
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

    fun removeSearchHistoryItem(query: String) {
        updateSettings { it.copy(searchHistory = it.searchHistory.filterNot { item -> item == query }) }
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
        _uiState.update {
            it.copy(
                message =
                    if (tag in
                        current
                    ) {
                        str(R.string.msg_watchlist_tag_removed, tag)
                    } else {
                        str(R.string.msg_watchlist_tag_added, tag)
                    },
            )
        }
    }

    fun refreshTimeline(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isTimelineRefreshing = forceRefresh,
                    loadState = if (it.timelineItems.isEmpty()) LoadState.Loading else it.loadState,
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.timeline.refresh", "feed.timeline") {
                        repository.followingIllusts(_uiState.value.settings.bookmarkRestrict, forceRefresh = forceRefresh)
                    }
                _uiState.update {
                    it.copy(
                        timelineItems = page.items.visibleWithSettings(it.settings),
                        timelineNextUrl = page.nextUrl,
                        isTimelineRefreshing = false,
                        loadState = LoadState.Loaded,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_timeline_refresh")
                _uiState.update {
                    it.copy(
                        isTimelineRefreshing = false,
                        loadState = LoadState.Error(loadFailureMessage(it, error)),
                    )
                }
            }
        }
    }

    fun loadMoreTimeline() {
        val nextUrl = _uiState.value.timelineNextUrl ?: return
        if (_uiState.value.isTimelinePaginating) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTimelinePaginating = true) }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.timeline.load_more", "feed.timeline") {
                        repository.nextPage(nextUrl)
                    }
                _uiState.update {
                    it.copy(
                        timelineItems = it.timelineItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                        timelineNextUrl = page.nextUrl,
                        isTimelinePaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_timeline_load_more")
                _uiState.update {
                    it.copy(
                        isTimelinePaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    fun refreshShortsFeed(forceRefresh: Boolean = false) {
        runLoading {
            val homePage = repository.loadHome(HomeFeedKind.Recommended, forceRefresh = forceRefresh)
            val followingPage = repository.followingIllusts(_uiState.value.settings.bookmarkRestrict, forceRefresh = forceRefresh)
            _uiState.update { state ->
                val home = homePage.items.visibleWithSettings(state.settings)
                val following = followingPage.items.visibleWithSettings(state.settings)
                state.copy(
                    shortsFeedItems = interleaveIllusts(home, following),
                    shortsFeedHomeNextUrl = homePage.nextUrl,
                    shortsFeedFollowingNextUrl = followingPage.nextUrl,
                )
            }
            warmSmartCache(_uiState.value.shortsFeedItems)
        }
    }

    fun updateShortsFeedCurrentIllust(illustId: Long) {
        _uiState.update { it.copy(shortsFeedCurrentIllustId = illustId) }
    }

    fun loadMoreShortsFeed() {
        val state = _uiState.value
        val homeNextUrl = state.shortsFeedHomeNextUrl
        val followingNextUrl = state.shortsFeedFollowingNextUrl
        if (homeNextUrl == null && followingNextUrl == null) return
        runLoading {
            val homePage = homeNextUrl?.let { repository.nextPage(it) }
            val followingPage = followingNextUrl?.let { repository.nextPage(it) }
            _uiState.update { current ->
                val additions =
                    interleaveIllusts(
                        homePage?.items.orEmpty().visibleWithSettings(current.settings),
                        followingPage?.items.orEmpty().visibleWithSettings(current.settings),
                    )
                current.copy(
                    shortsFeedItems = current.shortsFeedItems.appendIllusts(additions),
                    shortsFeedHomeNextUrl = homePage?.nextUrl,
                    shortsFeedFollowingNextUrl = followingPage?.nextUrl,
                )
            }
            warmSmartCache(_uiState.value.shortsFeedItems.takeLast(_uiState.value.settings.smartCacheItemCount))
        }
    }

    fun loadWatchlistTag(tag: String) {
        val normalized = tag.trim().removePrefix("#")
        if (normalized.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isWatchlistRefreshing = true,
                    loadState = if (it.watchlistItems.isEmpty() || it.activeWatchlistTag != normalized) LoadState.Loading else it.loadState,
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.watchlist.refresh", "feed.watchlist") {
                        repository.search(
                            word = normalized,
                            sort = _uiState.value.settings.searchSort,
                            target = _uiState.value.settings.searchTarget,
                            duration = _uiState.value.settings.searchDuration,
                            bookmarkFilter = _uiState.value.settings.searchBookmarkFilter,
                            includeR18 = _uiState.value.settings.allowR18,
                        )
                    }
                _uiState.update {
                    it.copy(
                        activeWatchlistTag = normalized,
                        watchlistItems = page.items.visibleWithSettings(it.settings),
                        watchlistNextUrl = page.nextUrl,
                        isWatchlistRefreshing = false,
                        loadState = LoadState.Loaded,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_watchlist_refresh")
                _uiState.update {
                    it.copy(
                        isWatchlistRefreshing = false,
                        loadState = LoadState.Error(loadFailureMessage(it, error)),
                    )
                }
            }
        }
    }

    fun loadMoreWatchlist() {
        val nextUrl = _uiState.value.watchlistNextUrl ?: return
        if (_uiState.value.isWatchlistPaginating) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isWatchlistPaginating = true) }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("feed.watchlist.load_more", "feed.watchlist") {
                        repository.nextPage(nextUrl)
                    }
                _uiState.update {
                    it.copy(
                        watchlistItems = it.watchlistItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                        watchlistNextUrl = page.nextUrl,
                        isWatchlistPaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "feed_watchlist_load_more")
                _uiState.update {
                    it.copy(
                        isWatchlistPaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    fun refreshFollowingUsers(forceRefresh: Boolean = false) {
        runLoading {
            val page = repository.followingUsers(_uiState.value.settings.bookmarkRestrict, forceRefresh = forceRefresh)
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

    fun removeViewHistory(ids: Collection<Long>) {
        val targetIds = ids.toSet()
        if (targetIds.isEmpty()) return
        updateSettings { settings ->
            settings.copy(
                viewHistory = settings.viewHistory.filterNot { illust -> illust.id in targetIds },
            )
        }
    }

    fun exportManagedData(uri: Uri) {
        val settings = _uiState.value.settings
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                managedDataRepository.export(uri, settings)
            }.onSuccess {
                _uiState.update { it.copy(message = str(R.string.msg_data_exported)) }
            }.onFailure { expectedFailure ->
                if (isCancellation(expectedFailure)) throw expectedFailure
                _uiState.update { it.copy(message = str(R.string.error_data_export_failed)) }
            }
        }
    }

    fun importManagedData(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val current = _uiState.value.settings
                val imported = managedDataRepository.import(uri, current)
                repository.saveSettings(imported, current)
                imported
            }.onSuccess { imported ->
                _uiState.update { it.withSettings(imported).copy(message = str(R.string.msg_data_imported)) }
            }.onFailure { expectedFailure ->
                if (isCancellation(expectedFailure)) throw expectedFailure
                _uiState.update { it.copy(message = str(R.string.error_data_import_failed)) }
            }
        }
    }
}
