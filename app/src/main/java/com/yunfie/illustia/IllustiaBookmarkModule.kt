package com.yunfie.illustia

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pagination for search/profile collections plus bookmark and recommendation behavior. */
abstract class IllustiaBookmarkModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaDetailProfileModule(app, managedDataRepository) {
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
        searchJob?.cancel()
        searchJob =
            runLoading {
                if (workType.isNovel) {
                    val page = repository.nextNovelPage(nextUrl)
                    _uiState.update {
                        it.copy(
                            searchNovelItems = it.searchNovelItems + page.items,
                            searchNovelNextUrl = page.nextUrl,
                        )
                    }
                } else {
                    val page = repository.nextPage(nextUrl)
                    _uiState.update {
                        val filteredItems =
                            page.items
                                .filter { illust -> workType.acceptsIllustType(illust.type) }
                                .visibleWithMutedTagsVisible(it.settings)
                        it.copy(
                            searchItems = it.searchItems.appendIllusts(filteredItems),
                            searchNextUrl = page.nextUrl,
                        )
                    }
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

    fun loadSelectedRelatedUsers() {
        val userId = _uiState.value.selectedUser?.id ?: return
        if (_uiState.value.selectedRelatedUsersLoading || _uiState.value.selectedRelatedUsers.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedRelatedUsersLoading = true) }
            try {
                val page = repository.relatedUsers(userId)
                _uiState.update { state ->
                    if (state.selectedUser?.id != userId) {
                        state
                    } else {
                        state.copy(
                            selectedRelatedUsers = page.users.filterNot { it.id == userId },
                            selectedRelatedUsersNextUrl = page.nextUrl,
                            selectedRelatedUsersLoading = false,
                        )
                    }
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update { state ->
                    if (state.selectedUser?.id == userId) {
                        state.copy(
                            selectedRelatedUsersLoading = false,
                            message = cleanErrorMessage(error, str(R.string.error_related_users_load_failed)),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun loadMoreSelectedRelatedUsers() {
        val userId = _uiState.value.selectedUser?.id ?: return
        val nextUrl = _uiState.value.selectedRelatedUsersNextUrl ?: return
        if (_uiState.value.selectedRelatedUsersLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedRelatedUsersLoading = true) }
            try {
                val page = repository.nextRelatedUsersPage(nextUrl)
                _uiState.update { state ->
                    if (state.selectedUser?.id != userId) {
                        state
                    } else {
                        state.copy(
                            selectedRelatedUsers =
                                state.selectedRelatedUsers.appendUserPreviews(
                                    page.users.filterNot { it.id == userId },
                                ),
                            selectedRelatedUsersNextUrl = page.nextUrl,
                            selectedRelatedUsersLoading = false,
                        )
                    }
                }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (handleAuthExpired(error)) return@launch
                _uiState.update { state ->
                    if (state.selectedUser?.id == userId) {
                        state.copy(
                            selectedRelatedUsersLoading = false,
                            message = cleanErrorMessage(error, str(R.string.error_related_users_load_failed)),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun refreshBookmarks(forceRefresh: Boolean = false) {
        val userId = _uiState.value.settings.bookmarkUserId
        if (userId == null) {
            _uiState.update {
                it.copy(loadState = LoadState.Error(str(R.string.error_pixiv_user_id_not_set)))
            }
            return
        }
        runLoading {
            val page = repository.bookmarks(userId, _uiState.value.settings.bookmarkRestrict, forceRefresh = forceRefresh)
            _uiState.update {
                it.copy(
                    bookmarkItems = page.items.visibleWithSettings(it.settings),
                    bookmarkNextUrl = page.nextUrl,
                )
            }
        }
    }

    fun refreshRecommendedTags(force: Boolean = false) {
        val state = _uiState.value
        if (state.settings.refreshToken.isBlank()) return
        val now = System.currentTimeMillis()
        val cacheAge = now - state.recommendedTagsFetchedAtMillis
        if (!force && state.recommendedTags.isNotEmpty() && cacheAge in 0 until RECOMMENDED_TAG_CACHE_TTL_MILLIS) {
            return
        }
        recommendedTagsJob?.cancel()
        recommendedTagsExpiryJob?.cancel()
        recommendedTagsJob =
            viewModelScope.launch(Dispatchers.IO) {
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
                    if (tags.isEmpty()) return@onSuccess
                    val fetchedAt = System.currentTimeMillis()
                    _uiState.update { current ->
                        current.copy(
                            recommendedTags = tags.map { it.tag },
                            recommendedTagTiles = tags,
                            recommendedTagsFetchedAtMillis = fetchedAt,
                        )
                    }
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
                }.onFailure { expectedFailure ->
                    if (isCancellation(expectedFailure)) throw expectedFailure
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

    fun toggleBookmark(
        illustId: Long,
        fallback: Illust? = null,
    ) {
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
                }
                updateIllustEverywhere(updated)
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) {
                    throw error
                }
                if (handleAuthExpired(error)) return@launch
                _uiState.update { it.copy(message = cleanErrorMessage(error, str(R.string.error_bookmark_failed))) }
            }
        }
    }
}
