package com.yunfie.illustia

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Illustration detail, image viewer, user profile, follow, and mute interactions. */
@Suppress("LargeClass")
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

    @Suppress("LongMethod")
    private fun launchDetailExtras(illust: Illust) {
        detailExtrasJob?.cancel()
        detailExtrasJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    GlitchTipTelemetry.traceAsync("illust.detail.load", "illust.detail") {
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
                                    val currentIllust = it.selectedIllust
                                    val mergedIllust =
                                        if (currentIllust != null) {
                                            if (fullIllust === illust) {
                                                currentIllust
                                            } else {
                                                fullIllust.copy(isBookmarked = currentIllust.isBookmarked)
                                            }
                                        } else {
                                            fullIllust
                                        }
                                    it.copy(
                                        selectedIllust = mergedIllust,
                                        relatedIllusts = related.items.visibleRelatedWithSettings(it.settings),
                                        selectedIllustUser = user,
                                        selectedIllustFirstComment = firstComment,
                                    )
                                }
                            }
                            if (fullIllust !== illust && fullIllust.artistId > 0L) {
                                val updatedHistory =
                                    _uiState.value.settings.viewHistory.map {
                                        if (it.id == illust.id) fullIllust.copy(isBookmarked = it.isBookmarked) else it
                                    }
                                updateSettings { it.copy(viewHistory = updatedHistory) }
                            }
                        }
                    }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
                    GlitchTipTelemetry.recordException(error, tag = "illust_detail", extras = mapOf("illustId" to illust.id))
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
        if (illustId <= 0L) return
        _detailNavigationRequests.tryEmit(illustId)
        val localIllust = findIllustById(illustId)
        if (localIllust != null && localIllust.artistId > 0L) {
            openIllust(localIllust)
            return
        }
        detailExtrasJob?.cancel()
        detailExtrasJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    GlitchTipTelemetry.traceAsync("illust.detail.load", "illust.detail") {
                        val illust = repository.illustDetail(illustId)
                        withContext(Dispatchers.Main) {
                            openIllust(illust)
                        }
                    }
                } catch (expectedFailure: Exception) {
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    if (handleAuthExpired(error)) return@launch
                    GlitchTipTelemetry.recordException(error, tag = "illust_detail", extras = mapOf("illustId" to illustId))
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(message = cleanErrorMessage(error, str(R.string.error_load_detail_failed))) }
                    }
                }
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
                            if (it.id == illustId) fullIllust.copy(isBookmarked = it.isBookmarked) else it
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

    fun openCurrentUserProfile() {
        val settings = _uiState.value.settings
        if (settings.refreshToken.isBlank()) {
            openAccountSwitcher()
            return
        }

        val cachedUserId = _uiState.value.currentAccount?.id ?: settings.bookmarkUserId
        if (cachedUserId != null && cachedUserId > 0L) {
            openUserPage(cachedUserId)
        }

        viewModelScope.launch {
            try {
                val myProfile =
                    withContext(Dispatchers.IO) {
                        repository.currentUserProfile()
                    }
                _uiState.update { current ->
                    val existing = current.currentAccount
                    val updatedAccount =
                        if (existing != null) {
                            existing.copy(
                                id = myProfile.userId,
                                name = myProfile.name,
                                account = myProfile.pixivId,
                                profileImageUrl = myProfile.profileImageUrl ?: existing.profileImageUrl,
                            )
                        } else {
                            UserProfile(
                                id = myProfile.userId,
                                name = myProfile.name,
                                account = myProfile.pixivId,
                                profileImageUrl = myProfile.profileImageUrl,
                                backgroundImageUrl = null,
                                comment = "",
                                isFollowed = false,
                            )
                        }
                    current.copy(currentAccount = updatedAccount)
                }
                saveCurrentAccount()

                if (cachedUserId != myProfile.userId) {
                    openUserPage(myProfile.userId)
                }
            } catch (expectedFailure: Exception) {
                if (isCancellation(expectedFailure)) throw expectedFailure
                if (handleAuthExpired(expectedFailure)) return@launch
                if (cachedUserId == null || cachedUserId <= 0L) {
                    val message = loadFailureMessage(_uiState.value, expectedFailure, str(R.string.error_load_artist_failed))
                    _uiState.update { it.copy(message = message) }
                }
            }
        }
    }

    suspend fun updateUserProfile(profile: UserProfileEdit): AccountEditResult =
        withContext(Dispatchers.IO) {
            val result = repository.setUserProfile(profile)
            if (result.isSucceeded) {
                _uiState.update { current ->
                    val existing = current.currentAccount
                    val updatedAccount =
                        existing?.copy(
                            name = profile.userName,
                            comment = profile.comment,
                        )
                    val selectedUser = current.selectedUser
                    val updatedSelectedUser =
                        if (selectedUser != null &&
                            (selectedUser.id == existing?.id || current.settings.accounts.any { it.userId == selectedUser.id })
                        ) {
                            selectedUser.copy(
                                name = profile.userName,
                                comment = profile.comment,
                            )
                        } else {
                            selectedUser
                        }
                    current.copy(
                        currentAccount = updatedAccount,
                        selectedUser = updatedSelectedUser,
                    )
                }
                saveCurrentAccount()
            }
            result
        }

    override fun openUserPage(userId: Long) = loadUserPage(userId, forceRefresh = false)

    fun refreshUserPage(userId: Long) = loadUserPage(userId, forceRefresh = true)

    private fun loadUserPage(
        userId: Long,
        forceRefresh: Boolean,
    ) {
        if (userId <= 0L) {
            _uiState.update { it.copy(message = str(R.string.error_load_artist_failed)) }
            return
        }
        closeUserPageJob?.cancel()
        closeUserPageJob = null
        val session = userProfileRequests.open(userId, _uiState.value.settings.refreshToken)
        userPageSnapshot = snapshotUserPageState()
        captureProfileReturnDetail()
        val cached = if (forceRefresh) null else userProfileCache.get(userId, _uiState.value.settings)
        _userNavigationRequests.tryEmit(userId)
        _uiState.update { state ->
            val empty =
                state.clearClosedUserPage().copy(
                    selectedUserId = userId,
                    showUserPage = true,
                    message = null,
                )
            cached?.restore(empty) ?: empty
        }
        if (cached != null) {
            userPageSnapshot = null
            return
        }
        session.scope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { repository.userDetail(userId, forceRefresh = forceRefresh) }
                if (!isCurrentProfileRequest(session)) return@launch
                _uiState.update { it.copy(selectedUser = profile, loadState = LoadState.Loaded) }
                userPageSnapshot = null
                userProfileCache.put(_uiState.value)
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (!isCurrentProfileRequest(session)) return@launch
                if (handleAuthExpired(error)) return@launch
                userProfileRequests.close()
                restoreUserPageSnapshot()
                val message = loadFailureMessage(_uiState.value, error, str(R.string.error_load_artist_failed))
                _uiState.update { it.copy(message = message, loadState = LoadState.Error(message)) }
            }
        }
        loadProfileWorks(session)
    }

    internal fun loadProfileWorks(
        session: UserProfileRequests.Session,
        nextUrl: String? = null,
    ) {
        if (!isCurrentProfileRequest(session) || _uiState.value.isSelectedUserIllustsPaginating) return
        _uiState.update { it.copy(isSelectedUserIllustsPaginating = true) }
        session.scope.launch {
            try {
                val page =
                    withContext(Dispatchers.IO) {
                        if (nextUrl == null) repository.userIllusts(session.userId) else repository.nextPage(nextUrl)
                    }
                if (!isCurrentProfileRequest(session)) return@launch
                val items = withContext(Dispatchers.Default) { page.items.visibleWithSettings(_uiState.value.settings) }
                if (!isCurrentProfileRequest(session)) return@launch
                _uiState.update { state ->
                    state.copy(
                        selectedUserIllusts = if (nextUrl == null) items else state.selectedUserIllusts.appendIllusts(items),
                        selectedUserNextUrl = page.nextUrl,
                        selectedUserIllustsLoaded = true,
                        isSelectedUserIllustsPaginating = false,
                    )
                }
                userProfileCache.put(_uiState.value)
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) throw error
                if (!isCurrentProfileRequest(session)) return@launch
                _uiState.update { it.copy(isSelectedUserIllustsPaginating = false) }
                if (handleAuthExpired(error)) return@launch
                GlitchTipTelemetry.recordException(error, tag = "user_illusts_load")
                _uiState.update { it.copy(message = cleanErrorMessage(error)) }
            }
        }
    }

    fun setActiveUserProfile(userId: Long?) {
        if (userId == null) {
            userProfileRequests.close()
            _uiState.update { it.copy(isSelectedUserIllustsPaginating = false, isSelectedUserBookmarksPaginating = false) }
            return
        }
        val current = userProfileRequests.current()
        if (current != null && current.userId == userId && isCurrentProfileRequest(current)) return
        val state = _uiState.value
        if (state.selectedUserId == userId && !state.userPageDismissed) {
            if (state.selectedUser == null) {
                openUserPage(userId)
            } else {
                val session = userProfileRequests.open(userId, state.settings.refreshToken)
                if (!state.selectedUserIllustsLoaded) loadProfileWorks(session)
            }
        }
    }

    fun hideUserPage() {
        closeUserPageJob?.cancel()
        userProfileRequests.close()
        _uiState.update { it.copy(isSelectedUserIllustsPaginating = false, isSelectedUserBookmarksPaginating = false) }
        _uiState.update {
            it.copy(userPageDismissed = true)
        }
    }

    fun closeUserPage() {
        closeUserPageJob?.cancel()
        userProfileRequests.close()
        _uiState.update { it.copy(isSelectedUserIllustsPaginating = false, isSelectedUserBookmarksPaginating = false) }
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
                    it.clearClosedUserPage()
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
        if (_uiState.value.isGuestMode &&
            _uiState.value.settings.refreshToken
                .isBlank()
        ) {
            promptGuestLogin("follow")
            return
        }
        runLoading {
            if (user.isFollowed) {
                repository.unfollowUser(user.id)
            } else {
                repository.followUser(user.id, _uiState.value.settings.bookmarkRestrict)
            }
            val updated = repository.userDetail(user.id)
            userProfileCache.updateUser(updated)
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
