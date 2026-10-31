package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.data.pixiv.CommentStore
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.CommentItemSkeleton
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LocalPixivImageProxyBaseUrl
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.ScrollablePullToRefresh
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.prefetchPixivThumbnail
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

/** 開いた直後に表示されるコメント行のアバターを先に温める件数。 */
private const val COMMENT_AVATAR_PREFETCH_LIMIT = 8

@Composable
fun CommentScreen(
    show: Boolean,
    id: Long,
    type: CommentArtworkType,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    if (!show) return
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val store = remember(repository, id, type) { CommentStore(repository, id, type = type) }
    val state by store.state.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val applicationContext = LocalContext.current.applicationContext
    val performHaptic = rememberHapticFeedbackAction()
    var commentText by remember { mutableStateOf("") }
    val hideCommentInput = state.hasDisabledNotice

    // 取得済みのコメントは同期的に初期状態へ入れる。開いた直後に全面スケルトン
    // （squircle の saveLayer + 無限シマー）を出さないことで、シートのスライド中に
    // 毎フレームのオフスクリーン合成が走らないようにする。
    LaunchedEffect(store) {
        store.cachedState()?.let { store.seed(it) }
    }
    // 開く直前にスケルトンを見せた場合のちらつきを抑えるため、一度でも内容を表示したら
    // 以後の再取得・再構成ではシマーを出さない。
    var hasShownContent by remember(store) { mutableStateOf(false) }
    if (state.comments.isNotEmpty()) hasShownContent = true

    var showStampPicker by remember { mutableStateOf(false) }
    var stamps by remember { mutableStateOf<List<PixivStamp>>(emptyList()) }
    var isLoadingStamps by remember { mutableStateOf(false) }

    LaunchedEffect(showStampPicker) {
        if (showStampPicker && stamps.isEmpty()) {
            isLoadingStamps = true
            runCatching {
                stamps = repository.stamps()
            }
            isLoadingStamps = false
        }
    }

    LaunchedEffect(store) {
        store.fetch()
    }
    // アバターは開いた後に初めて要求されるため、シートのスライドアニメーション中に
    // ネットワーク取得とデコードが走ってメインスレッドに割り込む。先に一度だけ
    // 非同期で温め、以降の追加取得では再実行しない。
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    LaunchedEffect(store, proxyBaseUrl) {
        val context = applicationContext
        store.state
            .map { comments -> comments.comments }
            .first { it.isNotEmpty() }
            .take(COMMENT_AVATAR_PREFETCH_LIMIT)
            .forEach { comment ->
                val url = comment.user?.profileImageUrls?.medium
                if (!url.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        prefetchPixivThumbnail(context, proxyPixivImageUrl(url, proxyBaseUrl))
                    }
                }
            }
    }
    val listState = rememberLazyListState()
    AutoLoadMoreEffect(
        listState = listState,
        enabled = settings.autoLoadMore,
        nextUrl = state.nextUrl,
        isLoading = state.isLoading,
        onLoadMore = { scope.launch { store.next() } },
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxSheetHeight = minOf(configuration.screenHeightDp.dp * 0.72f, 560.dp)

    WindowBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.detail_comments),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        endAction = {
            IconButton(onClick = { scope.launch { store.fetch(forceRefresh = true) } }) {
                Icon(imageVector = MiuixIcons.Refresh, contentDescription = stringResource(R.string.action_load_more))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val showInitialSkeletons =
                state.isLoading && state.comments.isEmpty() && !hasShownContent
            val showPaginationSkeletons = settings.autoLoadMore && state.isPaginating
            val shimmer = if (showInitialSkeletons || showPaginationSkeletons) rememberIllustSkeletonShimmer() else null
            ScrollablePullToRefresh(
                isRefreshing = state.isRefreshing,
                onRefresh = { scope.launch { store.fetch(forceRefresh = true) } },
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.errorMessage != null) {
                        item { Text(state.errorMessage ?: "", color = MiuixTheme.colorScheme.error) }
                    }
                    if (state.comments.isEmpty() && !state.isLoading) {
                        item { EmptyState(stringResource(R.string.detail_comments)) }
                    }
                    itemsIndexed(
                        items = state.comments,
                        key = { index, comment -> comment.id ?: "comment_$index" },
                        contentType = { _, _ -> "comment_item" },
                    ) { _, comment ->
                        CommentRow(
                            comment = comment,
                            onOpenUser = onOpenUser,
                        )
                    }
                    if (showPaginationSkeletons) {
                        item(key = "comment_paginating_skeleton", contentType = "comment_skeleton") {
                            CommentItemSkeleton(shimmerValue = shimmer)
                        }
                    } else if (!settings.autoLoadMore && state.nextUrl != null) {
                        item(key = "comment_load_more", contentType = "comment_load_more") {
                            Button(
                                onClick = { scope.launch { store.next() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = overlayActionButtonColors(),
                                enabled = !state.isPaginating,
                            ) {
                                if (state.isPaginating) {
                                    LoadingIndicator(modifier = Modifier.size(18.dp))
                                } else {
                                    Text(stringResource(R.string.action_load_more))
                                }
                            }
                        }
                    }
                    if (showInitialSkeletons) {
                        items(4, key = { "comment_skeleton_$it" }, contentType = { "comment_skeleton" }) {
                            CommentItemSkeleton(shimmerValue = shimmer)
                        }
                    }
                }
            }

            if (!hideCommentInput) {
                ElevatedPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 0.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            label = stringResource(R.string.detail_comments),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        if (type == CommentArtworkType.ILLUST) {
                            IconButton(
                                onClick = { showStampPicker = !showStampPicker },
                                backgroundColor =
                                    if (showStampPicker) {
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                minWidth = 44.dp,
                                minHeight = 44.dp,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Favorites,
                                    contentDescription = stringResource(R.string.comment_stamp_select),
                                    tint = if (showStampPicker) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val text = commentText.trim()
                                if (text.isNotEmpty()) {
                                    performHaptic(AppHapticEffect.Click)
                                    scope.launch {
                                        runCatching {
                                            store.postComment(text)
                                        }.onSuccess {
                                            performHaptic(AppHapticEffect.Success)
                                            commentText = ""
                                            store.fetch()
                                        }
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank(),
                            backgroundColor = MiuixTheme.colorScheme.primary,
                            minWidth = 44.dp,
                            minHeight = 44.dp,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Send,
                                contentDescription = stringResource(R.string.action_add),
                                tint = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                if (showStampPicker) {
                    ElevatedPanel(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        if (isLoadingStamps) {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LoadingIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else if (stamps.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.comment_stamp_empty),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                                contentPadding = PaddingValues(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(stamps, key = { it.id }) { stamp ->
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .miuixClickable {
                                                    performHaptic(AppHapticEffect.Toggle)
                                                    scope.launch {
                                                        runCatching {
                                                            store.postStampComment(stamp.id)
                                                        }.onSuccess {
                                                            performHaptic(AppHapticEffect.Success)
                                                            showStampPicker = false
                                                            store.fetch()
                                                        }
                                                    }
                                                }.padding(4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        PixivImage(
                                            url = stamp.url,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenUser: ((Long) -> Unit)?,
) {
    val userId = comment.user?.id
    val clickableModifier =
        if (userId != null && onOpenUser != null) {
            Modifier.miuixClickable { onOpenUser(userId) }
        } else {
            Modifier
        }
    ElevatedPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(clickableModifier),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val user = comment.user
                val avatarUrl = user?.profileImageUrls?.medium
                if (user != null && !avatarUrl.isNullOrBlank()) {
                    AvatarImage(url = avatarUrl, name = user.name, size = 38.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user?.name.orEmpty(),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = comment.date.orEmpty(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            val stampUrl = comment.stamp?.stampUrl
            if (!stampUrl.isNullOrBlank()) {
                PixivImage(
                    url = stampUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
            if (!comment.comment.isNullOrBlank()) {
                Text(
                    text = comment.comment,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }
    }
}
