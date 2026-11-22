package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings

/** Only values consumed by the Activity window and theme, excluding feed and collection state. */
internal data class ActivityPresentationState(
    val settings: AppSettings,
    val settingsLoaded: Boolean,
    val appLocked: Boolean,
    val selectedIllust: Illust?,
    val selectedUserName: String?,
    val activeSearchWord: String,
    val showUserPage: Boolean,
)

internal fun IllustiaUiState.activityPresentation() =
    ActivityPresentationState(
        settings =
            AppSettings(
                themeMode = settings.themeMode,
                useDynamicColor = settings.useDynamicColor,
                artworkThemeEnabled = settings.artworkThemeEnabled,
                pixivImageProxyBaseUrl = settings.pixivImageProxyBaseUrl,
                secureWindow = settings.secureWindow,
                appLockEnabled = settings.appLockEnabled,
                appLanguage = settings.appLanguage,
                notchOptimization = settings.notchOptimization,
                privacyModeEnabled = settings.privacyModeEnabled,
                hideRecents = settings.hideRecents,
                dummyAppName = settings.dummyAppName,
                dummyIconVariant = settings.dummyIconVariant,
                appIconVariant = settings.appIconVariant,
                appFont = settings.appFont,
            ),
        settingsLoaded = settingsLoaded,
        appLocked = appLocked,
        selectedIllust = selectedIllust,
        selectedUserName = selectedUser?.name,
        activeSearchWord = activeSearchWord,
        showUserPage = showUserPage,
    )
