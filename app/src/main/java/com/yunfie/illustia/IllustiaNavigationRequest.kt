package com.yunfie.illustia

import androidx.compose.runtime.Immutable

@Immutable
enum class IllustiaNavigationRequest {
    Settings,
    GeneralSettings,
    ImageSettings,
    BookmarkSettings,
    AccountSettings,
    AccountLoginMethod,
    DataSettings,
    ViewHistory,
    MuteSettings,
    AppData,
    DownloadQueue,
    OfflineLibrary,
    SavedIllustViewer,
    About,
    FavoriteTags,
    AppLockSetup,
    AppLockPinEntry,
    PrivacyModeSettings,
}
