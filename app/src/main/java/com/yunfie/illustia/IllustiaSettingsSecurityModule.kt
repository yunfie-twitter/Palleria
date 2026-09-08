package com.yunfie.illustia

import android.app.Application
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.viewModelScope
import com.yunfie.illustia.DummyAppIconSwitcher
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.SearchWorkType
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.isDynamicColorAvailable
import com.yunfie.illustia.ui.screens.CalculatorEngine
import com.yunfie.illustia.widget.IllustWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings mutations, app/privacy locking, and calculator-backed unlock behavior. */
abstract class IllustiaSettingsSecurityModule(
    app: Application,
    managedDataRepository: ManagedDataRepository,
) : IllustiaViewModelFoundation(app, managedDataRepository) {
    abstract fun refreshHome(forceRefresh: Boolean = false)

    abstract fun submitSearch(
        word: String = _uiState.value.searchDraft,
        forceRefresh: Boolean = false,
    )

    fun loadDeferredStartupData() {
        if (!deferredStartupDataStarted.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            val fullSettings = repository.readSettings(SettingsStore.STARTUP_VIEW_HISTORY_LIMIT)
            val normalizedFullSettings =
                if (fullSettings.useDynamicColor && !isDynamicColorAvailable()) {
                    fullSettings.copy(useDynamicColor = false)
                } else {
                    fullSettings
                }
            if (normalizedFullSettings != fullSettings) {
                repository.saveSettings(normalizedFullSettings, fullSettings)
            }
            _uiState.update { current ->
                val hydratedSettings = current.settings.withHydratedRoomCollections(normalizedFullSettings)
                current.withSettings(hydratedSettings).copy(
                    currentAccount = current.currentAccount ?: hydratedSettings.resolveLoggedInAccount(),
                )
            }

            val activeSettings = _uiState.value.settings
            if (activeSettings.refreshToken.isNotBlank() && !_uiState.value.appLocked && !activeSettings.privacyModeEnabled) {
                launch {
                    refreshCurrentAccountProfile(activeSettings)
                }
            }

            launch {
                SettingsStore.pallaSyncEnabledUpdates
                    .filterNotNull()
                    .collect { update ->
                        if (update.revision > initialPallaSyncStateRevision) {
                            applyPallaSyncEnabledUpdate(update.enabled)
                        }
                    }
            }

            SettingsStore.syncUpdates
                .filterNotNull()
                .collect { update ->
                    if (update.revision > initialSyncRevision) {
                        applySyncedSettings(update.collections)
                    }
                }
        }
    }

    fun loadFullViewHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val fullHistory = repository.readFullViewHistory()
            _uiState.update { current ->
                current.withSettings(current.settings.copy(viewHistory = fullHistory))
            }
        }
    }

    fun loadInitialHomeIfNeeded() {
        val state = _uiState.value
        if (
            state.settings.refreshToken.isBlank() ||
            state.homeItems.isNotEmpty()
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

    fun updateAppFont(value: String) {
        updateSettings { it.copy(appFont = value) }
    }

    fun updateThemeMode(value: String) {
        updateSettings { it.copy(themeMode = value) }
    }

    fun updateUseDynamicColor(value: Boolean) {
        updateSettings {
            it.copy(useDynamicColor = value && isDynamicColorAvailable())
        }
    }

    fun updateSeedColor(value: Long) {
        updateSettings { it.copy(seedColor = value) }
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

    fun updateHapticMode(value: String) {
        updateSettings { it.copy(hapticMode = value) }
    }

    fun updateHapticsEnabled(enabled: Boolean) {
        updateHapticMode(if (enabled) AppHapticMode.Rich.value else AppHapticMode.Off.value)
    }

    fun updatePrefetchImages(value: Boolean) {
        updateSettings { it.copy(prefetchImages = value) }
    }

    fun updateAutoLoadMore(value: Boolean) {
        updateSettings { it.copy(autoLoadMore = value) }
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

    // ─── Privacy Mode 設定値更新 ────────────────────────────────────────────────

    fun updatePrivacyModeAutoLockTiming(value: String) {
        updateSettings { it.copy(privacyModeAutoLockTiming = value) }
    }

    fun updateHideRecents(value: Boolean) {
        updateSettings { it.copy(hideRecents = value) }
    }

    fun updateHideNotifications(value: Boolean) {
        updateSettings { it.copy(hideNotifications = value) }
    }

    fun updatePallaSyncEnabled(value: Boolean) {
        updateSettings { it.copy(pallaSyncEnabled = value) }
        illustiaApplication?.setPallaSyncEnabled(value)
    }

    fun updatePallaSyncServerUrl(value: String) {
        updateSettings { it.copy(pallaSyncServerUrl = value) }
    }

    fun updateSendTelemetry(value: Boolean) {
        updateSettings { it.copy(sendTelemetry = value) }
        illustiaApplication?.setTelemetryEnabled(value)
    }

    fun updateDiscordRpcEnabled(value: Boolean) {
        updateSettings { it.copy(discordRpcEnabled = value) }
    }

    fun updateDiscordToken(value: String) {
        updateSettings { it.copy(discordToken = value) }
    }

    fun updateDiscordApplicationId(value: String) {
        updateSettings { it.copy(discordApplicationId = value) }
    }

    fun updateDiscordRpcShowArtworkDetails(value: Boolean) {
        updateSettings { it.copy(discordRpcShowArtworkDetails = value) }
    }

    fun updateDiscordRpcShowButtons(value: Boolean) {
        updateSettings { it.copy(discordRpcShowButtons = value) }
    }

    fun updateDiscordRpcShowLogs(value: Boolean) {
        updateSettings { it.copy(discordRpcShowLogs = value) }
    }

    fun updateDummyAppName(value: String) {
        if (value.isNotBlank() && value.length <= 30) {
            updateSettings { it.copy(dummyAppName = value) }
        }
    }

    fun updateDummyIconVariant(value: String) {
        updateSettings { it.copy(dummyIconVariant = value) }
    }

    fun verifyCurrentUnlockCode(code: String): Boolean = settingsStore.verifyUnlockCode(code)

    fun applyDummyIconSettings(context: android.content.Context) {
        val settings = _uiState.value.settings
        applyDummyAppIcon(context, settings.privacyModeEnabled)
    }

    fun changeUnlockCode(
        currentCode: String,
        newCode: String,
    ): Boolean {
        if (!settingsStore.isValidUnlockCode(newCode)) return false
        if (!settingsStore.verifyUnlockCode(currentCode)) return false
        settingsStore.saveUnlockCodeHash(newCode)
        return true
    }

    fun applyDummyAppIcon(
        context: android.content.Context,
        enabled: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            DummyAppIconSwitcher.apply(context, enabled)
        }
    }

    fun updateAmoledMode(value: Boolean) {
        updateSettings { it.copy(amoledMode = value) }
    }

    fun updateNavigationOrder(value: List<String>) {
        val allowed = setOf("home", "search", "shorts", "bookmarks", "ranking", "more")
        val normalized = value.filter { it in allowed }.distinct()
        updateSettings { it.copy(navigationOrder = normalized.ifEmpty { it.navigationOrder }) }
    }

    fun updateHiddenNavigationTabs(value: List<String>) {
        val allowed = setOf("home", "search", "shorts", "bookmarks", "ranking", "more")
        updateSettings { it.copy(hiddenNavigationTabs = value.filter { id -> id in allowed }.distinct()) }
    }

    fun updateNavigationStyle(value: String) {
        updateSettings { it.copy(navigationStyle = value.takeIf { style -> style in setOf("standard", "floating", "auto") } ?: "standard") }
    }

    fun updateArtworkThemeEnabled(value: Boolean) {
        updateSettings { it.copy(artworkThemeEnabled = value) }
    }

    fun updateShowCardTitle(value: Boolean) {
        updateSettings { it.copy(showCardTitle = value) }
    }

    fun updateShowCardArtist(value: Boolean) {
        updateSettings { it.copy(showCardArtist = value) }
    }

    fun updateShowCardTags(value: Boolean) {
        updateSettings { it.copy(showCardTags = value) }
    }

    fun updateShowCardBookmarkCount(value: Boolean) {
        updateSettings { it.copy(showCardBookmarkCount = value) }
    }

    fun updateDetailSectionOrder(value: List<String>) {
        val allowed = setOf("artist", "tags", "description", "related")
        val normalized = value.filter { it in allowed }.distinct()
        updateSettings { it.copy(detailSectionOrder = normalized.ifEmpty { it.detailSectionOrder }) }
    }

    fun updateSkipConfirmOnDetailSave(value: Boolean) {
        updateSettings { it.copy(skipConfirmOnDetailSave = value) }
    }

    fun updateUserProfileBottomSheetEnabled(value: Boolean) {
        updateSettings { it.copy(userProfileBottomSheetEnabled = value) }
    }

    fun updateShortsFeedEnabled(value: Boolean) {
        updateSettings { it.copy(shortsFeedEnabled = value) }
    }

    fun updateDisableHorizontalSwipeInShortsFeed(value: Boolean) {
        updateSettings { it.copy(disableHorizontalSwipeInShortsFeed = value) }
    }

    fun updateShowAiBadge(value: Boolean) {
        updateSettings { it.copy(showAiBadge = value) }
    }

    fun userProfileGridState(userId: Long): LazyGridState = userProfileGridStates.getOrPut(userId) { LazyGridState() }

    fun rankingGridState(mode: String): LazyGridState = rankingGridStates.getOrPut(mode) { LazyGridState() }

    fun updateSaveViewHistory(value: Boolean) {
        updateSettings { it.copy(saveViewHistory = value) }
    }

    fun updateSaveSearchHistory(value: Boolean) {
        updateSettings { it.copy(saveSearchHistory = value) }
    }

    fun unlockApp(pin: String): Boolean =
        if (settingsStore.verifyPin(pin)) {
            resumeAfterUnlock()
            true
        } else {
            false
        }

    fun verifyPin(pin: String): Boolean = settingsStore.verifyPin(pin)

    fun confirmUnlock() {
        resumeAfterUnlock()
    }

    fun unlockWithBiometric() {
        resumeAfterUnlock()
    }

    private fun resumeAfterUnlock() {
        _uiState.update { it.copy(appLocked = false) }
        resumePendingNativeIntentIfReady()
        viewModelScope.launch(Dispatchers.IO) {
            val settings = _uiState.value.settings
            if (settings.refreshToken.isNotBlank()) {
                refreshCurrentAccountProfile(settings)
                if (settings.startupScreen == "home" && _uiState.value.homeItems.isEmpty()) {
                    refreshHome()
                }
            }
        }
    }

    fun lockApp() {
        val settings = _uiState.value.settings
        if (settings.appLockEnabled && settingsStore.hasPinSet()) {
            _uiState.update { it.copy(appLocked = true) }
        }
    }

    fun shouldLockOnReturn(): Boolean {
        val settings = _uiState.value.settings
        return settings.appLockEnabled && settings.appLockTiming == "return" && settingsStore.hasPinSet()
    }

    fun setupPin(pin: String) {
        settingsStore.savePinHash(pin)
        updateSettings { it.copy(appLockEnabled = true) }
    }

    fun changePin(newPin: String) {
        settingsStore.savePinHash(newPin)
    }

    fun disableAppLock() {
        settingsStore.clearPinHash()
        updateSettings { it.copy(appLockEnabled = false, biometricEnabled = false) }
    }

    // ─── Privacy Mode 制御 ─────────────────────────────────────────────────────

    /**
     * プライバシーモードを有効化する。
     * 解除コードが未設定なら初期コード "168" を保存する。
     */
    fun enablePrivacyMode() {
        if (!settingsStore.hasUnlockCodeSet()) {
            settingsStore.saveUnlockCodeHash("168")
        }
        updateSettings { it.copy(privacyModeEnabled = true) }
        _uiState.update { it.copy(privacyLocked = true) }
        refreshPrivacySensitiveWidgets()
    }

    /**
     * プライバシーモードを無効化する。ロック状態をリセットし、電卓画面を非表示にする。
     */
    fun disablePrivacyMode() {
        updateSettings { it.copy(privacyModeEnabled = false) }
        _uiState.update { it.copy(privacyLocked = false, calculatorBuffer = "", isTransitioningToIllustia = false) }
        refreshPrivacySensitiveWidgets()
    }

    private fun refreshPrivacySensitiveWidgets() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            IllustWidgetProvider.refreshAll(context)
            refreshRankingWidget()
        }
    }

    /**
     * 解除コードを検証し、成功なら遷移アニメーションを開始する。
     * @return 照合成功なら true
     */
    fun verifyAndUnlockPrivacy(code: String): Boolean =
        if (settingsStore.verifyUnlockCode(code)) {
            _uiState.update { it.copy(isTransitioningToIllustia = true) }
            true
        } else {
            false
        }

    /**
     * 遷移アニメーション完了を通知する（CalculatorScreen から呼ぶ）。
     * privacyLocked を false にして Illustia 本体を表示する。
     */
    fun confirmPrivacyUnlock() {
        privacyUnlockJob?.cancel()
        privacyUnlockJob =
            viewModelScope.launch {
                _uiState.update { it.copy(privacyLocked = false, isTransitioningToIllustia = false, calculatorBuffer = "") }
                resumePendingNativeIntentIfReady()
                val settings = _uiState.value.settings
                if (settings.refreshToken.isNotBlank() && _uiState.value.homeItems.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        refreshCurrentAccountProfile(settings)
                        if (settings.startupScreen == "home") {
                            refreshHome()
                        }
                    }
                }
            }
    }

    /**
     * 即時ロックを実行する（画面 OFF・端末ロック・バックグラウンド移行時）。
     */
    fun lockPrivacyMode() {
        if (_uiState.value.settings.privacyModeEnabled) {
            privacyUnlockJob?.cancel()
            _uiState.update { it.copy(privacyLocked = true, calculatorBuffer = "", isTransitioningToIllustia = false) }
        }
    }

    // ─── 電卓バッファ操作 ───────────────────────────────────────────────────────

    fun appendToCalculatorBuffer(char: Char) {
        _uiState.update { state ->
            if (state.calculatorBuffer.length < 50) {
                state.copy(calculatorBuffer = state.calculatorBuffer + char)
            } else {
                state
            }
        }
    }

    fun clearCalculatorBuffer() {
        _uiState.update { it.copy(calculatorBuffer = "") }
    }

    fun deleteLastCalculatorBuffer() {
        _uiState.update { state ->
            if (state.calculatorBuffer.isNotEmpty()) {
                state.copy(calculatorBuffer = state.calculatorBuffer.dropLast(1))
            } else {
                state
            }
        }
    }

    fun evaluateCalculatorExpression() {
        val buffer = _uiState.value.calculatorBuffer
        if (buffer.isBlank()) return

        // パターンB: 解除コード照合
        if (verifyAndUnlockPrivacy(buffer)) {
            // 解除成功: 履歴に記録しない、バッファは confirmPrivacyUnlock でクリア
            return
        }

        // 通常の計算
        val result = CalculatorEngine.evaluate(buffer)
        val resultStr = if (result != null) CalculatorEngine.formatResult(result) else null

        _uiState.update { state ->
            val newHistory =
                if (resultStr != null) {
                    val entry = CalculatorHistoryEntry(expression = buffer, result = resultStr)
                    (listOf(entry) + state.calculatorHistory).take(20)
                } else {
                    state.calculatorHistory
                }
            state.copy(
                calculatorBuffer = resultStr ?: "エラー",
                calculatorHistory = newHistory,
            )
        }
    }

    // ─── AutoLock タイマー ──────────────────────────────────────────────────────

    fun startAutoLockTimer() {
        if (!_uiState.value.settings.privacyModeEnabled) return
        if (_uiState.value.privacyLocked) return
        val delayMs: Long =
            when (_uiState.value.settings.privacyModeAutoLockTiming) {
                "immediate" -> 0L
                "30s" -> 30_000L
                "1m" -> 60_000L
                "5m" -> 5 * 60_000L
                "10m" -> 10 * 60_000L
                "disabled" -> return
                else -> 0L
            }
        autoLockJob?.cancel()
        autoLockJob =
            viewModelScope.launch {
                if (delayMs > 0L) delay(delayMs)
                lockPrivacyMode()
            }
    }

    fun cancelAutoLockTimer() {
        autoLockJob?.cancel()
        autoLockJob = null
    }

    fun updateBiometricEnabled(value: Boolean) {
        updateSettings { it.copy(biometricEnabled = value) }
    }

    fun updateAppLockTiming(value: String) {
        updateSettings { it.copy(appLockTiming = value) }
    }

    // ── Lock failure tracking & progressive lockout ──────────────────────────
    // Cooldown durations (seconds) keyed by fail-count thresholds.
    private val cooldownTable =
        listOf(
            9 to 5 * 60L, // 5 min
            6 to 2 * 60L, // 2 min
            3 to 30L, // 30 sec
        )

    fun recordLockFailure() {
        val count = _uiState.value.settings.appLockFailCount + 1
        val cooldownSec = cooldownTable.firstOrNull { count >= it.first }?.second ?: 0L
        val cooldownUntil =
            if (cooldownSec > 0L) {
                android.os.SystemClock.elapsedRealtime() + cooldownSec * 1000L
            } else {
                0L
            }
        updateSettings {
            it.copy(appLockFailCount = count, appLockCooldownUntil = cooldownUntil)
        }
        _uiState.update { it.copy(showLockRecoveryDialog = count >= 12) }
    }

    fun resetLockFailCount() {
        updateSettings { it.copy(appLockFailCount = 0, appLockCooldownUntil = 0L) }
    }

    fun dismissLockRecovery() {
        _uiState.update { it.copy(showLockRecoveryDialog = false) }
    }

    fun openRecoveryWebLogin() {
        appLockRecoveryLogin = true
        _uiState.update {
            it.copy(
                showLockRecoveryDialog = false,
                webLoginRequest = createPixivWebLoginRequest(),
            )
        }
    }

    fun cooldownRemainingSeconds(): Long {
        val until = _uiState.value.settings.appLockCooldownUntil
        if (until == 0L) return 0L
        return ((until - android.os.SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
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

    fun updateDownloadFolderByArtist(value: Boolean) {
        updateSettings { it.copy(downloadFolderByArtist = value) }
    }

    fun updateDownloadFolderByWork(value: Boolean) {
        updateSettings { it.copy(downloadFolderByWork = value) }
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

    fun updateMangaReaderMode(value: String) {
        updateSettings { it.copy(mangaReaderMode = value) }
    }

    fun updateSmartCacheEnabled(value: Boolean) {
        updateSettings { it.copy(smartCacheEnabled = value) }
    }

    fun updateSmartCacheWifiOnly(value: Boolean) {
        updateSettings { it.copy(smartCacheWifiOnly = value) }
    }

    fun updateSmartCacheItemCount(value: Int) {
        updateSettings { it.copy(smartCacheItemCount = value.coerceIn(4, 30)) }
    }

    fun updateImageCacheSizeMb(value: Int) {
        updateSettings { it.copy(imageCacheSizeMb = value.coerceIn(100, 1000)) }
    }

    fun updateWallpaperPlaylistEnabled(value: Boolean) {
        updateSettings { it.copy(wallpaperPlaylistEnabled = value) }
        com.yunfie.illustia.wallpaper.WallpaperPlaylistScheduler
            .setEnabled(getApplication(), value)
    }

    fun updateLiveWallpaperSource(value: String) {
        updateSettings { it.copy(liveWallpaperSource = value) }
    }

    fun updateLiveWallpaperSourceFolder(value: String) {
        updateSettings { it.copy(liveWallpaperSourceFolder = value.trim().take(2_048)) }
    }

    fun updateLiveWallpaperChangeMode(value: String) {
        updateSettings { it.copy(liveWallpaperChangeMode = value) }
    }

    fun updateLiveWallpaperIntervalMinutes(value: Int) {
        updateSettings { it.copy(liveWallpaperIntervalMinutes = value.coerceIn(15, 1440)) }
    }

    fun updateLiveWallpaperOrder(value: String) {
        updateSettings { it.copy(liveWallpaperOrder = value) }
    }

    fun updateLiveWallpaperScaleMode(value: String) {
        updateSettings { it.copy(liveWallpaperScaleMode = value) }
    }

    fun updateLiveWallpaperBackground(value: String) {
        updateSettings { it.copy(liveWallpaperBackground = value) }
    }

    fun updateLiveWallpaperCrossfade(value: Boolean) {
        updateSettings { it.copy(liveWallpaperCrossfade = value) }
    }

    fun updateLiveWallpaperExcludeSensitive(value: Boolean) {
        updateSettings { it.copy(liveWallpaperExcludeSensitive = value) }
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

    fun updateRelatedIllustColumnCount(value: Int) {
        val count = if (value == 0) 0 else value.coerceIn(2, 5)
        updateSettings { it.copy(relatedIllustColumnCount = count) }
    }

    fun updatePixivImageProxyBaseUrl(value: String) {
        updateSettings { it.copy(pixivImageProxyBaseUrl = value) }
    }

    fun updatePixivNetworkMode(value: String) {
        updateSettings { it.copy(pixivNetworkMode = value) }
    }

    fun updateRestrict(value: Restrict) {
        updateSettings { it.copy(bookmarkRestrict = value) }
    }

    fun updateBookmarkSelectedTab(index: Int) {
        _uiState.update { it.copy(bookmarkSelectedTab = index) }
    }

    fun updateSearchSort(value: SearchSort) {
        updateSettings { it.copy(searchSort = value) }
        refreshActiveSearch()
    }

    fun updateSearchTarget(value: SearchTarget) {
        updateSettings { it.copy(searchTarget = value) }
        refreshActiveSearch()
    }

    fun updateSearchWorkType(value: SearchWorkType) {
        updateSettings { it.copy(searchWorkType = value) }
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
}
