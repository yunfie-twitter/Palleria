package com.yunfie.illustia

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Illustration detail, image viewer, user profile, follow, and mute interactions. */
abstract class IllustiaDetailProfileModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaAuthFeedModule(app, managedDataRepository) {
    fun openIllust(illust: Illust) {
        captureProfileReturnDetail()
        recordViewHistory(illust)
        _uiState.update {
            it.copy(selectedIllust = illust, selectedIllustUser = null, selectedIllustFirstComment = null, relatedIllusts = emptyList())
        }
        _detailNavigationRequests.tryEmit(illust.id)
        launchDetailExtras(illust)
    }

    private fun recordViewHistory(illust: Illust) {
        if (_uiState.value.settings.saveViewHistory) {
            val isBookmarked = illust.isBookmarked || _uiState.value.bookmarkItems.any { it.id == illust.id }
            val recordedIllust = if (illust.isBookmarked != isBookmarked) illust.copy(isBookmarked = isBookmarked) else illust
            val history =
                (listOf(recordedIllust) + _uiState.value.settings.viewHistory)
                    .distinctBy { it.id }
                    .take(48)
            updateSettings { it.copy(viewHistory = history) }
        }
    }

    private fun launchDetailExtras(illust: Illust) {
        detailExtrasJob?.cancel()
        detailExtrasJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val fullIllust =
                        if (illust.artistId <= 0L) {
                            runCatching { repository.illustDetail(illust.id) }.getOrNull() ?: illust
                        } else {
                            illust
                        }
                    kotlinx.coroutines.coroutineScope {
                        val relatedDeferred = async { repository.relatedIllusts(illust.id) }
                        val firstCommentDeferred =
                            async {
                                runCatching {
                                    repository.illustComments(illust.id).comments.firstOrNull()
                                }.getOrNull()
                            }
                        val userDeferred =
                            fullIllust.artistId.takeIf { it > 0L }?.let { artistId ->
                                async { repository.userDetail(artistId) }
                            }
                        val related = relatedDeferred.await()
                        val firstComment = firstCommentDeferred.await()
                        val user = userDeferred?.await()
                        _uiState.update {
                            if (it.selectedIllust?.id != illust.id) {
                                it
                            } else {
                                it.copy(
                                    selectedIllust = fullIllust,
                                    relatedIllusts = related.items.visibleRelatedWithSettings(it.settings),
                                    selectedIllustUser = user,
                                    selectedIllustFirstComment = firstComment,
                                )
                            }
                        }
                        if (fullIllust !== illust && fullIllust.artistId > 0L) {
                            val updatedHistory =
                                _uiState.value.settings.viewHistory.map {
                                    if (it.id == illust.id) fullIllust else it
                                }
                            updateSettings { it.copy(viewHistory = updatedHistory) }
                        }
                    }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
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

    override fun openIllust(illustId: Long) {
        findIllustById(illustId)?.let { illust ->
            if (illust.artistId > 0L) {
                openIllust(illust)
                return
            }
        }
        runLoading {
            val illust = repository.illustDetail(illustId)
            openIllust(illust)
        }
    }

    suspend fun getIllustPreviewUrl(illustId: Long): String? =
        withContext(Dispatchers.IO) {
            findIllustById(illustId)?.mediumImageUrl
                ?: runCatching { repository.illustDetail(illustId).mediumImageUrl }.getOrNull()
        }

    fun refreshIllustDetail(illustId: Long) {
        detailExtrasJob?.cancel()
        detailExtrasJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val fullIllust = repository.illustDetail(illustId)
                    kotlinx.coroutines.coroutineScope {
                        val relatedDeferred = async { repository.relatedIllusts(illustId) }
                        val firstCommentDeferred =
                            async {
                                runCatching {
                                    repository.illustComments(illustId).comments.firstOrNull()
                                }.getOrNull()
                            }
                        val userDeferred =
                            fullIllust.artistId.takeIf { it > 0L }?.let { artistId ->
                                async { repository.userDetail(artistId) }
                            }
                        val related = relatedDeferred.await()
                        val firstComment = firstCommentDeferred.await()
                        val user = userDeferred?.await()
                        _uiState.update {
                            if (it.selectedIllust?.id != illustId) {
                                it
                            } else {
                                it.copy(
                                    selectedIllust = fullIllust,
                                    relatedIllusts = related.items.visibleRelatedWithSettings(it.settings),
                                    selectedIllustUser = user,
                                    selectedIllustFirstComment = firstComment,
                                )
                            }
                        }
                    }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
                    _uiState.update {
                        if (it.selectedIllust?.id == illustId) {
                            it.copy(message = cleanErrorMessage(error, str(R.string.error_load_detail_failed)))
                        } else {
                            it
                        }
                    }
                }
            }
    }

    fun lazyLoadPartialIllust(illustId: Long) {
        val currentSettings = _uiState.value.settings
        val target = currentSettings.viewHistory.find { it.id == illustId } ?: return
        if (target.artistId != 0L) return

        viewModelScope.launch {
            try {
                val fullIllust = repository.illustDetail(illustId)
                updateSettings { current ->
                    val updatedHistory =
                        current.viewHistory.map {
                            if (it.id == illustId) fullIllust else it
                        }
                    current.copy(viewHistory = updatedHistory)
                }
            } catch (expectedFailure: Exception) {
                if (isCancellation(expectedFailure)) throw expectedFailure
                Log.w("IllustiaViewModel", "Failed to lazily refresh illustration detail", expectedFailure)
            }
        }
    }

    fun closeIllust() {
        detailExtrasJob?.cancel()
        _uiState.update { it.copy(selectedIllust = null, selectedIllustUser = null, selectedIllustFirstComment = null) }
    }

    fun restoreIllustDetail(
        illust: Illust,
        user: UserProfile?,
        firstComment: Comment?,
        relatedIllusts: List<Illust>,
    ) {
        detailExtrasJob?.cancel()
        _uiState.update {
            it.copy(
                selectedIllust = illust,
                selectedIllustUser = user,
                selectedIllustFirstComment = firstComment,
                relatedIllusts = relatedIllusts,
            )
        }
    }

    fun openImageViewer(
        illust: Illust,
        startPage: Int = 0,
    ) {
        val page = startPage.coerceAtLeast(0)
        _uiState.update { it.copy(imageViewerIllust = illust, imageViewerStartPage = page, imageViewerCurrentPage = page) }
    }

    fun updateImageViewerPage(page: Int) {
        _uiState.update { it.copy(imageViewerCurrentPage = page.coerceAtLeast(0)) }
    }

    suspend fun loadUgoiraPlayback(illustId: Long): UgoiraPlayback =
        withContext(Dispatchers.IO) {
            val metadata = repository.ugoiraMetadata(illustId)
            val zipUrl =
                metadata.ugoiraMetadata.zipUrls.medium.ifBlank {
                    throw IllegalStateException("Ugoira zip URL is missing.")
                }
            val requestUrl = proxyPixivImageUrl(zipUrl, _uiState.value.settings.pixivImageProxyBaseUrl)
            val cacheRoot = File(getApplication<Application>().cacheDir, "ugoira/$illustId")
            repository.prepareUgoira(
                url = requestUrl,
                cacheDir = cacheRoot.absolutePath,
                frames = metadata.ugoiraMetadata.frames,
            )
        }

    fun closeImageViewer() {
        _uiState.update { it.copy(imageViewerIllust = null, imageViewerStartPage = 0, imageViewerCurrentPage = 0) }
    }

    fun onIllustLongPress(illust: Illust) {
        _uiState.update { it.copy(longPressedIllust = illust, longPressedTag = null) }
    }

    fun onIllustLongPress(
        illustId: Long,
        fallback: Illust? = null,
    ) {
        val illust = findIllustById(illustId) ?: fallback ?: return
        onIllustLongPress(illust)
    }

    fun closeIllustOptions() {
        _uiState.update { it.copy(longPressedIllust = null) }
    }

    fun openTagOptions(
        rawTag: String,
        imageUrl: String? = null,
    ) {
        val tag = rawTag.trim().removePrefix("#").trim()
        if (tag.isBlank()) return
        _uiState.update {
            it.copy(
                longPressedIllust = null,
                longPressedTag =
                    TagPreview(
                        tag = tag,
                        imageUrl = imageUrl?.takeIf(String::isNotBlank),
                    ),
            )
        }
    }

    fun closeTagOptions() {
        _uiState.update { it.copy(longPressedTag = null) }
    }

    fun openUser(user: UserPreview) {
        openUserPage(user.id)
    }

    fun openUser(userId: Long) {
        openUserPage(userId)
    }

    fun closeUser() {
        closeUserPage()
    }

    fun openUserPage(user: UserPreview) {
        openUserPage(user.id)
    }

    override fun openUserPage(userId: Long) {
        closeUserPageJob?.cancel()
        closeUserPageJob = null
        userPageLoadJob?.cancel()
        userPageLoadJob = null
        if (userId <= 0L) {
            _uiState.update { it.copy(message = str(R.string.error_load_artist_failed)) }
            return
        }
        userPageSnapshot = snapshotUserPageState()
        captureProfileReturnDetail()
        _userNavigationRequests.tryEmit(userId)
        _uiState.update {
            it.copy(
                selectedUserId = userId,
                selectedUser = null,
                selectedUserIllusts = emptyList(),
                selectedUserNextUrl = null,
                selectedUserBookmarks = emptyList(),
                selectedUserBookmarksNextUrl = null,
                selectedRelatedUsers = emptyList(),
                selectedRelatedUsersNextUrl = null,
                selectedRelatedUsersLoading = false,
                showUserPage = true,
                userPageDismissed = false,
                message = null,
            )
        }
        val job =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val profileDeferred = async { repository.userDetail(userId) }
                    val pageDeferred = async { repository.userIllusts(userId) }
                    val profile = profileDeferred.await()
                    val page = pageDeferred.await()
                    _uiState.update { state ->
                        if (state.selectedUserId != userId) return@update state
                        state.copy(
                            selectedUser = profile,
                            selectedUserIllusts = page.items.visibleWithSettings(state.settings),
                            selectedUserNextUrl = page.nextUrl,
                            selectedUserBookmarks = emptyList(),
                            selectedUserBookmarksNextUrl = null,
                            selectedRelatedUsers = emptyList(),
                            selectedRelatedUsersNextUrl = null,
                            selectedRelatedUsersLoading = false,
                            loadState = LoadState.Loaded,
                        )
                    }
                    userPageSnapshot = null
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
                    restoreUserPageSnapshot()
                    val message = loadFailureMessage(_uiState.value, error, str(R.string.error_load_artist_failed))
                    _uiState.update {
                        it.copy(
                            message = message,
                            loadState = LoadState.Error(message),
                        )
                    }
                }
            }
        userPageLoadJob = job
        job.invokeOnCompletion {
            if (userPageLoadJob === job) userPageLoadJob = null
        }
    }

    fun hideUserPage() {
        closeUserPageJob?.cancel()
        userPageLoadJob?.cancel()
        userPageLoadJob = null
        _uiState.update {
            it.copy(userPageDismissed = true)
        }
    }

    fun closeUserPage() {
        closeUserPageJob?.cancel()
        userPageLoadJob?.cancel()
        userPageLoadJob = null
        _uiState.update {
            it.copy(
                showUserPage = false,
                userPageDismissed = true,
            )
        }
        closeUserPageJob =
            viewModelScope.launch {
                delay(350)
                _uiState.update {
                    it.copy(
                        selectedUserId = null,
                        selectedUser = null,
                        selectedUserIllusts = emptyList(),
                        selectedUserNextUrl = null,
                        selectedUserBookmarks = emptyList(),
                        selectedUserBookmarksNextUrl = null,
                        selectedRelatedUsers = emptyList(),
                        selectedRelatedUsersNextUrl = null,
                        selectedRelatedUsersLoading = false,
                        userPageDismissed = false,
                    )
                }
                closeUserPageJob = null
            }
    }

    fun restoreProfileReturnDetail(): Boolean {
        val snapshot = profileReturnDetail ?: return false
        profileReturnDetail = null
        detailExtrasJob?.cancel()
        _uiState.update {
            it.copy(
                selectedIllust = snapshot.illust,
                selectedIllustUser = snapshot.user,
                selectedIllustFirstComment = snapshot.firstComment,
                relatedIllusts = snapshot.relatedIllusts,
            )
        }
        return true
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
                    userSearchItems =
                        state.userSearchItems.map {
                            if (it.id == user.id) it.copy(isFollowed = updated.isFollowed) else it
                        },
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

    fun reportIllust(
        illustId: Long,
        problemType: String?,
        message: String?,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.reportIllust(illustId, problemType, message)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(message = str(R.string.msg_report_submitted)) }
                    onComplete?.invoke(true)
                }
            } catch (expectedFailure: Exception) {
                if (isCancellation(expectedFailure)) throw expectedFailure
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(message = cleanErrorMessage(expectedFailure, str(R.string.msg_report_failed))) }
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun reportUser(
        userId: Long,
        problemType: String?,
        message: String?,
        onComplete: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.reportUser(userId, problemType, message)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(message = str(R.string.msg_report_submitted)) }
                    onComplete?.invoke(true)
                }
            } catch (expectedFailure: Exception) {
                if (isCancellation(expectedFailure)) throw expectedFailure
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(message = cleanErrorMessage(expectedFailure, str(R.string.msg_report_failed))) }
                    onComplete?.invoke(false)
                }
            }
        }
    }
}
