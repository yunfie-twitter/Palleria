package com.yunfie.illustia.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
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
    onMessage: (String) -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    gridState: LazyGridState,
    showHeaderControls: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    contentHeight: Dp? = null,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var showUnfollowConfirm by remember(user.id) { mutableStateOf(false) }
    var followAnimationTrigger by remember(user.id) { mutableIntStateOf(0) }
    val scrollBehavior = if (showHeaderControls) MiuixScrollBehavior() else null
    val bookmarkGridState = remember(user.id) { LazyGridState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage

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
    val contentModifier = modifier
        .then(if (contentHeight != null) Modifier.height(contentHeight) else Modifier.fillMaxSize())
        .background(backgroundColor)

    if (showHeaderControls && scrollBehavior != null) {
        Scaffold(
            containerColor = backgroundColor,
            topBar = {
                Column(
                    modifier = Modifier.background(backgroundColor),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UserProfileTopAppBar(
                        user = user,
                        onBack = onBack,
                        onMuteUser = onMuteUser,
                        onMessage = onMessage,
                        scrollBehavior = scrollBehavior,
                    )
                    UserProfileTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { index ->
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                }
            },
        ) { scaffoldPadding ->
            UserProfilePagerContent(
                user = user,
                settings = settings,
                illusts = illusts,
                bookmarks = bookmarks,
                hasMore = hasMore,
                bookmarkHasMore = bookmarkHasMore,
                onOpenIllust = onOpenIllust,
                onBookmark = onBookmark,
                onLoadMore = onLoadMore,
                onLoadMoreBookmarks = onLoadMoreBookmarks,
                onToggleFollow = {
                    if (user.isFollowed) {
                        showUnfollowConfirm = true
                    } else {
                        followAnimationTrigger += 1
                        onToggleFollow()
                    }
                },
                isMuted = isMuted,
                onUnmuteUser = onUnmuteUser,
                followAnimationTrigger = followAnimationTrigger,
                backgroundColor = backgroundColor,
                pagerState = pagerState,
                worksGridState = gridState,
                bookmarksGridState = bookmarkGridState,
                modifier = contentModifier.padding(top = scaffoldPadding.calculateTopPadding()),
                showTabsInHeader = false,
            )
        }
    } else {
        UserProfilePagerContent(
            user = user,
            settings = settings,
            illusts = illusts,
            bookmarks = bookmarks,
            hasMore = hasMore,
            bookmarkHasMore = bookmarkHasMore,
            onOpenIllust = onOpenIllust,
            onBookmark = onBookmark,
            onLoadMore = onLoadMore,
            onLoadMoreBookmarks = onLoadMoreBookmarks,
            onToggleFollow = {
                if (user.isFollowed) {
                    showUnfollowConfirm = true
                } else {
                    followAnimationTrigger += 1
                    onToggleFollow()
                }
            },
            isMuted = isMuted,
            onUnmuteUser = onUnmuteUser,
            followAnimationTrigger = followAnimationTrigger,
            backgroundColor = backgroundColor,
            pagerState = pagerState,
            worksGridState = gridState,
            bookmarksGridState = bookmarkGridState,
            modifier = contentModifier,
            showTabsInHeader = true,
            onTabSelected = { index ->
                coroutineScope.launch { pagerState.animateScrollToPage(index) }
            },
        )
    }
}

@Composable
private fun UserProfilePagerContent(
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
    pagerState: androidx.compose.foundation.pager.PagerState,
    worksGridState: LazyGridState,
    bookmarksGridState: LazyGridState,
    modifier: Modifier = Modifier,
    showTabsInHeader: Boolean,
    onTabSelected: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { UserProfileHeader(user = user) }
            item {
                UserProfileInfo(
                    user = user,
                    illustCount = illusts.size,
                    selectedTab = pagerState.currentPage,
                    onTabSelected = { index -> onTabSelected?.invoke(index) },
                    onToggleFollow = onToggleFollow,
                    isMuted = isMuted,
                    onUnmuteUser = onUnmuteUser,
                    backgroundColor = backgroundColor,
                    followAnimationTrigger = followAnimationTrigger,
                    showTabs = showTabsInHeader,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(backgroundColor),
        ) { page ->
            when (page) {
                0 -> UserIllustGridPage(
                    illusts = illusts,
                    settings = settings,
                    hasMore = hasMore,
                    onOpenIllust = onOpenIllust,
                    onBookmark = onBookmark,
                    onLoadMore = onLoadMore,
                    gridState = worksGridState,
                    backgroundColor = backgroundColor,
                )
                1 -> {
                    if (isMuted) {
                        UserInfoPage(
                            backgroundColor = backgroundColor,
                            contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp),
                        ) {
                            MutedUserContentNotice(onUnmuteUser = onUnmuteUser)
                        }
                    } else {
                        UserIllustGridPage(
                            illusts = bookmarks,
                            settings = settings,
                            hasMore = bookmarkHasMore,
                            onOpenIllust = onOpenIllust,
                            onBookmark = onBookmark,
                            onLoadMore = onLoadMoreBookmarks,
                            gridState = bookmarksGridState,
                            backgroundColor = backgroundColor,
                            emptyLabel = stringResource(R.string.bookmark_empty),
                            keyPrefix = "user_bookmark",
                        )
                    }
                }
                else -> UserInfoPage(
                    backgroundColor = backgroundColor,
                    contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp),
                ) {
                    UserDetailsCard(user = user)
                }
            }
        }
    }
}
@Composable
private fun UserProfileHeader(
    user: UserProfile,
) {
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val padding = 14.dp.roundToPx()
                val expandedWidth = constraints.maxWidth + padding * 2
                val placeable = measurable.measure(
                    constraints.copy(
                        maxWidth = expandedWidth,
                        minWidth = expandedWidth
                    )
                )
                layout(constraints.maxWidth, placeable.height) {
                    placeable.placeRelative(-padding, 0)
                }
            }
            .fillMaxWidth()
            .height(180.dp)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (user.backgroundImageUrl != null) {
            PixivImage(
                url = user.backgroundImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun UserProfileTopAppBar(
    user: UserProfile,
    onBack: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.detail_share)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val shareTitle = user.name.ifBlank { "@${user.account}" }
    val profileUrl = remember(user.id) { "https://www.pixiv.net/users/${user.id}" }

    TopAppBar(
        title = "",
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            HeaderIcon(MiuixIcons.Back, onClick = onBack)
        },
        actions = {
            WindowIconDropdownMenu(
                entry = DropdownEntry(
                    items = listOf(
                        DropdownItem(text = stringResource(R.string.action_sort)),
                        DropdownItem(
                            text = shareLabel,
                            onClick = {
                                runCatching {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "$shareTitle\n$profileUrl")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, shareLabel))
                                }.onFailure { onMessage(shareFailedMessage) }
                            },
                        ),
                        DropdownItem(
                            text = stringResource(R.string.dialog_mute),
                            onClick = {
                                onMuteUser()
                                onBack()
                            },
                        ),
                    ),
                ),
            ) {
                Icon(
                    MiuixIcons.More,
                    contentDescription = stringResource(R.string.detail_more),
                    modifier = Modifier.size(24.dp),
                )
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
    showTabs: Boolean = true,
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
                    haptic = true,
                    onClick = if (isMuted) onUnmuteUser else onToggleFollow,
                ),
            ) {
                if (isMuted) {
                    MutedUserPill()
                } else {
                    FollowPill(user.isFollowed, followAnimationTrigger)
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
        if (showTabs) {
            UserProfileTabs(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
            )
        }
    }
}

@Composable
private fun UserProfileTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    TabRow(
        tabs = listOf(
            stringResource(R.string.user_tab_works),
            stringResource(R.string.user_tab_bookmarks),
            stringResource(R.string.user_tab_info),
        ),
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

@Composable
private fun UserIllustGridPage(
    illusts: List<Illust>,
    settings: AppSettings,
    hasMore: Boolean,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    gridState: LazyGridState,
    backgroundColor: Color,
    emptyLabel: String = stringResource(R.string.search_empty_illust),
    keyPrefix: String = "user_illust",
) {
    val feedHighQuality = settings.highQualityImages && settings.feedPreviewQuality != "low"
    val showAiBadge = settings.showAiBadge
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(illusts, key = { "${keyPrefix}_${it.id}" }, contentType = { "illust_card" }) { illust ->
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
                EmptyState(emptyLabel)
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
}

@Composable
private fun UserInfoPage(
    backgroundColor: Color,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentPadding = contentPadding,
    ) {
        item {
            content()
        }
    }
}

@Composable
private fun UserDetailsCard(user: UserProfile) {
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
