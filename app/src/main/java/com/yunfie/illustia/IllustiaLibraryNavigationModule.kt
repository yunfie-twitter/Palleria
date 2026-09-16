package com.yunfie.illustia

import android.app.Application
import android.net.ConnectivityManager
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import com.yunfie.illustia.data.AnimatedGifEncoder
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.data.UgoiraMp4Encoder
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.settings.db.SavedIllustPageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** Downloads, offline library, notifications, settings navigation, and account switching. */
@Suppress("LargeClass")
abstract class IllustiaLibraryNavigationModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaBookmarkModule(app, managedDataRepository) {
    override fun saveImage(
        url: String,
        filename: String,
    ) {
        val queueId = System.nanoTime()
        val queuedIllust = resolveDownloadIllust(filename)
        val queueTitle = queuedIllust?.title?.takeIf { it.isNotBlank() } ?: filename
        val queueSubtitle =
            queuedIllust?.artistName?.takeIf { it.isNotBlank() }
                ?: str(R.string.download_queue_waiting)
        viewModelScope.launch(Dispatchers.IO) {
            enqueueDownloadQueue(queueId, queueTitle, queueSubtitle, DownloadQueueStatus.Waiting)
            var terminalStatus: DownloadQueueStatus? = null
            acquireDownloadSlot()
            updateDownloadQueueStatus(queueId, DownloadQueueStatus.Downloading)
            _uiState.update { it.copy(loadState = LoadState.Loading, message = null) }
            try {
                val currentIllust = resolveDownloadIllust(filename)
                val targetName = buildDownloadPath(filename, currentIllust)
                val checkUrl = resolveCheckUrl(url, currentIllust)
                val (finalName, clearOld) =
                    resolveDuplicateTarget(targetName, checkUrl, _uiState.value.settings.duplicateSaveMode)
                        ?: run {
                            terminalStatus = DownloadQueueStatus.Skipped
                            _uiState.update {
                                it.copy(loadState = LoadState.Loaded, message = str(R.string.msg_save_skipped_duplicate))
                            }
                            return@launch
                        }
                executeGalleryDownload(currentIllust, url, finalName, clearOld)
                terminalStatus = DownloadQueueStatus.Completed
                _uiState.update { it.copy(loadState = LoadState.Loaded) }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                if (isCancellation(error)) {
                    throw error
                }
                terminalStatus = DownloadQueueStatus.Failed
                if (handleAuthExpired(error)) return@launch
                _uiState.update { it.copy(loadState = LoadState.Error(cleanErrorMessage(error, str(R.string.error_save_failed)))) }
            } finally {
                terminalStatus?.let { updateDownloadQueueStatus(queueId, it) }
                releaseDownloadSlot()
            }
        }
    }

    private fun resolveCheckUrl(
        url: String,
        illust: Illust?,
    ): String =
        if (illust?.type == "ugoira") {
            "https://www.pixiv.net/artworks/${illust.id}.gif"
        } else {
            proxyPixivImageUrl(url, _uiState.value.settings.pixivImageProxyBaseUrl)
        }

    private fun resolveDuplicateTarget(
        targetName: String,
        checkUrl: String,
        mode: String,
    ): Pair<String, Boolean>? {
        if (!imageStore.exists(targetName, checkUrl)) {
            return targetName to false
        }
        return when (mode) {
            "overwrite" -> targetName to true
            "always" -> findUniqueTargetName(targetName, checkUrl) to false
            else -> null
        }
    }

    private fun findUniqueTargetName(
        targetName: String,
        checkUrl: String,
    ): String {
        var counter = 1
        var candidate = "${targetName}_$counter"
        while (imageStore.exists(candidate, checkUrl)) {
            counter++
            candidate = "${targetName}_$counter"
        }
        return candidate
    }

    private suspend fun executeGalleryDownload(
        illust: Illust?,
        url: String,
        targetName: String,
        clearOld: Boolean,
    ) {
        if (illust?.type == "ugoira") {
            downloadUgoiraToGallery(illust, targetName, clearOld)
        } else {
            downloadImageToGallery(url, targetName, clearOld)
        }
        if (illust != null && shouldAutoBookmark(illust, url)) {
            val settings = _uiState.value.settings
            val restrict = if (settings.privateBookmarkDefault) Restrict.Private else settings.bookmarkRestrict
            val updated = repository.toggleBookmark(illust, restrict)
            updateIllustEverywhere(updated)
        }
    }

    fun saveImages(
        urls: List<String>,
        filenamePrefix: String,
    ) {
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

    fun saveOfflineImage(
        url: String,
        filename: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_uiState.value.settings.offlineWifiOnly && getApplication<Application>().applicationContext.isNetworkMetered()) {
                    _uiState.update { it.copy(message = str(R.string.offline_wifi_only_desc)) }
                    return@launch
                }
                val requestUrl = proxyPixivImageUrl(url, _uiState.value.settings.pixivImageProxyBaseUrl)
                val currentSize = settingsStore.getSavedIllustStorageBytes()
                if (currentSize >= _uiState.value.settings.offlineStorageLimitBytes) {
                    _uiState.update { it.copy(message = str(R.string.offline_capacity_limit_desc)) }
                    return@launch
                }
                val request =
                    Request
                        .Builder()
                        .url(requestUrl)
                        .header("Referer", "https://www.pixiv.net/")
                        .header("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Palleria)")
                        .build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception(str(R.string.error_save_failed) + " (${response.code})")
                    val body = response.body
                    val contentType = body.contentType()?.toString()
                    val file = saveOfflineFile(filename, requestUrl, contentType, body.byteStream())
                    val current = _uiState.value.selectedIllust ?: return@use
                    val pages =
                        listOf(
                            SavedIllustPageEntity().apply {
                                illustId = current.id
                                pageIndex = 0
                                localPath = file.absolutePath
                                sourceUrl = requestUrl
                            },
                        )
                    settingsStore.saveSavedIllust(
                        SavedIllustEntity().apply {
                            illustId = current.id
                            title = current.title
                            artistName = current.artistName
                            artistId = current.artistId
                            thumbUrl = current.thumbnailUrl
                            localCoverPath = file.absolutePath
                            localPagePathsJson = "[\"${file.absolutePath.replace("\\", "\\\\")}\"]"
                            pageCount = 1
                            savedAt = System.currentTimeMillis()
                            saveGroup = current.artistName
                            xRestrict =
                                if (
                                    current.tags.any {
                                        it.equals("R-18", ignoreCase = true) ||
                                            it.equals("R18", ignoreCase = true) ||
                                            it.equals("R-18G", ignoreCase = true)
                                    }
                                ) {
                                    1
                                } else {
                                    0
                                }
                        },
                        pages,
                    )
                    loadSavedLibrary()
                    _uiState.update { it.copy(message = str(R.string.detail_save_offline)) }
                }
            } catch (expectedFailure: Exception) {
                val e = expectedFailure
                if (isCancellation(e)) throw e
                _uiState.update { it.copy(message = cleanErrorMessage(e, str(R.string.error_save_failed))) }
            }
        }
    }

    fun openOfflineLibrary() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.OfflineLibrary)
    }

    fun openDownloadQueue() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.DownloadQueue)
    }

    fun clearFinishedDownloads() {
        _uiState.update { state ->
            state.copy(
                downloadQueue =
                    state.downloadQueue.filter {
                        it.status == DownloadQueueStatus.Waiting ||
                            it.status == DownloadQueueStatus.Downloading
                    },
                message = str(R.string.msg_download_history_cleared),
            )
        }
    }

    fun loadSavedLibrary() {
        if (savedLibraryJob?.isActive == true) return
        savedLibraryJob =
            viewModelScope.launch(Dispatchers.IO) {
                val saved = settingsStore.getSavedIllusts()
                _uiState.update { it.copy(savedIllusts = saved) }
            }
    }

    fun openSavedIllustViewer(illustId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = settingsStore.getSavedIllust(illustId) ?: return@launch
            _uiState.update { it.copy(selectedSavedIllustId = saved.illust.illustId) }
            _navigationRequests.tryEmit(IllustiaNavigationRequest.SavedIllustViewer)
        }
    }

    fun closeSavedIllustViewer() {
        _uiState.update { it.copy(selectedSavedIllustId = null) }
    }

    fun deleteSavedIllust(illustId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.deleteSavedIllust(illustId)
            _uiState.update { it.copy(savedIllusts = it.savedIllusts.filterNot { item -> item.illustId == illustId }) }
        }
    }

    fun updateOfflineWifiOnly(value: Boolean) {
        updateSettings { it.copy(offlineWifiOnly = value) }
    }

    fun updateOfflineStorageLimitBytes(value: Long) {
        updateSettings { it.copy(offlineStorageLimitBytes = value) }
    }

    private fun android.content.Context.isNetworkMetered(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        return runCatching { cm?.isActiveNetworkMetered ?: false }.getOrDefault(false)
    }

    private suspend fun acquireDownloadSlot() {
        while (true) {
            val acquired =
                downloadMutex.withLock {
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

    private fun enqueueDownloadQueue(
        id: Long,
        title: String,
        subtitle: String,
        status: DownloadQueueStatus,
    ) {
        _uiState.update { state ->
            state.copy(
                downloadQueue =
                    (
                        listOf(
                            DownloadQueueEntry(
                                id = id,
                                title = title,
                                subtitle = subtitle,
                                status = status,
                            ),
                        ) + state.downloadQueue
                    ).take(32),
            )
        }
    }

    private fun updateDownloadQueueStatus(
        id: Long,
        status: DownloadQueueStatus,
    ) {
        _uiState.update { state ->
            state.copy(
                downloadQueue =
                    state.downloadQueue
                        .map { entry ->
                            if (entry.id == id) {
                                entry.copy(status = status, timestampMillis = System.currentTimeMillis())
                            } else {
                                entry
                            }
                        }.take(32),
            )
        }
    }

    private fun buildDownloadPath(
        filename: String,
        illust: Illust?,
    ): String {
        val settings = _uiState.value.settings
        return buildDownloadPath(
            filename = filename,
            illust = illust,
            groupByArtist = settings.downloadFolderByArtist,
            groupByWork = settings.downloadFolderByWork,
        )
    }

    private fun saveOfflineFile(
        filename: String,
        sourceUrl: String,
        responseMimeType: String?,
        input: java.io.InputStream,
    ): File {
        val dir = settingsStore.savedIllustDir()
        dir.mkdirs()
        val target = File(dir, filename.withImageExtension(sourceUrl, responseMimeType))
        input.use { stream ->
            FileOutputStream(target).use { output -> stream.copyTo(output) }
        }
        return target
    }

    private fun resolveDownloadIllust(filename: String): Illust? =
        extractIllustId(filename)?.let(::findIllustById)
            ?: _uiState.value.selectedIllust
            ?: _uiState.value.imageViewerIllust

    private fun downloadImageToGallery(
        url: String,
        filename: String,
        clearOld: Boolean = false,
    ) {
        val requestUrl = proxyPixivImageUrl(url, _uiState.value.settings.pixivImageProxyBaseUrl)
        val request =
            Request
                .Builder()
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
                clearOld = clearOld,
            )
        }
    }

    private suspend fun downloadUgoiraToGallery(
        illust: Illust,
        filename: String,
        clearOld: Boolean = false,
    ) {
        val playback = loadUgoiraPlayback(illust.id)
        if (playback.frames.isEmpty()) {
            throw IllegalStateException(str(R.string.ugoira_load_failed))
        }
        val cacheDir = getApplication<Application>().cacheDir
        if (_uiState.value.settings.ugoiraSaveFormat == "gif") {
            saveUgoiraAsGif(illust.id, playback.frames, filename, cacheDir, clearOld)
        } else {
            saveUgoiraAsMp4(illust.id, playback.frames, filename, cacheDir, clearOld)
        }
    }

    private fun saveUgoiraAsGif(
        illustId: Long,
        frames: List<UgoiraPlaybackFrame>,
        filename: String,
        cacheDir: File,
        clearOld: Boolean = false,
    ) {
        val tempGif = File.createTempFile("ugoira_${illustId}_", ".gif", cacheDir)
        try {
            tempGif.outputStream().buffered().use { output ->
                AnimatedGifEncoder.encode(frames, output)
            }
            tempGif.inputStream().buffered().use { input ->
                imageStore.save(
                    input = input,
                    name = filename,
                    sourceUrl = "https://www.pixiv.net/artworks/$illustId.gif",
                    responseMimeType = "image/gif",
                    clearOld = clearOld,
                )
            }
        } finally {
            tempGif.delete()
        }
    }

    private fun saveUgoiraAsMp4(
        illustId: Long,
        frames: List<UgoiraPlaybackFrame>,
        filename: String,
        cacheDir: File,
        clearOld: Boolean = false,
    ) {
        val tempMp4 = File.createTempFile("ugoira_${illustId}_", ".mp4", cacheDir)
        try {
            UgoiraMp4Encoder.encode(frames, tempMp4)
            tempMp4.inputStream().buffered().use { input ->
                imageStore.save(
                    input = input,
                    name = filename,
                    sourceUrl = "https://www.pixiv.net/artworks/$illustId.mp4",
                    responseMimeType = "video/mp4",
                    clearOld = clearOld,
                )
            }
        } finally {
            tempMp4.delete()
        }
    }

    private fun shouldAutoBookmark(
        illust: Illust?,
        url: String,
    ): Boolean {
        if (!_uiState.value.settings.autoBookmarkOnDownload || illust == null) return false
        return !illust.isBookmarked && (illust.type == "ugoira" || illust.hasImageUrl(url))
    }

    fun clearAppCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appContext = getApplication<Application>().applicationContext
                runCatching {
                    val imageLoader = SingletonImageLoader.get(appContext)
                    imageLoader.diskCache?.clear()
                    imageLoader.memoryCache?.clear()
                }

                val cacheRoot = appContext.cacheDir
                cacheRoot.listFiles()?.forEach { file ->
                    when {
                        file.name == "image_cache" || file.name == "http_cache" -> {
                            file.listFiles()?.forEach { child -> child.deleteRecursively() }
                        }

                        file.name.startsWith("ugoira_") ||
                            file.name.startsWith("temp_") ||
                            file.name.endsWith(".tmp") -> {
                            file.deleteRecursively()
                        }
                    }
                }
                _uiState.update { it.copy(message = str(R.string.msg_cache_deleted), loadState = LoadState.Loaded) }
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
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

    fun openUpdateSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.UpdateSettings)
    }

    fun openPallaSyncSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.PallaSyncSettings)
    }

    fun openDiscordSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.DiscordSettings)
    }

    fun openDiscordLogin() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.DiscordLogin)
    }

    fun openWallpaperPlaylistSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.WallpaperPlaylistSettings)
    }

    fun openPallaSyncDevices() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.PallaSyncDevices)
    }

    fun openViewHistory() {
        loadFullViewHistory()
        _navigationRequests.tryEmit(IllustiaNavigationRequest.ViewHistory)
    }

    fun openNotifications() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.Notifications)
        refreshNotifications()
    }

    fun refreshNotifications() {
        if (_uiState.value.notificationsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(notificationsLoading = true) }
            runCatching { repository.notifications() }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            notifications = page.notifications,
                            notificationNextUrl = page.nextUrl,
                            expandedNotifications = emptyMap(),
                            notificationsLoading = false,
                        )
                    }
                }.onFailure { expectedFailure ->
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    _uiState.update {
                        it.copy(
                            notificationsLoading = false,
                            message =
                                cleanErrorMessage(
                                    error,
                                    getApplication<Application>().getString(R.string.error_notifications_load_failed),
                                ),
                        )
                    }
                }
        }
    }

    fun loadMoreNotifications() {
        val nextUrl = _uiState.value.notificationNextUrl ?: return
        if (_uiState.value.notificationsLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(notificationsLoading = true) }
            runCatching { repository.nextNotificationPage(nextUrl) }
                .onSuccess { page ->
                    _uiState.update { state ->
                        state.copy(
                            notifications = (state.notifications + page.notifications).distinctBy { it.id },
                            notificationNextUrl = page.nextUrl,
                            notificationsLoading = false,
                        )
                    }
                }.onFailure { expectedFailure ->
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    _uiState.update {
                        it.copy(
                            notificationsLoading = false,
                            message =
                                cleanErrorMessage(
                                    error,
                                    getApplication<Application>().getString(R.string.error_notifications_load_failed),
                                ),
                        )
                    }
                }
        }
    }

    fun expandNotification(notificationId: Long) {
        if (_uiState.value.expandedNotifications.containsKey(notificationId)) return
        viewModelScope.launch {
            runCatching { repository.notificationViewMore(notificationId) }
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(expandedNotifications = it.expandedNotifications + (notificationId to page.notifications))
                    }
                }.onFailure { expectedFailure ->
                    val error = expectedFailure
                    if (isCancellation(error)) throw error
                    showMessage(cleanErrorMessage(error, getApplication<Application>().getString(R.string.error_notification_open_failed)))
                }
        }
    }

    fun openNotificationTarget(targetUrl: String?) {
        when (val event = NativeIntentRouter.parseText(targetUrl)) {
            is NativeIntentEvent.Artwork -> openIllust(event.id)
            is NativeIntentEvent.User -> openUserPage(event.id)
            else -> showMessage(str(R.string.notifications_target_unsupported))
        }
    }

    fun openMuteSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.MuteSettings)
    }

    fun openAppData() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AppData)
    }

    fun openAbout() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.About)
    }

    fun openFavoriteTags() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.FavoriteTags)
    }

    fun openAppLockSetup() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AppLockSetup)
    }

    fun openAppLockPinEntry() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.AppLockPinEntry)
    }

    fun openPrivacyModeSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.PrivacyModeSettings)
    }

    fun openExperimentalSettings() {
        _navigationRequests.tryEmit(IllustiaNavigationRequest.ExperimentalSettings)
    }

    fun openAccountSwitcher() {
        _uiState.update { it.copy(showAccountSwitcher = true) }
    }

    fun closeAccountSwitcher() {
        _uiState.update { it.copy(showAccountSwitcher = false) }
    }

    fun switchAccount(index: Int) {
        val current = _uiState.value.settings
        val accounts = current.accounts
        if (index < 0 || index >= accounts.size) return
        val account = accounts[index]
        val nextSettings =
            current.copy(
                refreshToken = account.refreshToken,
                activeAccountIndex = index,
                bookmarkUserId = account.userId,
            )
        _uiState.update {
            it.withSettings(nextSettings).copy(
                currentAccount = nextSettings.resolveLoggedInAccount(),
                sessionReady = false,
                showAccountSwitcher = false,
                loadState = LoadState.Idle,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveSettings(nextSettings, current)
            login()
        }
    }

    fun removeAccount(index: Int) {
        val current = _uiState.value.settings
        val mutable = current.accounts.toMutableList()
        if (index < 0 || index >= mutable.size) return
        val removedAccount = mutable.removeAt(index)
        val removedActiveAccount =
            current.activeAccountIndex == index ||
                current.refreshToken == removedAccount.refreshToken
        val newIndex =
            when {
                removedActiveAccount && mutable.isNotEmpty() -> index.coerceAtMost(mutable.lastIndex)
                removedActiveAccount -> -1
                current.activeAccountIndex > index -> current.activeAccountIndex - 1
                else -> current.activeAccountIndex
            }
        val nextAccount = mutable.getOrNull(newIndex)
        val nextSettings =
            current.copy(
                accounts = mutable,
                activeAccountIndex = newIndex,
                refreshToken = if (removedActiveAccount) nextAccount?.refreshToken.orEmpty() else current.refreshToken,
                bookmarkUserId = if (removedActiveAccount) nextAccount?.userId else current.bookmarkUserId,
            )

        _uiState.update {
            it.withSettings(nextSettings).copy(
                currentAccount = if (removedActiveAccount) nextSettings.resolveLoggedInAccount() else it.currentAccount,
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
                repository.saveSettings(nextSettings, current)
                if (removedActiveAccount) {
                    login()
                }
            }
        }
    }

    override fun saveCurrentAccount() {
        val current = _uiState.value
        val account = current.currentAccount ?: return
        val token = current.settings.refreshToken
        if (token.isBlank()) return
        val stored =
            StoredAccount(
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
}
