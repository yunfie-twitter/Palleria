package com.yunfie.illustia
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.PixivNotification
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.db.SavedIllustEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Single owner for application UI state.
 *
 * The public state remains the flat [IllustiaUiState] for source compatibility. Controllers use
 * feature snapshots to keep their read/write boundary explicit while migration is incremental.
 */
internal class AppStateStore(
    initialState: IllustiaUiState = IllustiaUiState(),
) {
    private val mutableState = MutableStateFlow(initialState)

    val state: StateFlow<IllustiaUiState> = mutableState.asStateFlow()
    val value: IllustiaUiState
        get() = mutableState.value

    fun update(reducer: (IllustiaUiState) -> IllustiaUiState) = mutableState.update(reducer)

    fun <T> map(transform: suspend (IllustiaUiState) -> T): Flow<T> = state.map(transform)

    fun slices(): AppStateSlices = mutableState.value.toSlices()
}

internal data class AppStateSlices(
    val session: SessionState,
    val feedSearch: FeedSearchState,
    val detailProfile: DetailProfileState,
    val bookmarkLibrary: BookmarkLibraryState,
    val settingsSecurity: SettingsSecurityState,
)

internal data class SessionState(
    val ready: Boolean,
    val currentAccount: UserProfile?,
)

internal data class FeedSearchState(
    val homeItems: List<Illust>,
    val searchDraft: String,
    val activeSearchWord: String,
    val searchItems: List<Illust>,
    val searchNovels: List<NovelPreview>,
    val searchUsers: List<UserPreview>,
)

internal data class DetailProfileState(
    val selectedIllust: Illust?,
    val selectedUser: UserProfile?,
    val selectedUserIllusts: List<Illust>,
)

internal data class BookmarkLibraryState(
    val bookmarks: List<Illust>,
    val savedIllusts: List<SavedIllustEntity>,
    val notifications: List<PixivNotification>,
)

internal data class SettingsSecurityState(
    val settings: AppSettings,
    val settingsLoaded: Boolean,
    val appLocked: Boolean,
    val privacyLocked: Boolean,
)

private fun IllustiaUiState.toSlices(): AppStateSlices =
    AppStateSlices(
        session = SessionState(sessionReady, currentAccount),
        feedSearch =
            FeedSearchState(
                homeItems,
                searchDraft,
                activeSearchWord,
                searchItems,
                searchNovelItems,
                userSearchItems,
            ),
        detailProfile = DetailProfileState(selectedIllust, selectedUser, selectedUserIllusts),
        bookmarkLibrary =
            BookmarkLibraryState(
                bookmarkItems,
                savedIllusts,
                notifications,
            ),
        settingsSecurity = SettingsSecurityState(settings, settingsLoaded, appLocked, privacyLocked),
    )
