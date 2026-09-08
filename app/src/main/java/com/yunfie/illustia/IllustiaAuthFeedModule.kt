package com.yunfie.illustia

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Authentication, native intents, search, feeds, timelines, and managed-data transfer. */
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
        runLoading {
            loadHomeInternal(_uiState.value.homeKind, forceRefresh = forceRefresh)
        }
    }

    fun refreshNovels(forceRefresh: Boolean = false) {
        runLoading {
            val page = repository.loadNovels(forceRefresh = forceRefresh)
            _uiState.update {
                it.copy(
                    novelItems = page.items,
                    novelNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun loadMoreNovels() {
        val nextUrl = _uiState.value.novelNextUrl ?: return
        runLoading {
            val page = repository.nextNovelPage(nextUrl)
            _uiState.update {
                it.copy(
                    novelItems = it.novelItems + page.items,
                    novelNextUrl = page.nextUrl,
                )
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
                    rankingModeLoadStates = it.rankingModeLoadStates + (mode to LoadState.Loading),
                )
            }
            try {
                val page = repository.loadRanking(mode, forceRefresh = forceRefresh)
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
                _uiState.update { current ->
                    current.copy(
                        rankingModeLoadStates = current.rankingModeLoadStates + (mode to LoadState.Error(cleanErrorMessage(error))),
                    )
                }
            }
        }
    }

    fun loadMoreRanking(mode: String = _uiState.value.rankingMode) {
        val nextUrl = _uiState.value.rankingModeNextUrls[mode] ?: _uiState.value.rankingNextUrl ?: return
        if (_uiState.value.rankingModeLoadStates[mode] is LoadState.Loading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    rankingModeLoadStates = it.rankingModeLoadStates + (mode to LoadState.Loading),
                )
            }
            try {
                val page = repository.nextPage(nextUrl)
                val settings = _uiState.value.settings
                _uiState.update { current ->
                    val currentList = current.rankingModeItems[mode] ?: current.rankingItems
                    val nextList = currentList.appendIllusts(page.items.visibleWithMutedTagsVisible(settings))
                    val updatedItems = current.rankingModeItems + (mode to nextList)
                    val updatedNextUrls = current.rankingModeNextUrls + (mode to page.nextUrl)
                    val updatedLoadStates = current.rankingModeLoadStates + (mode to LoadState.Idle)
                    current.copy(
                        rankingModeItems = updatedItems,
                        rankingModeNextUrls = updatedNextUrls,
                        rankingModeLoadStates = updatedLoadStates,
                        rankingItems = if (current.rankingMode == mode) nextList else current.rankingItems,
                        rankingNextUrl = if (current.rankingMode == mode) page.nextUrl else current.rankingNextUrl,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update { current ->
                    current.copy(
                        rankingModeLoadStates = current.rankingModeLoadStates + (mode to LoadState.Error(cleanErrorMessage(error))),
                    )
                }
            }
        }
    }

    fun loadMoreHome() {
        val nextUrl = _uiState.value.homeNextUrl ?: return
        runLoading {
            val page = repository.nextPage(nextUrl)
            val settings = _uiState.value.settings
            val additions = page.items.visibleWithSettings(settings).preferUnseenFeedItems(settings)
            _uiState.update {
                it.copy(
                    homeItems = it.homeItems.appendIllusts(additions),
                    homeNextUrl = page.nextUrl,
                )
            }
            rememberFeedItems(additions)
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

            else -> {
                Unit
            }
        }
        val settings = _uiState.value.settings
        if (settings.saveSearchHistory) {
            val history =
                (listOf(normalized) + settings.searchHistory)
                    .distinct()
                    .take(6)
            updateSettings { it.copy(searchHistory = history) }
        }
        searchSnapshot = snapshotSearchState()
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
        searchJob?.cancel()
        val job =
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(loadState = LoadState.Loading, message = null) }
                try {
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

                        _uiState.update {
                            it.copy(
                                searchItems =
                                    page
                                        ?.items
                                        ?.filter { illust -> workType.acceptsIllustType(illust.type) }
                                        ?.visibleWithMutedTagsVisible(it.settings)
                                        .orEmpty(),
                                searchNextUrl = page?.nextUrl,
                                searchNovelItems = novelPage?.items.orEmpty(),
                                searchNovelNextUrl = novelPage?.nextUrl,
                                userSearchItems = users?.items.orEmpty(),
                                userSearchNextUrl = users?.nextUrl,
                                loadState = LoadState.Loaded,
                            )
                        }
                    }
                    searchSnapshot = null
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
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
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                loadState = LoadState.Error(loadFailureMessage(it, error)),
                            )
                        }
                    }
                }
            }
        searchJob = job
        job.invokeOnCompletion {
            if (searchJob === job) {
                searchJob = null
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
        runLoading {
            val page = repository.followingIllusts(_uiState.value.settings.bookmarkRestrict, forceRefresh = forceRefresh)
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
        runLoading {
            val page =
                repository.search(
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
