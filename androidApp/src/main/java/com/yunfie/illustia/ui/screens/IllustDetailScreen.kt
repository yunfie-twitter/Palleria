@file:JvmName("AndroidIllustDetailScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.screens.IllustDetailScreen as SharedIllustDetailScreen

@Composable
fun IllustDetailScreen(
    illust: Illust,
    isActive: Boolean = true,
    relatedIllusts: List<Illust>,
    relatedIllustsLoaded: Boolean = false,
    firstComment: Comment?,
    settings: IllustDetailSettings,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onBookmarkLongClick: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
    isRefreshingExternal: Boolean? = null,
    onOpenUser: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onOpenImage: (Int) -> Unit,
    onSearchTag: (String) -> Unit,
    onLongPressTag: (String) -> Unit,
    isArtistFollowed: Boolean,
    isArtistFollowKnown: Boolean = true,
    isArtistMuted: Boolean,
    isTagMuted: Boolean,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onReport: (String?, (Boolean) -> Unit) -> Unit,
    onMuteTag: (String) -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onSaveImage: (String, String) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMessage: (String) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    onRelatedSectionReady: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    listState: LazyListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(),
) {
    SharedIllustDetailScreen(
        illust = illust,
        isActive = isActive,
        relatedIllusts = relatedIllusts,
        relatedIllustsLoaded = relatedIllustsLoaded,
        firstComment = firstComment,
        settings = settings,
        onBack = onBack,
        onBookmark = onBookmark,
        onBookmarkLongClick = onBookmarkLongClick,
        onRefresh = onRefresh,
        isRefreshingExternal = isRefreshingExternal,
        onOpenUser = onOpenUser,
        onOpenComments = onOpenComments,
        onOpenSeries = onOpenSeries,
        onOpenImage = onOpenImage,
        onSearchTag = onSearchTag,
        onLongPressTag = onLongPressTag,
        isArtistFollowed = isArtistFollowed,
        isArtistFollowKnown = isArtistFollowKnown,
        isArtistMuted = isArtistMuted,
        isTagMuted = isTagMuted,
        onToggleFollow = onToggleFollow,
        onUnmuteUser = onUnmuteUser,
        onMuteIllust = onMuteIllust,
        onMuteUser = onMuteUser,
        onReport = onReport,
        onMuteTag = onMuteTag,
        onOpenIllust = onOpenIllust,
        onLongPressIllust = onLongPressIllust,
        onOpenIllustById = onOpenIllustById,
        onSaveImage = onSaveImage,
        onSaveAllImages = onSaveAllImages,
        onMessage = onMessage,
        loadUgoiraPlayback = loadUgoiraPlayback,
        onRelatedSectionReady = onRelatedSectionReady,
        onPageChanged = onPageChanged,
        listState = listState,
        platform = androidUserProfilePlatform(),
    )
}
