package com.yunfie.illustia.ui.screens.profile

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.ProfileGridHorizontalSpacing
import com.yunfie.illustia.ui.components.ProfileGridVerticalSpacing
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.profileGridContentPadding
import com.yunfie.illustia.ui.screens.UserResultCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.items as lazyListItems

enum class UserWorkSortOrder {
    Newest,
    Oldest,
    MostBookmarks,
}

enum class UserWorkTypeFilter {
    All,
    IllustOnly,
    MangaOnly,
}

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
    onIllustLongClick: ((Illust) -> Unit)? = null,
) {
    var showAvatarPreview by remember(user.id) { mutableStateOf(false) }
    val tabListState = rememberLazyListState()
    if (showAvatarPreview) {
        Dialog(
            onDismissRequest = { showAvatarPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier =
                    Modifier
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
            enter =
                expandVertically(
                    animationSpec = tween(320),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(220, delayMillis = 60)),
            exit =
                shrinkVertically(
                    animationSpec = tween(280),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(180)),
        ) {
            UserProfileHeader(
                user = user,
                selectedTab = pagerState.currentPage,
                onTabSelected = onTabSelected,
                onToggleFollow = onToggleFollow,
                isMuted = isMuted,
                onUnmuteUser = onUnmuteUser,
                followAnimationTrigger = followAnimationTrigger,
                backgroundColor = backgroundColor,
                onAvatarClick = { showAvatarPreview = true },
                tabListState = tabListState,
            )
        }
        AnimatedVisibility(
            visible = !showProfileHeader,
            enter =
                expandVertically(
                    animationSpec = tween(280),
                    expandFrom = Alignment.Top,
                ) + fadeIn(animationSpec = tween(200, delayMillis = 80)),
            exit =
                shrinkVertically(
                    animationSpec = tween(220),
                    shrinkTowards = Alignment.Top,
                ) + fadeOut(animationSpec = tween(140)),
        ) {
            Column {
                Spacer(
                    Modifier
                        .statusBarsPadding()
                        .height(54.dp),
                )
                UserProfileTabs(
                    selectedTab = pagerState.currentPage,
                    onTabSelected = onTabSelected,
                    modifier = Modifier.padding(horizontal = 14.dp),
                    listState = tabListState,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f).background(backgroundColor),
        ) { page ->
            when (page) {
                0 -> {
                    UserIllustGridPage(
                        illusts = illusts,
                        settings = settings,
                        hasMore = hasMore,
                        onOpenIllust = onOpenIllust,
                        onBookmark = onBookmark,
                        onLoadMore = onLoadMore,
                        gridState = worksGridState,
                        backgroundColor = backgroundColor,
                        onIllustLongClick = onIllustLongClick,
                    )
                }

                1 -> {
                    if (isMuted) {
                        UserInfoPage(backgroundColor) { MutedUserContentNotice(onUnmuteUser) }
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
                            onIllustLongClick = onIllustLongClick,
                        )
                    }
                }

                else -> {
                    UserInfoPage(backgroundColor) { UserDetailsCard(user) }
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    user: UserProfile,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    followAnimationTrigger: Int,
    backgroundColor: Color,
    onAvatarClick: () -> Unit,
    tabListState: LazyListState,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
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
        UserProfileInfo(
            user = user,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
            onToggleFollow = onToggleFollow,
            isMuted = isMuted,
            onUnmuteUser = onUnmuteUser,
            followAnimationTrigger = followAnimationTrigger,
            backgroundColor = backgroundColor,
            onAvatarClick = onAvatarClick,
            tabListState = tabListState,
        )
    }
}

@Composable
internal fun UserProfileSmallTopAppBar(
    user: UserProfile,
    sortOrder: UserWorkSortOrder,
    typeFilter: UserWorkTypeFilter,
    onSortOrderChange: (UserWorkSortOrder) -> Unit,
    onTypeFilterChange: (UserWorkTypeFilter) -> Unit,
    onBack: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenRelatedUsers: () -> Unit,
    onTitleClick: () -> Unit = {},
    compact: Boolean,
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.detail_share)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val sortLabel = stringResource(R.string.action_sort)
    val sortNewestLabel = stringResource(R.string.user_sort_newest)
    val sortOldestLabel = stringResource(R.string.user_sort_oldest)
    val sortPopularLabel = stringResource(R.string.user_sort_popular)
    val filterCategoryLabel = stringResource(R.string.user_filter_category)
    val filterAllLabel = stringResource(R.string.user_filter_all)
    val filterIllustLabel = stringResource(R.string.user_filter_illust_only)
    val filterMangaLabel = stringResource(R.string.user_filter_manga_only)
    val relatedLabel = stringResource(R.string.user_tab_related)
    val muteLabel = stringResource(R.string.dialog_mute)
    val shareTitle = user.name.ifBlank { "@${user.account}" }
    val profileUrl = remember(user.id) { "https://www.pixiv.net/users/${user.id}" }
    var showMoreMenu by remember { mutableStateOf(false) }

    val menuEntries =
        remember(
            sortOrder,
            typeFilter,
            shareTitle,
            profileUrl,
            user.id,
            shareLabel,
            shareFailedMessage,
            sortLabel,
            sortNewestLabel,
            sortOldestLabel,
            sortPopularLabel,
            filterCategoryLabel,
            filterAllLabel,
            filterIllustLabel,
            filterMangaLabel,
            relatedLabel,
            muteLabel,
        ) {
            listOf(
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = sortLabel,
                                children =
                                    listOf(
                                        DropdownItem(
                                            text = sortNewestLabel,
                                            selected = sortOrder == UserWorkSortOrder.Newest,
                                            onClick = { onSortOrderChange(UserWorkSortOrder.Newest) },
                                        ),
                                        DropdownItem(
                                            text = sortOldestLabel,
                                            selected = sortOrder == UserWorkSortOrder.Oldest,
                                            onClick = { onSortOrderChange(UserWorkSortOrder.Oldest) },
                                        ),
                                        DropdownItem(
                                            text = sortPopularLabel,
                                            selected = sortOrder == UserWorkSortOrder.MostBookmarks,
                                            onClick = { onSortOrderChange(UserWorkSortOrder.MostBookmarks) },
                                        ),
                                    ),
                            ),
                            DropdownItem(
                                text = filterCategoryLabel,
                                children =
                                    listOf(
                                        DropdownItem(
                                            text = filterAllLabel,
                                            selected = typeFilter == UserWorkTypeFilter.All,
                                            onClick = { onTypeFilterChange(UserWorkTypeFilter.All) },
                                        ),
                                        DropdownItem(
                                            text = filterIllustLabel,
                                            selected = typeFilter == UserWorkTypeFilter.IllustOnly,
                                            onClick = { onTypeFilterChange(UserWorkTypeFilter.IllustOnly) },
                                        ),
                                        DropdownItem(
                                            text = filterMangaLabel,
                                            selected = typeFilter == UserWorkTypeFilter.MangaOnly,
                                            onClick = { onTypeFilterChange(UserWorkTypeFilter.MangaOnly) },
                                        ),
                                    ),
                            ),
                        ),
                ),
                DropdownEntry(
                    items =
                        listOf(
                            DropdownItem(
                                text = shareLabel,
                                onClick = {
                                    runCatching {
                                        val intent =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, "$shareTitle\n$profileUrl")
                                            }
                                        context.startActivity(Intent.createChooser(intent, shareLabel))
                                    }.onFailure { onMessage(shareFailedMessage) }
                                },
                            ),
                            DropdownItem(
                                text = relatedLabel,
                                onClick = onOpenRelatedUsers,
                            ),
                            DropdownItem(
                                text = muteLabel,
                                onClick = {
                                    onMuteUser()
                                    onBack()
                                },
                            ),
                        ),
                ),
            )
        }

    val barScrimColor by animateColorAsState(
        targetValue = if (compact) MiuixTheme.colorScheme.background.copy(alpha = 0.76f) else Color.Transparent,
        label = "profile-top-bar-color",
    )
    Box(Modifier.fillMaxWidth()) {
        if (compact && user.backgroundImageUrl != null) {
            PixivImage(
                url = user.backgroundImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .matchParentSize()
                        .blur(24.dp),
            )
        }
        Box(Modifier.matchParentSize().background(barScrimColor))
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderOverlayIcon(
                icon = MiuixIcons.Back,
                onClick = onBack,
                backgroundColor = if (compact) Color.Transparent else Color.White.copy(alpha = 0.92f),
                contentColor = if (compact) MiuixTheme.colorScheme.onBackground else Color.Black,
            )
            if (compact) {
                Text(
                    text = shareTitle,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                            .miuixClickable(onClick = onTitleClick),
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Box {
                HeaderOverlayIcon(
                    icon = MiuixIcons.More,
                    onClick = { showMoreMenu = true },
                    backgroundColor = if (compact) Color.Transparent else Color.White.copy(alpha = 0.92f),
                    contentColor = if (compact) MiuixTheme.colorScheme.onBackground else Color.Black,
                )
                OverlayCascadingListPopup(
                    show = showMoreMenu,
                    entries = menuEntries,
                    onDismissRequest = { showMoreMenu = false },
                )
            }
        }
    }
}

@Composable
private fun UserProfileInfo(
    user: UserProfile,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    followAnimationTrigger: Int,
    backgroundColor: Color,
    onAvatarClick: () -> Unit,
    tabListState: LazyListState,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).offset(y = (-48).dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AvatarImage(
                user.profileImageUrl,
                user.name,
                104.dp,
                Modifier
                    .border(androidx.compose.foundation.BorderStroke(4.dp, backgroundColor), CircleShape)
                    .miuixClickable(onClick = onAvatarClick),
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.miuixClickable(pressedScale = 0.94f, haptic = true, onClick = if (isMuted) onUnmuteUser else onToggleFollow)) {
                if (isMuted) MutedUserPill() else FollowPill(user.isFollowed, followAnimationTrigger)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                user.name.ifBlank { "@${user.account}" },
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.account.isNotBlank()) {
                Text(
                    "@${user.account}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (user.comment.isNotBlank()) {
                Text(
                    user.comment,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        UserProfileTabs(selectedTab, onTabSelected, listState = tabListState)
    }
}

@Composable
internal fun UserProfileTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val tabs =
        listOf(
            stringResource(R.string.user_tab_works),
            stringResource(R.string.user_tab_bookmarks),
            stringResource(R.string.user_tab_info),
        )
    TabRowWithContour(
        modifier = modifier.fillMaxWidth(),
        tabs = tabs,
        selectedTabIndex = selectedTab,
        onTabSelected = onTabSelected,
        listState = listState,
    )
}

@Composable
private fun MutedUserPill() {
    Box(
        Modifier.squircleSurface(MiuixTheme.colorScheme.error, 24.dp).padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.detail_unmute), color = Color.White, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.body2)
    }
}

@Composable
private fun MutedUserContentNotice(onUnmuteUser: () -> Unit) {
    ElevatedPanel(contentPadding = PaddingValues(18.dp)) {
        Text(
            stringResource(R.string.detail_muted_artist),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Black,
        )
        Text(
            stringResource(R.string.detail_muted_artist_blur, stringResource(R.string.detail_muted_artist_blur_default)),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            lineHeight = 20.sp,
        )
        Button(
            onClick = onUnmuteUser,
            colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.error),
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
    onIllustLongClick: ((Illust) -> Unit)? = null,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(adaptiveProfileGridColumns()),
        modifier = Modifier.fillMaxSize().background(backgroundColor),
        contentPadding = profileGridContentPadding(),
        horizontalArrangement = Arrangement.spacedBy(ProfileGridHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(ProfileGridVerticalSpacing),
    ) {
        items(illusts, key = { "${keyPrefix}_${it.id}" }, contentType = { "illust_card" }) { illust ->
            IllustCard(
                illust = illust,
                onBookmark = { onBookmark(illust) },
                onClick = { onOpenIllust(illust) },
                onLongClick = onIllustLongClick?.let { { it(illust) } },
                modifier = Modifier.animateItem(),
                highQualityImages = settings.useHighQualityFeedImages,
                showAiBadge = settings.showAiBadge,
            )
        }
        if (illusts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(emptyLabel) }
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
    content: @Composable () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().background(backgroundColor),
        contentPadding = PaddingValues(14.dp, 0.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { content() }
    }
}

@Composable
internal fun RelatedCreatorsSheetContent(
    users: List<UserPreview>,
    hasMore: Boolean,
    loading: Boolean,
    onOpenUser: (UserPreview) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(contentType = "related_intro") {
            Text(
                text = stringResource(R.string.user_related_summary),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        lazyListItems(users, key = { "related_user_${it.id}" }, contentType = { "related_user" }) { relatedUser ->
            UserResultCard(
                user = relatedUser,
                onClick = { onOpenUser(relatedUser) },
            )
        }
        if (users.isEmpty() && loading) {
            item(contentType = "related_loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
        } else if (users.isEmpty()) {
            item(contentType = "related_empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EmptyState(stringResource(R.string.user_related_empty))
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.action_reload))
                    }
                }
            }
        }
        if (hasMore) {
            item(contentType = "related_more") {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    if (loading) {
                        LoadingIndicator(Modifier.size(20.dp))
                    } else {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDetailsCard(user: UserProfile) {
    ElevatedPanel {
        SettingRow(stringResource(R.string.user_id_label), user.id.toString()) {}
        DividerLine()
        SettingRow(stringResource(R.string.settings_account), "@${user.account}") {}
        if (user.comment.isNotBlank()) {
            DividerLine()
            Text(user.comment, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.body1, lineHeight = 23.sp)
        }
    }
}
