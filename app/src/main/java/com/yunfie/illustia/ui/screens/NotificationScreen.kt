package com.yunfie.illustia.ui.screens

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.pixiv.PixivNotification
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.smoothScrollToTop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NotificationScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        if (state.notifications.isEmpty()) viewModel.refreshNotifications()
    }
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val performHaptic = rememberHapticFeedbackAction()
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.more_notifications),
                largeTitle = stringResource(R.string.more_notifications),
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            performHaptic(AppHapticEffect.Click)
                            coroutineScope.launch {
                                listState.smoothScrollToTop(scrollBehavior)
                            }
                        }
                    },
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
                actions = { HeaderIcon(MiuixIcons.Refresh, onClick = viewModel::refreshNotifications) },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding =
                PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.notifications.isEmpty() && state.notificationsLoading) {
                item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            } else if (state.notifications.isEmpty()) {
                item { EmptyState(stringResource(R.string.notifications_empty)) }
            }

            items(state.notifications, key = { it.id }) { notification ->
                NotificationCard(notification, onClick = { viewModel.openNotificationTarget(notification.targetUrl) })
                val expanded = state.expandedNotifications[notification.id]
                if (expanded != null) {
                    expanded.forEach { child ->
                        Box(Modifier.padding(start = 24.dp, top = 6.dp)) {
                            NotificationCard(child, compact = true, onClick = { viewModel.openNotificationTarget(child.targetUrl) })
                        }
                    }
                } else if (notification.viewMore != null) {
                    ViewMoreRow(
                        title =
                            notification.viewMore.title
                                .orEmpty()
                                .ifBlank { stringResource(R.string.notifications_load_more) },
                        unread = notification.viewMore.unreadExists,
                        onClick = { viewModel.expandNotification(notification.id) },
                    )
                }
            }

            if (state.notificationNextUrl != null) {
                item {
                    ViewMoreRow(
                        title = stringResource(R.string.notifications_load_more),
                        unread = false,
                        onClick = viewModel::loadMoreNotifications,
                    )
                }
            }
            if (state.notifications.isNotEmpty() && state.notificationsLoading) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: PixivNotification,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = if (compact) 14.dp else 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            NotificationImage(notification)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (!notification.isRead) Box(Modifier.size(7.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary))
                    Text(
                        text = notification.content?.text.toPlainNotificationText(),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = if (compact) 2 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                notification.createdDatetime?.let {
                    Text(
                        it.toDisplayDate(),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (!notification.targetUrl.isNullOrBlank()) {
                Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NotificationImage(notification: PixivNotification) {
    val image = notification.content?.leftImage ?: notification.content?.leftIcon
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (!image.isNullOrBlank()) {
            PixivImage(
                url = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
        } else {
            Icon(MiuixIcons.Messages, contentDescription = null, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun ViewMoreRow(
    title: String,
    unread: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .miuixClickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (unread) "$title · ${stringResource(R.string.notifications_unread)}" else title,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun String?.toPlainNotificationText(): String =
    this?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }.orEmpty()

private fun String.toDisplayDate(): String = replace('T', ' ').substringBefore('+').removeSuffix("Z")
