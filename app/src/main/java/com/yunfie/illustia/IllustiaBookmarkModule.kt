package com.yunfie.illustia

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.data.BookmarkToggleScheduler
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchWorkType
import com.yunfie.illustia.models.pixiv.BookmarkTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

private const val TARGET_SEARCH_PAGE_BATCH = 24
private const val MAX_SEARCH_LOAD_MORE_PAGES = 20

/** Pagination for search/profile collections plus bookmark and recommendation behavior. */
@Suppress("LargeClass")
abstract class IllustiaBookmarkModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaDetailProfileModule(app, managedDataRepository) {
    private val bookmarkRefreshGeneration = AtomicLong()

    abstract fun saveImage(
        url: String,
        filename: String,
    )

    fun loadMoreSearch() {
        val workType = _uiState.value.settings.searchWorkType
        val nextUrl =
            if (workType.isNovel) {
                _uiState.value.searchNovelNextUrl
            } else {
                _uiState.value.searchNextUrl
            } ?: return
        if (_uiState.value.isSearchPaginating) return
        searchJob?.cancel()
        val job =
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(isSearchPaginating = true) }
                try {
                    if (workType.isNovel) {
                        loadMoreSearchNovelsInternal(nextUrl)
                    } else {
                        loadMoreSearchIllustsInternal(nextUrl, workType)
                    }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
                    _uiState.update {
                        it.copy(
                            isSearchPaginating = false,
                            message = cleanErrorMessage(error),
                        )
                    }
                }
            }
        searchJob = job
        job.invokeOnCompletion {
            if (searchJob === job) searchJob = null
        }
    }

    private suspend fun loadMoreSearchNovelsInternal(nextUrl: String) {
        val page = repository.nextNovelPage(nextUrl)
        _uiState.update {
            it.copy(
                searchNovelItems = it.searchNovelItems + page.items.visibleWithSettings(it.settings),
                searchNovelNextUrl = page.nextUrl,
                isSearchPaginating = false,
            )
        }
    }

    private suspend fun loadMoreSearchIllustsInternal(
        nextUrl: String,
        workType: SearchWorkType,
    ) {
        var currentNextUrl: String? = nextUrl
        val collectedItems = mutableListOf<Illust>()
        val settings = _uiState.value.settings
        var loopCount = 0
        val targetBatch = if (workType == SearchWorkType.Artworks) 1 else TARGET_SEARCH_PAGE_BATCH
        while (
            collectedItems.size < targetBatch &&
            currentNextUrl != null &&
            loopCount < MAX_SEARCH_LOAD_MORE_PAGES
        ) {
            loopCount++
            val page = repository.nextPage(currentNextUrl)
            val filteredItems =
                page.items
                    .filter { illust -> workType.acceptsIllustType(illust.type) }
                    .visibleWithMutedTagsVisible(settings)
            collectedItems.addAll(filteredItems)
            currentNextUrl = page.nextUrl
            if (workType == SearchWorkType.Artworks) break
        }
        _uiState.update {
            it.copy(
                searchItems = it.searchItems.appendIllusts(collectedItems),
                searchNextUrl = currentNextUrl,
                isSearchPaginating = false,
            )
        }
    }

    fun loadMoreUserSearch() {
        val nextUrl = _uiState.value.userSearchNextUrl ?: return
        if (_uiState.value.isUserSearchPaginating) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isUserSearchPaginating = true) }
            try {
                val page = repository.nextUserSearchPage(nextUrl)
                _uiState.update {
                    val mutedUsers = it.mutedUsersSet
                    val filteredItems = page.items.filterNot { item -> item.id in mutedUsers }
                    it.copy(
                        userSearchItems = it.userSearchItems.appendUserPreviews(filteredItems),
                        userSearchNextUrl = page.nextUrl,
                        isUserSearchPaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update {
                    it.copy(
                        isUserSearchPaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    fun loadMoreUserIllusts() {
        val session =
            userProfileRequests.current()
                ?: _uiState.value.selectedUserId?.let { userProfileRequests.open(it, _uiState.value.settings.refreshToken) }
                ?: return
        val state = _uiState.value
        if (!state.selectedUserIllustsLoaded) {
            loadProfileWorks(session)
        } else {
            state.selectedUserNextUrl?.let { loadProfileWorks(session, it) }
        }
    }

    fun loadSelectedUserBookmarks() {
        if (_uiState.value.selectedUserBookmarksLoaded) return
        loadProfileBookmarks()
    }

    fun loadMoreSelectedUserBookmarks() {
        val state = _uiState.value
        if (!state.selectedUserBookmarksLoaded) {
            loadProfileBookmarks()
        } else {
            state.selectedUserBookmarksNextUrl?.let { loadProfileBookmarks(it) }
        }
    }

    private fun loadProfileBookmarks(nextUrl: String? = null) {
        val session =
            userProfileRequests.current()
                ?: _uiState.value.selectedUserId?.let { userProfileRequests.open(it, _uiState.value.settings.refreshToken) }
                ?: return
        if (!isCurrentProfileRequest(session) || _uiState.value.isSelectedUserBookmarksPaginating) return
        _uiState.update {
            it.copy(
                isSelectedUserBookmarksPaginating = true,
                selectedUserBookmarksError = null,
            )
        }
        session.scope.launch {
            try {
                val page =
                    withContext(Dispatchers.IO) {
                        if (nextUrl == null) repository.bookmarks(session.userId, Restrict.Public) else repository.nextPage(nextUrl)
                    }
                if (!isCurrentProfileRequest(session)) return@launch
                val items = withContext(Dispatchers.Default) { page.items.visibleWithSettings(_uiState.value.settings) }
                if (!isCurrentProfileRequest(session)) return@launch
                _uiState.update { state ->
                    state.copy(
                        selectedUserBookmarks = if (nextUrl == null) items else state.selectedUserBookmarks.appendIllusts(items),
                        selectedUserBookmarksNextUrl = page.nextUrl,
                        selectedUserBookmarksLoaded = true,
                        selectedUserBookmarksError = null,
                        isSelectedUserBookmarksPaginating = false,
                    )
                }
                userProfileCache.put(_uiState.value)
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (!isCurrentProfileRequest(session)) return@launch
                _uiState.update { it.copy(isSelectedUserBookmarksPaginating = false) }
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "user_bookmarks_load")
                val errorMsg = profileFailureMessage(error)
                _uiState.update { state ->
                    if (nextUrl == null && state.selectedUserBookmarks.isEmpty()) {
                        state.copy(selectedUserBookmarksError = errorMsg)
                    } else {
                        state.copy(message = cleanErrorMessage(error))
                    }
                }
            }
        }
    }

    fun loadSelectedRelatedUsers(
        targetUserId: Long? = null,
        force: Boolean = false,
    ) {
        val userId = targetUserId ?: _uiState.value.selectedUser?.id ?: return
        val cachedForSameUser = hasCachedRelatedUsers(userId)
        if (shouldSkipRelatedUsersLoad(force, cachedForSameUser)) return
        viewModelScope.launch(Dispatchers.IO) {
            markRelatedUsersLoading(userId, force, cachedForSameUser)
            fetchSelectedRelatedUsers(userId)
        }
    }

    private fun hasCachedRelatedUsers(userId: Long): Boolean {
        val state = _uiState.value
        return state.selectedRelatedUsers.isNotEmpty() && state.selectedRelatedUsersUserId == userId
    }

    private fun shouldSkipRelatedUsersLoad(
        force: Boolean,
        cachedForSameUser: Boolean,
    ): Boolean {
        if (force) return false
        val state = _uiState.value
        return state.selectedRelatedUsersLoading || state.selectedRelatedUsersRefreshing || cachedForSameUser
    }

    private fun markRelatedUsersLoading(
        userId: Long,
        force: Boolean,
        cachedForSameUser: Boolean,
    ) {
        _uiState.update {
            it.copy(
                selectedRelatedUsers = if (cachedForSameUser) it.selectedRelatedUsers else emptyList(),
                selectedRelatedUsersNextUrl = if (cachedForSameUser) it.selectedRelatedUsersNextUrl else null,
                selectedRelatedUsersUserId = userId,
                selectedRelatedUsersLoading = !force && !cachedForSameUser,
                selectedRelatedUsersRefreshing = force,
                selectedRelatedUsersError = null,
            )
        }
    }

    private suspend fun fetchSelectedRelatedUsers(userId: Long) {
        try {
            val page = repository.relatedUsers(userId)
            _uiState.update { state ->
                if (state.selectedRelatedUsersUserId != userId) return@update state
                state.copy(
                    selectedRelatedUsers = page.users.filterNot { it.id == userId },
                    selectedRelatedUsersNextUrl = page.nextUrl,
                    selectedRelatedUsersUserId = userId,
                    selectedRelatedUsersLoading = false,
                    selectedRelatedUsersRefreshing = false,
                    selectedRelatedUsersError = null,
                )
            }
        } catch (expectedFailure: Exception) {
            if (isCancellation(expectedFailure)) throw expectedFailure
            handleRelatedUsersLoadFailure(userId, expectedFailure)
        }
    }

    private suspend fun handleRelatedUsersLoadFailure(
        userId: Long,
        error: Exception,
    ) {
        if (handleAuthExpired(error)) return
        _uiState.update { state ->
            if (state.selectedRelatedUsersUserId != userId) return@update state
            val message = cleanErrorMessage(error, str(R.string.error_related_users_load_failed))
            state.copy(
                selectedRelatedUsers = emptyList(),
                selectedRelatedUsersNextUrl = null,
                selectedRelatedUsersLoading = false,
                selectedRelatedUsersRefreshing = false,
                selectedRelatedUsersError = message,
                message = message,
            )
        }
    }

    fun loadMoreSelectedRelatedUsers(targetUserId: Long? = null) {
        val userId = targetUserId ?: _uiState.value.selectedUser?.id ?: return
        val nextUrl = _uiState.value.selectedRelatedUsersNextUrl
        if (nextUrl == null || _uiState.value.selectedRelatedUsersPaginating || _uiState.value.selectedRelatedUsersLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedRelatedUsersPaginating = true) }
            try {
                val page = repository.nextRelatedUsersPage(nextUrl)
                _uiState.update { state ->
                    state.copy(
                        selectedRelatedUsers =
                            state.selectedRelatedUsers.appendUserPreviews(
                                page.users.filterNot { it.id == userId },
                            ),
                        selectedRelatedUsersNextUrl = page.nextUrl,
                        selectedRelatedUsersPaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (!handleAuthExpired(error)) {
                    _uiState.update { state ->
                        state.copy(
                            selectedRelatedUsersPaginating = false,
                            message = cleanErrorMessage(error, str(R.string.error_related_users_load_failed)),
                        )
                    }
                }
            }
        }
    }

    fun refreshBookmarks(forceRefresh: Boolean = false) {
        val requestState = _uiState.value
        val userId = requestState.settings.bookmarkUserId
        if (userId == null) {
            _uiState.update {
                it.copy(loadState = LoadState.Error(str(R.string.error_pixiv_user_id_not_set)))
            }
            return
        }
        val generation = bookmarkRefreshGeneration.incrementAndGet()
        val restrict = requestState.settings.bookmarkRestrict
        val tag = requestState.bookmarkTag
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isBookmarkRefreshing = forceRefresh,
                    loadState = if (it.bookmarkItems.isEmpty()) LoadState.Loading else it.loadState,
                )
            }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("bookmarks.refresh", "bookmarks") {
                        repository.bookmarks(userId, restrict, forceRefresh = forceRefresh, tag = tag)
                    }
                if (generation != bookmarkRefreshGeneration.get()) return@launch
                _uiState.update {
                    it.copy(
                        bookmarkItems = page.items.visibleWithSettings(it.settings),
                        bookmarkNextUrl = page.nextUrl,
                        isBookmarkRefreshing = false,
                        loadState = LoadState.Loaded,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (generation != bookmarkRefreshGeneration.get()) return@launch
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "bookmarks_refresh")
                _uiState.update {
                    it.copy(
                        isBookmarkRefreshing = false,
                        loadState = LoadState.Error(loadFailureMessage(it, error)),
                    )
                }
            }
        }
    }

    fun selectBookmarkTag(
        tag: String?,
        restrict: Restrict,
    ) {
        if (_uiState.value.settings.bookmarkRestrict != restrict) {
            updateSettings { it.copy(bookmarkRestrict = restrict) }
        }
        _uiState.update {
            it.copy(
                bookmarkTag = tag,
                bookmarkItems = emptyList(),
                bookmarkNextUrl = null,
                isBookmarkPaginating = false,
            )
        }
        refreshBookmarks()
    }

    fun refreshRecommendedTags(force: Boolean = false) {
        val state = _uiState.value
        if (state.settings.refreshToken.isBlank()) return
        val now = System.currentTimeMillis()
        val cacheAge = now - state.recommendedTagsFetchedAtMillis
        if (!force && state.recommendedTagsFetchedAtMillis > 0L && cacheAge in 0 until RECOMMENDED_TAG_CACHE_TTL_MILLIS) {
            return
        }
        recommendedTagsJob?.cancel()
        recommendedTagsExpiryJob?.cancel()
        _uiState.update { it.copy(recommendedTagsLoading = true, recommendedTagsError = null) }
        recommendedTagsJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (isOffline()) {
                    failRecommendedTagsOffline()
                    return@launch
                }
                runCatching {
                    val tags =
                        repository
                            .trendingTags()
                            .distinct()
                            .take(24)
                    if (tags.isEmpty()) return@runCatching emptyList<RecommendedTagTile>()

                    kotlinx.coroutines.coroutineScope {
                        tags
                            .map { tag ->
                                async {
                                    val imageUrl = loadRecommendedTagImage(tag)
                                    RecommendedTagTile(tag = tag, imageUrl = imageUrl)
                                }
                            }.awaitAll()
                    }
                }.onSuccess { tags ->
                    val fetchedAt = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            recommendedTags = tags.map { it.tag },
                            recommendedTagTiles = tags,
                            recommendedTagsFetchedAtMillis = fetchedAt,
                            recommendedTagsLoading = false,
                            recommendedTagsError = null,
                        )
                    }
                    scheduleRecommendedTagsExpiry(fetchedAt)
                }.onFailure { expectedFailure ->
                    if (isCancellation(expectedFailure)) throw expectedFailure
                    _uiState.update { current ->
                        current.copy(
                            recommendedTagsLoading = false,
                            recommendedTagsError = loadFailureMessage(current, expectedFailure),
                        )
                    }
                }
            }
    }

    // Recommended tags are never cached locally, so there is nothing to fall back to offline.
    private fun failRecommendedTagsOffline() {
        _uiState.update {
            it.copy(
                recommendedTagsLoading = false,
                recommendedTagsError = str(R.string.offline_no_cache),
            )
        }
    }

    private fun scheduleRecommendedTagsExpiry(fetchedAt: Long) {
        recommendedTagsExpiryJob =
            viewModelScope.launch {
                delay(RECOMMENDED_TAG_CACHE_TTL_MILLIS)
                _uiState.update { current ->
                    if (current.recommendedTagsFetchedAtMillis != fetchedAt) {
                        current
                    } else {
                        current.copy(recommendedTagsFetchedAtMillis = 0L)
                    }
                }
            }
    }

    fun loadMoreBookmarks() {
        val requestState = _uiState.value
        val nextUrl = requestState.bookmarkNextUrl ?: return
        if (requestState.isBookmarkPaginating) return
        val generation = bookmarkRefreshGeneration.get()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBookmarkPaginating = true) }
            try {
                val page =
                    GlitchTipTelemetry.traceAsync("bookmarks.load_more", "bookmarks") {
                        repository.nextPage(nextUrl)
                    }
                if (generation != bookmarkRefreshGeneration.get()) return@launch
                _uiState.update {
                    it.copy(
                        bookmarkItems = it.bookmarkItems.appendIllusts(page.items.visibleWithSettings(it.settings)),
                        bookmarkNextUrl = page.nextUrl,
                        isBookmarkPaginating = false,
                    )
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (generation != bookmarkRefreshGeneration.get()) return@launch
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "bookmarks_load_more")
                _uiState.update {
                    it.copy(
                        isBookmarkPaginating = false,
                        message = cleanErrorMessage(error),
                    )
                }
            }
        }
    }

    private val _watchlistRefreshRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val watchlistRefreshRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _watchlistRefreshRequests

    fun refreshWatchlist() {
        _watchlistRefreshRequests.tryEmit(Unit)
    }

    fun toggleBookmark(
        illust: Illust,
        restrict: com.yunfie.illustia.models.Restrict? = null,
    ) {
        if (illust.isBookmarked) {
            _uiState.update { it.copy(pendingBookmarkRemoval = illust) }
            return
        }
        performToggleBookmark(illust, restrict)
    }

    fun openBookmarkTagPicker(illust: Illust) {
        val settings = _uiState.value.settings
        if (settings.refreshToken.isBlank()) {
            _uiState.update { it.copy(message = str(R.string.error_bookmark_failed)) }
            return
        }
        val restrict =
            if (settings.privateBookmarkDefault) Restrict.Private else settings.bookmarkRestrict
        _uiState.update {
            it.copy(
                bookmarkTagPickerIllust = illust,
                bookmarkTagPickerTags = emptyList(),
                bookmarkTagPickerLoading = settings.bookmarkUserId != null,
                bookmarkTagPickerError = null,
                bookmarkTagPickerRestrict = restrict,
            )
        }
        val userId = settings.bookmarkUserId ?: return
        viewModelScope.launch {
            loadBookmarkTagOptions(illust.id, userId, settings.refreshToken, restrict)
        }
    }

    fun updateBookmarkTagPickerRestrict(restrict: Restrict) {
        val state = _uiState.value
        val illust = state.bookmarkTagPickerIllust
        val userId = state.settings.bookmarkUserId
        if (illust == null || userId == null || state.bookmarkTagPickerRestrict == restrict) return
        _uiState.update {
            it.copy(bookmarkTagPickerRestrict = restrict, bookmarkTagPickerLoading = true, bookmarkTagPickerError = null)
        }
        viewModelScope.launch {
            loadBookmarkTagOptions(illust.id, userId, state.settings.refreshToken, restrict)
        }
    }

    fun retryBookmarkTagPickerTags() {
        val state = _uiState.value
        val illust = state.bookmarkTagPickerIllust ?: return
        val userId = state.settings.bookmarkUserId ?: return
        val refreshToken = state.settings.refreshToken
        val restrict = state.bookmarkTagPickerRestrict
        _uiState.update { current ->
            if (current.bookmarkTagPickerIllust?.id == illust.id && current.bookmarkTagPickerRestrict == restrict) {
                current.copy(bookmarkTagPickerLoading = true, bookmarkTagPickerError = null)
            } else {
                current
            }
        }
        viewModelScope.launch {
            loadBookmarkTagOptions(illust.id, userId, refreshToken, restrict)
        }
    }

    private suspend fun loadBookmarkTagOptions(
        illustId: Long,
        userId: Long,
        refreshToken: String,
        restrict: Restrict,
    ) {
        runCatching { repository.illustBookmarkTags(userId, restrict) }
            .onSuccess { tags ->
                _uiState.update { state ->
                    if (state.bookmarkTagPickerIllust?.id == illustId &&
                        state.settings.refreshToken == refreshToken &&
                        state.bookmarkTagPickerRestrict == restrict
                    ) {
                        state.copy(bookmarkTagPickerTags = tags, bookmarkTagPickerLoading = false, bookmarkTagPickerError = null)
                    } else {
                        state
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update { state ->
                    if (state.bookmarkTagPickerIllust?.id == illustId &&
                        state.settings.refreshToken == refreshToken &&
                        state.bookmarkTagPickerRestrict == restrict
                    ) {
                        state.copy(bookmarkTagPickerLoading = false, bookmarkTagPickerError = cleanErrorMessage(error))
                    } else {
                        state
                    }
                }
            }
    }

    fun closeBookmarkTagPicker() {
        _uiState.update {
            it.copy(
                bookmarkTagPickerIllust = null,
                bookmarkTagPickerTags = emptyList(),
                bookmarkTagPickerLoading = false,
                bookmarkTagPickerError = null,
            )
        }
    }

    fun confirmBookmarkTagPicker(
        illust: Illust,
        restrict: Restrict,
        tags: List<String>,
    ) {
        closeBookmarkTagPicker()
        enqueueBookmarkState(illust, bookmarked = true, restrict = restrict, bookmarkTags = tags)
    }

    fun toggleBookmark(
        illustId: Long,
        fallback: Illust? = null,
        restrict: com.yunfie.illustia.models.Restrict? = null,
    ) {
        val illust = findIllustById(illustId) ?: fallback ?: return
        toggleBookmark(illust, restrict)
    }

    fun cancelBookmarkRemoval() {
        _uiState.update { it.copy(pendingBookmarkRemoval = null) }
    }

    fun confirmBookmarkRemoval() {
        val illust = _uiState.value.pendingBookmarkRemoval ?: return
        _uiState.update { it.copy(pendingBookmarkRemoval = null) }
        performToggleBookmark(illust)
    }

    private fun performToggleBookmark(
        illust: Illust,
        explicitRestrict: com.yunfie.illustia.models.Restrict? = null,
    ) {
        val settings = _uiState.value.settings
        if (settings.refreshToken.isBlank()) {
            _uiState.update { it.copy(message = str(R.string.error_bookmark_failed)) }
            return
        }
        val selectedRestrict =
            explicitRestrict
                ?: if (settings.privateBookmarkDefault) Restrict.Private else settings.bookmarkRestrict
        enqueueBookmarkState(illust, bookmarked = !illust.isBookmarked, restrict = selectedRestrict)
    }

    private fun enqueueBookmarkState(
        illust: Illust,
        bookmarked: Boolean,
        restrict: Restrict,
        bookmarkTags: List<String> = emptyList(),
    ) {
        val settings = _uiState.value.settings
        if (settings.refreshToken.isBlank()) {
            _uiState.update { it.copy(message = str(R.string.error_bookmark_failed)) }
            return
        }
        // 見た目はタップした瞬間に反転する。通信は WorkManager の永続ジョブに載せるので、
        // 画面やアプリを閉じてもキャンセルされず、次回起動時にも再試行される。
        val bookmarkCountDelta =
            when {
                bookmarked && !illust.isBookmarked -> 1
                !bookmarked && illust.isBookmarked -> -1
                else -> 0
            }
        val optimistic =
            illust.copy(
                isBookmarked = bookmarked,
                totalBookmarks = (illust.totalBookmarks + bookmarkCountDelta).coerceAtLeast(0),
            )
        updateIllustEverywhere(optimistic)
        val enqueued =
            runCatching {
                BookmarkToggleScheduler.enqueue(
                    context = getApplication(),
                    illustId = illust.id,
                    bookmarked = bookmarked,
                    restrict = restrict,
                    followRestrict = settings.followRestrict,
                    bookmarkUserId = settings.bookmarkUserId ?: 0L,
                    artistId = illust.artistId,
                    tags = illust.tags.take(3),
                    bookmarkTags = bookmarkTags.take(10),
                )
            }.isSuccess
        if (!enqueued) {
            updateIllustEverywhere(illust)
            _uiState.update { it.copy(message = str(R.string.error_bookmark_failed)) }
        }
    }
}
