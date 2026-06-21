package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface

@Composable
fun UserProfileScreen(
    user: UserProfile,
    settings: AppSettings,
    illusts: List<Illust>,
    bookmarks: List<Illust>,
    hasMore: Boolean,
    bookmarkHasMore: Boolean,
    onBack: () -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    onLoadBookmarks: () -> Unit,
    onLoadMoreBookmarks: () -> Unit,
    onToggleFollow: () -> Unit,
    onMuteUser: () -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    showHeaderControls: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    contentHeight: Dp? = null,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var showUnfollowConfirm by remember(user.id) { mutableStateOf(false) }
    var selectedTab by remember(user.id) { mutableStateOf(0) }

    LaunchedEffect(selectedTab, user.id) {
        if (!isMuted && selectedTab == 1) onLoadBookmarks()
    }

    if (showUnfollowConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.detail_unfollow_title),
            summary = stringResource(R.string.detail_unfollow_confirm, user.name.ifBlank { "@${user.account}" }),
            confirmText = stringResource(R.string.action_unfollow),
            destructive = true,
            onConfirm = {
                showUnfollowConfirm = false
                onToggleFollow()
            },
            onDismiss = { showUnfollowConfirm = false },
        )
    }

    LazyVerticalGrid(
        state = rememberLazyGridState(),
        columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
        modifier = modifier
            .then(if (contentHeight != null) Modifier.height(contentHeight) else Modifier.fillMaxSize())
            .background(backgroundColor),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            UserProfileHeader(
                user = user,
                showHeaderControls = showHeaderControls,
                onBack = onBack,
                onMuteUser = onMuteUser
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            UserProfileInfo(
                user = user,
                illustCount = illusts.size,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onToggleFollow = { if (user.isFollowed) showUnfollowConfirm = true else onToggleFollow() },
                isMuted = isMuted,
                onUnmuteUser = onUnmuteUser,
                backgroundColor = backgroundColor
            )
        }
        
        if (isMuted && selectedTab != 2) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MutedUserContentNotice(onUnmuteUser = onUnmuteUser)
            }
        } else {
            when (selectedTab) {
                0 -> UserIllustsGrid(
                    illusts = illusts,
                    settings = settings,
                    hasMore = hasMore,
                    onOpenIllust = onOpenIllust,
                    onBookmark = onBookmark,
                    onLoadMore = onLoadMore
                )
                1 -> UserBookmarksGrid(
                    bookmarks = bookmarks,
                    settings = settings,
                    hasMore = bookmarkHasMore,
                    onOpenIllust = onOpenIllust,
                    onBookmark = onBookmark,
                    onLoadMore = onLoadMoreBookmarks
                )
                else -> UserDetailsSection(user = user)
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: UserProfile,
    showHeaderControls: Boolean,
    onBack: () -> Unit,
    onMuteUser: () -> Unit
) {
    val bannerWidth = LocalConfiguration.current.screenWidthDp.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredWidth(bannerWidth)
            .offset(x = (-14).dp)
            .height(180.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (user.backgroundImageUrl != null) {
            PixivImage(
                url = user.backgroundImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        
        if (showHeaderControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderOverlayIcon(icon = MiuixIcons.Back, onClick = onBack)
                WindowIconDropdownMenu(
                    entry = DropdownEntry(
                        items = listOf(
                            DropdownItem(text = stringResource(R.string.action_sort)),
                            DropdownItem(
                                text = stringResource(R.string.dialog_mute),
                                onClick = {
                                    onMuteUser()
                                    onBack()
                                },
                            ),
                        ),
                    ),
                    backgroundColor = Color.Black.copy(alpha = 0.35f),
                    cornerRadius = 19.dp,
                    minWidth = 38.dp,
                    minHeight = 38.dp,
                ) {
                    Icon(MiuixIcons.More, contentDescription = stringResource(R.string.detail_more), tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun UserProfileInfo(
    user: UserProfile,
    illustCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    backgroundColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-40).dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarImage(
                url = user.profileImageUrl,
                name = user.name,
                size = 88.dp,
                modifier = Modifier.border(androidx.compose.foundation.BorderStroke(3.dp, backgroundColor), CircleShape),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier.miuixClickable(
                    pressedScale = 0.94f,
                    onClick = if (isMuted) onUnmuteUser else onToggleFollow,
                ),
            ) {
                if (isMuted) {
                    MutedUserPill()
                } else {
                    FollowPill(user.isFollowed)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name.ifBlank { "@${user.account}" }, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.data_items_count, illustCount) + " " + stringResource(R.string.user_tab_works), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.body2)
            if (user.comment.isNotBlank()) {
                Text(
                    text = user.comment, 
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, 
                    style = MiuixTheme.textStyles.body2, 
                    lineHeight = 20.sp, 
                    maxLines = 3, 
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        TabRowWithContour(
            tabs = listOf(stringResource(R.string.user_tab_works), stringResource(R.string.user_tab_bookmarks), stringResource(R.string.user_tab_info)),
            selectedTabIndex = selectedTab,
            onTabSelected = onTabSelected,
            colors = TabRowDefaults.tabRowColors(
                backgroundColor = Color.Transparent,
                selectedBackgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedContentColor = MiuixTheme.colorScheme.onBackground,
            ),
            minWidth = 100.dp,
            maxWidth = 160.dp,
        )
    }
}

@Composable
private fun MutedUserPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .squircleSurface(MiuixTheme.colorScheme.error, 24.dp)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_unmute),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun MutedUserContentNotice(onUnmuteUser: () -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(18.dp)) {
        Text(
            text = stringResource(R.string.detail_muted_artist),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = stringResource(R.string.detail_muted_artist_blur, stringResource(R.string.detail_muted_artist_blur_default)),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            lineHeight = 20.sp,
        )
        Button(
            onClick = onUnmuteUser,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
            ),
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(stringResource(R.string.detail_unmute), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.UserIllustsGrid(
    illusts: List<Illust>,
    settings: AppSettings,
    hasMore: Boolean,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit
) {
    val feedHighQuality = settings.highQualityImages && settings.feedPreviewQuality != "low"
    val showAiBadge = settings.showAiBadge

    gridItems(illusts, key = { "user_illust_${it.id}" }, contentType = { "illust_card" }) { illust ->
        IllustCard(
            illust = illust,
            onBookmark = { onBookmark(illust) },
            onClick = { onOpenIllust(illust) },
            modifier = Modifier.animateItem(),
            highQualityImages = feedHighQuality,
            showAiBadge = showAiBadge,
        )
    }
    if (illusts.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(stringResource(R.string.search_empty_illust))
        }
    }
    if (hasMore) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.action_load_more))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.UserBookmarksGrid(
    bookmarks: List<Illust>,
    settings: AppSettings,
    hasMore: Boolean,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit
) {
    val feedHighQuality = settings.highQualityImages && settings.feedPreviewQuality != "low"
    val showAiBadge = settings.showAiBadge

    gridItems(bookmarks, key = { "user_bookmark_${it.id}" }, contentType = { "illust_card" }) { illust ->
        IllustCard(
            illust = illust,
            onBookmark = { onBookmark(illust) },
            onClick = { onOpenIllust(illust) },
            modifier = Modifier.animateItem(),
            highQualityImages = feedHighQuality,
            showAiBadge = showAiBadge,
        )
    }
    if (bookmarks.isEmpty()) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(stringResource(R.string.bookmark_empty))
        }
    }
    if (hasMore) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.action_load_more))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.UserDetailsSection(user: UserProfile) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        ElevatedPanel {
            SettingRow(stringResource(R.string.user_id_label), user.id.toString()) {
                Text("Pixiv", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            DividerLine()
            SettingRow(stringResource(R.string.settings_account), "@${user.account}") {
                Text(if (user.isFollowed) stringResource(R.string.action_following) else stringResource(R.string.action_not_followed), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
            }
            if (user.comment.isNotBlank()) {
                DividerLine()
                Text(user.comment, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.body1, lineHeight = 23.sp)
            }
        }
    }
}
