package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.data.pixiv.CommentStore
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    var commentText by remember { mutableStateOf("") }
    val hideCommentInput = remember(state.comments) {
        state.comments.any { it.isPixivCommentDisabledNotice() }
    }

    LaunchedEffect(store) {
        store.fetch()
    }
    AutoLoadMoreEffect(
        enabled = settings.autoLoadMore,
        nextUrl = state.nextUrl,
        isLoading = state.isLoading,
        onLoadMore = { scope.launch { store.next() } },
    )

    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.detail_comments),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        endAction = {
            IconButton(onClick = { scope.launch { store.fetch() } }) {
                Icon(imageVector = MiuixIcons.Refresh, contentDescription = stringResource(R.string.action_load_more))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PullToRefresh(
                isRefreshing = state.isLoading && state.comments.isNotEmpty(),
                onRefresh = { scope.launch { store.fetch() } },
                modifier = Modifier.weight(1f),
            ) {
                androidx.compose.foundation.lazy.LazyColumn(
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
                    items(state.comments, key = { it.id ?: it.hashCode().toLong() }) { comment ->
                        CommentRow(
                            comment = comment,
                            onOpenUser = comment.user?.id?.let { userId -> { onOpenUser(userId) } },
                        )
                    }
                    if (!settings.autoLoadMore && state.nextUrl != null) {
                        item {
                            Button(
                                onClick = { scope.launch { store.next() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = overlayActionButtonColors(),
                            ) {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                    if (state.isLoading && state.comments.isEmpty()) {
                        item {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }

            if (!hideCommentInput) {
                ElevatedPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    contentPadding = PaddingValues(12.dp),
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
                        IconButton(
                            onClick = {
                                val text = commentText.trim()
                                if (text.isNotEmpty()) {
                                    scope.launch {
                                        store.postComment(text)
                                        commentText = ""
                                        store.fetch()
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
            }
        }
    }
}

private fun Comment.isPixivCommentDisabledNotice(): Boolean {
    val message = comment.orEmpty().replace(Regex("\\s+"), "")
    return message.contains("コメントがオフにされています")
}

@Composable
private fun CommentRow(
    comment: Comment,
    onOpenUser: (() -> Unit)?,
) {
    ElevatedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .miuixClickable(onClick = onOpenUser ?: {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val user = comment.user
                val avatarUrl = user?.profileImageUrls?.medium
                if (!avatarUrl.isNullOrBlank()) {
                    AvatarImage(url = avatarUrl, name = user?.name.orEmpty(), size = 38.dp)
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
            Text(
                text = comment.comment.orEmpty(),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
