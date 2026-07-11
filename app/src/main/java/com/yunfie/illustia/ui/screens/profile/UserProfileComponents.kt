package com.yunfie.illustia.ui.screens.profile

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UserProfilePagerContent(
    user: UserProfile,
    settings: AppSettings,
    illusts: List<Illust>,
    bookmarks: List<Illust>,
    hasMore: Boolean,
    bookmarkHasMore: Boolean,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    onLoadMoreBookmarks: () -> Unit,
    onToggleFollow: () -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    followAnimationTrigger: Int,
    backgroundColor: Color,
    pagerState: PagerState,
    worksGridState: LazyGridState,
    bookmarksGridState: LazyGridState,
    modifier: Modifier = Modifier,
    onTabSelected: (Int) -> Unit,
    showProfileHeader: Boolean,
) {
    var showAvatarPreview by remember(user.id) { mutableStateOf(false) }
    if (showAvatarPreview) {
        Dialog(
            onDismissRequest = { showAvatarPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .miuixClickable(onClick = { showAvatarPreview = false }),
                contentAlignment = Alignment.Center,
            ) {
                AvatarImage(
                    url = user.profileImageUrl,
                    name = user.name,
                    size = 280.dp,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                HeaderOverlayIcon(
                    icon = MiuixIcons.Close,
                    onClick = { showAvatarPreview = false },
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp),
                    contentColor = Color.White,
                )
            }
        }
    }
    Column(modifier = modifier.background(backgroundColor)) {
        AnimatedVisibility(
            visible = showProfileHeader,
            enter = expandVertically(tween(260), expandFrom = Alignment.Top) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(260), shrinkTowards = Alignment.Top) + fadeOut(tween(160)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UserProfileHeader(user)
                UserProfileInfo(
                    user = user,
                    illustCount = illusts.size,
                    selectedTab = pagerState.currentPage,
                    onTabSelected = onTabSelected,
                    onToggleFollow = onToggleFollow,
                    isMuted = isMuted,
                    onUnmuteUser = onUnmuteUser,
                    followAnimationTrigger = followAnimationTrigger,
                    backgroundColor = backgroundColor,
                    showTabs = true,
                    onAvatarClick = { showAvatarPreview = true },
                )
            }
        }
        AnimatedVisibility(
            visible = !showProfileHeader,
            enter = expandVertically(tween(260), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(220), shrinkTowards = Alignment.Top),
        ) {
            Spacer(Modifier.height(64.dp))
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f).background(backgroundColor),
        ) { page ->
            when (page) {
                0 -> UserIllustGridPage(illusts, settings, hasMore, onOpenIllust, onBookmark, onLoadMore, worksGridState, backgroundColor)
                1 -> if (isMuted) {
                    UserInfoPage(backgroundColor) { MutedUserContentNotice(onUnmuteUser) }
                } else {
                    UserIllustGridPage(
                        bookmarks, settings, bookmarkHasMore, onOpenIllust, onBookmark,
                        onLoadMoreBookmarks, bookmarksGridState, backgroundColor,
                        stringResource(R.string.bookmark_empty), "user_bookmark",
                    )
                }
                else -> UserInfoPage(backgroundColor) { UserDetailsCard(user) }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(user: UserProfile) {
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val padding = 14.dp.roundToPx()
                val width = constraints.maxWidth + padding * 2
                val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-padding, 0) }
            }
            .fillMaxWidth()
            .height(180.dp)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        user.backgroundImageUrl?.let {
            PixivImage(
                url = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun UserProfileTopAppBar(
    user: UserProfile,
    onBack: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.detail_share)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val shareTitle = user.name.ifBlank { "@${user.account}" }
    val profileUrl = remember(user.id) { "https://www.pixiv.net/users/${user.id}" }
    Row(
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderOverlayIcon(MiuixIcons.Back, onBack, contentColor = Color.White)
            WindowIconDropdownMenu(
                entry = DropdownEntry(
                    items = listOf(
                        DropdownItem(text = stringResource(R.string.action_sort)),
                        DropdownItem(text = shareLabel, onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "$shareTitle\n$profileUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, shareLabel))
                            }.onFailure { onMessage(shareFailedMessage) }
                        }),
                        DropdownItem(text = stringResource(R.string.dialog_mute), onClick = {
                            onMuteUser()
                            onBack()
                        }),
                    ),
                ),
                backgroundColor = Color.Black.copy(alpha = 0.35f),
                cornerRadius = 19.dp,
                minWidth = 38.dp,
                minHeight = 38.dp,
            ) {
                Icon(MiuixIcons.More, stringResource(R.string.detail_more), Modifier.size(24.dp), tint = Color.White)
            }
    }
}

@Composable
internal fun UserProfileSmallTopAppBar(
    user: UserProfile,
    onBack: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.detail_share)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val shareTitle = user.name.ifBlank { "@${user.account}" }
    val profileUrl = remember(user.id) { "https://www.pixiv.net/users/${user.id}" }
    SmallTopAppBar(
        title = shareTitle,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
            }
        },
        actions = {
            WindowIconDropdownMenu(
                entry = DropdownEntry(
                    items = listOf(
                        DropdownItem(text = stringResource(R.string.action_sort)),
                        DropdownItem(text = shareLabel, onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "$shareTitle\n$profileUrl")
                                }
                                context.startActivity(Intent.createChooser(intent, shareLabel))
                            }.onFailure { onMessage(shareFailedMessage) }
                        }),
                        DropdownItem(text = stringResource(R.string.dialog_mute), onClick = {
                            onMuteUser()
                            onBack()
                        }),
                    ),
                ),
            ) {
                Icon(MiuixIcons.More, contentDescription = stringResource(R.string.detail_more))
            }
        },
    )
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
    followAnimationTrigger: Int,
    backgroundColor: Color,
    showTabs: Boolean,
    onAvatarClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().offset(y = (-40).dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarImage(
                user.profileImageUrl, user.name, 88.dp,
                Modifier
                    .border(androidx.compose.foundation.BorderStroke(3.dp, backgroundColor), CircleShape)
                    .miuixClickable(onClick = onAvatarClick),
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.miuixClickable(pressedScale = 0.94f, haptic = true, onClick = if (isMuted) onUnmuteUser else onToggleFollow)) {
                if (isMuted) MutedUserPill() else FollowPill(user.isFollowed, followAnimationTrigger)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name.ifBlank { "@${user.account}" }, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.data_items_count, illustCount) + " " + stringResource(R.string.user_tab_works), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.body2)
            if (user.comment.isNotBlank()) Text(user.comment, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (showTabs) UserProfileTabs(selectedTab, onTabSelected, Modifier.offset(y = (-6).dp))
    }
}

@Composable
internal fun UserProfileTabs(selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    TabRowWithContour(
        modifier = modifier,
        tabs = listOf(stringResource(R.string.user_tab_works), stringResource(R.string.user_tab_bookmarks), stringResource(R.string.user_tab_info)),
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
    )
}

@Composable
private fun MutedUserPill() {
    Box(Modifier.squircleSurface(MiuixTheme.colorScheme.error, 24.dp).padding(horizontal = 20.dp, vertical = 11.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.detail_unmute), color = Color.White, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun MutedUserContentNotice(onUnmuteUser: () -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(18.dp)) {
        Text(stringResource(R.string.detail_muted_artist), color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Black)
        Text(stringResource(R.string.detail_muted_artist_blur, stringResource(R.string.detail_muted_artist_blur_default)), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2, lineHeight = 20.sp)
        Button(onClick = onUnmuteUser, colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error), modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.detail_unmute), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UserIllustGridPage(
    illusts: List<Illust>, settings: AppSettings, hasMore: Boolean,
    onOpenIllust: (Illust) -> Unit, onBookmark: (Illust) -> Unit, onLoadMore: () -> Unit,
    gridState: LazyGridState, backgroundColor: Color,
    emptyLabel: String = stringResource(R.string.search_empty_illust), keyPrefix: String = "user_illust",
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(illusts, key = { "${keyPrefix}_${it.id}" }, contentType = { "illust_card" }) { illust ->
            IllustCard(
                illust = illust,
                onBookmark = { onBookmark(illust) },
                onClick = { onOpenIllust(illust) },
                modifier = Modifier.animateItem(),
                highQualityImages = settings.highQualityImages && settings.feedPreviewQuality != "low",
                showAiBadge = settings.showAiBadge,
            )
        }
        if (illusts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(emptyLabel) }
        if (hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
            Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(stringResource(R.string.action_load_more)) }
        }
    }
}

@Composable
private fun UserInfoPage(backgroundColor: Color, content: @Composable () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(backgroundColor),
        contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { content() }
    }
}

@Composable
private fun UserDetailsCard(user: UserProfile) {
    ElevatedPanel {
        SettingRow(stringResource(R.string.user_id_label), user.id.toString()) { Text("Pixiv", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
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
