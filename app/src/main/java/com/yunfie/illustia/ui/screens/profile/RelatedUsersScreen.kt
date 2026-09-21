package com.yunfie.illustia.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.AutoLoadMoreEffect
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import com.yunfie.illustia.ui.screens.UserResultCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RelatedUsersScreen(
    userId: Long,
    userName: String = "",
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(userId) {
        viewModel.loadSelectedRelatedUsers(targetUserId = userId)
    }

    AutoLoadMoreEffect(
        listState = listState,
        enabled = settings.autoLoadMore,
        nextUrl = uiState.selectedRelatedUsersNextUrl,
        isLoading = uiState.selectedRelatedUsersLoading,
        onLoadMore = { viewModel.loadMoreSelectedRelatedUsers(targetUserId = userId) },
    )

    val titleText =
        if (userName.isNotBlank()) {
            userName
        } else {
            stringResource(R.string.user_tab_related)
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = titleText,
                largeTitle = titleText,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        val adaptivePadding = adaptiveMainNavigationContentPadding()
        PullToRefresh(
            isRefreshing = uiState.selectedRelatedUsersLoading && uiState.selectedRelatedUsers.isNotEmpty(),
            onRefresh = { viewModel.loadSelectedRelatedUsers(targetUserId = userId, force = true) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface),
                contentPadding =
                    PaddingValues(
                        start = adaptivePadding + 16.dp,
                        end = adaptivePadding + 16.dp,
                        top = scaffoldPadding.calculateTopPadding() + 8.dp,
                        bottom = 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(contentType = "related_intro") {
                    Text(
                        text = stringResource(R.string.user_related_summary),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }

                items(
                    items = uiState.selectedRelatedUsers,
                    key = { "related_user_" },
                    contentType = { "related_user" },
                ) { relatedUser ->
                    UserResultCard(
                        user = relatedUser,
                        onClick = { onOpenUser(relatedUser.id) },
                    )
                }

                if (uiState.selectedRelatedUsers.isEmpty() && uiState.selectedRelatedUsersLoading) {
                    item(contentType = "related_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                } else if (uiState.selectedRelatedUsers.isEmpty()) {
                    item(contentType = "related_empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            EmptyState(stringResource(R.string.user_related_empty))
                            Button(onClick = { viewModel.loadSelectedRelatedUsers(targetUserId = userId, force = true) }) {
                                Text(stringResource(R.string.action_reload))
                            }
                        }
                    }
                }

                if (!settings.autoLoadMore && uiState.selectedRelatedUsersNextUrl != null) {
                    item(contentType = "related_more") {
                        Button(
                            onClick = { viewModel.loadMoreSelectedRelatedUsers(targetUserId = userId) },
                            enabled = !uiState.selectedRelatedUsersLoading,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            if (uiState.selectedRelatedUsersLoading) {
                                LoadingIndicator(Modifier.size(20.dp))
                            } else {
                                Text(stringResource(R.string.action_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}
