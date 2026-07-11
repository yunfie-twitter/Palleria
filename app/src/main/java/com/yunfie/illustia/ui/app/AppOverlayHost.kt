package com.yunfie.illustia.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.NonAmoledDarkTheme
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.screens.CommentScreen
import com.yunfie.illustia.ui.screens.RefreshTokenLoginBottomSheet
import com.yunfie.illustia.ui.screens.UserProfileScreen
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun AppOverlayHost(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    showTokenLogin: Boolean,
    onDismissTokenLogin: () -> Unit,
    selectedCommentTarget: Pair<Long, com.yunfie.illustia.data.pixiv.CommentArtworkType>?,
    onDismissComments: () -> Unit,
) {
    val configuration = LocalConfiguration.current

    selectedCommentTarget?.let { target ->
        CommentScreen(
            show = true,
            id = target.first,
            type = target.second,
            viewModel = viewModel,
            onDismiss = onDismissComments,
            onBack = onDismissComments,
            onOpenUser = { userId ->
                onDismissComments()
                viewModel.openUser(userId)
            },
        )
    }

    appState.state.longPressedIllust?.let { illust ->
        OverlayBottomSheet(
            show = true,
            title = illust.title.ifBlank { stringResource(R.string.dialog_work_options) },
            onDismissRequest = viewModel::closeIllustOptions,
            backgroundColor = LocalBottomSheetBackgroundColor.current,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dialog_artist_label, illust.artistName),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
                Button(
                    onClick = {
                        viewModel.closeIllustOptions()
                        viewModel.openIllust(illust)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.dialog_show_detail))
                }
                Button(
                    onClick = {
                        viewModel.closeIllustOptions()
                        viewModel.toggleBookmark(illust)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        if (illust.isBookmarked) {
                            stringResource(R.string.action_remove_bookmark)
                        } else {
                            stringResource(R.string.action_bookmark)
                        },
                    )
                }
                Button(
                    onClick = {
                        viewModel.closeIllustOptions()
                        viewModel.saveImage(illust.originalImageUrl ?: illust.imageUrl, "illustia_${illust.id}")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.detail_save_image))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.closeIllustOptions()
                            viewModel.muteIllust(illust.id)
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.detail_mute_work), color = MiuixTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            viewModel.closeIllustOptions()
                            viewModel.muteUser(illust.artistId)
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.detail_mute_artist), color = MiuixTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    appState.state.selectedUser?.let { user ->
        if (!appState.state.showUserPage && !appState.state.userPageDismissed) {
            val userSheetBackground = LocalBottomSheetBackgroundColor.current
            val userSheetHeight = minOf(configuration.screenHeightDp.dp * 0.68f, 560.dp)
            WindowBottomSheet(
                show = true,
                title = user.name.ifBlank { "@${user.account}" },
                backgroundColor = userSheetBackground,
                startAction = {
                    IconButton(onClick = viewModel::closeUser) {
                        Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                endAction = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::expandUserSheetToPage) {
                            Icon(
                                imageVector = MiuixIcons.TopDownloads,
                                contentDescription = stringResource(R.string.user_open_full_page),
                            )
                        }
                        WindowIconDropdownMenu(
                            entry = DropdownEntry(
                                items = listOf(
                                    DropdownItem(
                                        text = stringResource(R.string.dialog_mute),
                                        onClick = {
                                            viewModel.muteUser(user.id)
                                            viewModel.closeUser()
                                        },
                                    ),
                                ),
                            ),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(R.string.detail_more),
                            )
                        }
                    }
                },
                onDismissRequest = viewModel::closeUser,
            ) {
                NonAmoledDarkTheme {
                    UserProfileScreen(
                        user = user,
                        settings = appState.state.settings,
                        illusts = appState.state.selectedUserIllusts,
                        bookmarks = appState.state.selectedUserBookmarks,
                        hasMore = appState.state.selectedUserNextUrl != null,
                        bookmarkHasMore = appState.state.selectedUserBookmarksNextUrl != null,
                        onBack = viewModel::closeUser,
                        onOpenIllust = { illust ->
                            viewModel.closeUser()
                            viewModel.openIllust(illust)
                        },
                        onBookmark = viewModel::toggleBookmark,
                        onLoadMore = viewModel::loadMoreUserIllusts,
                        onLoadBookmarks = viewModel::loadSelectedUserBookmarks,
                        onLoadMoreBookmarks = viewModel::loadMoreSelectedUserBookmarks,
                        onToggleFollow = { viewModel.toggleFollow(user) },
                        onMuteUser = { viewModel.muteUser(user.id) },
                        onMessage = viewModel::showMessage,
                        isMuted = appState.state.settings.mutedUsers.contains(user.id),
                        onUnmuteUser = { viewModel.unmuteUser(user.id) },
                        gridState = viewModel.userProfileGridState(user.id),
                        showHeaderControls = false,
                        backgroundColor = userSheetBackground,
                        contentHeight = userSheetHeight,
                    )
                }
            }
        }
    }

    if (showTokenLogin) {
        RefreshTokenLoginBottomSheet(
            state = appState.state,
            viewModel = viewModel,
            onDismiss = onDismissTokenLogin,
        )
    }

    MiuixConfirmDialog(
        show = appState.state.showReloginRequiredDialog,
        title = stringResource(R.string.dialog_relogin_title),
        summary = stringResource(R.string.dialog_relogin_summary),
        confirmText = stringResource(R.string.dialog_relogin_button),
        onConfirm = viewModel::openWebLogin,
        onDismiss = viewModel::dismissReloginRequiredDialog,
    )

    appState.state.pendingBookmarkRemoval?.let { illust ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.dialog_unbookmark_title),
            summary = stringResource(
                R.string.dialog_unbookmark_confirm,
                illust.title.ifBlank { stringResource(R.string.detail_muted_artist_blur_default) },
            ),
            confirmText = stringResource(R.string.action_remove_bookmark),
            destructive = true,
            onConfirm = viewModel::confirmBookmarkRemoval,
            onDismiss = viewModel::cancelBookmarkRemoval,
        )
    }

    if (appState.state.showLockRecoveryDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.app_lock_recovery_title),
            summary = stringResource(R.string.app_lock_recovery_summary),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = {},
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = viewModel::resetAppLockData,
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        stringResource(R.string.app_lock_recovery_reset),
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = viewModel::openRecoveryWebLogin,
                    modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        stringResource(R.string.app_lock_recovery_verify),
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
