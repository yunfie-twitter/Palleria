package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.UserProfile
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val MoreCardShape = RoundedCornerShape(18.dp)
private val IconTileShape = RoundedCornerShape(12.dp)

@Composable
fun MoreScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    val quickActions = rememberQuickActions(state, viewModel)
    val utilityActions = rememberUtilityActions(viewModel)

    AccountSwitchSheet(
        show = state.showAccountSwitcher,
        accounts = state.settings.accounts,
        activeAccountIndex = state.settings.activeAccountIndex,
        viewModel = viewModel,
        onDismiss = viewModel::closeAccountSwitcher,
        onAddAccount = viewModel::openAccountLoginMethod,
    )

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.nav_more),
                largeTitle = stringResource(R.string.nav_more),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 18.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                MoreHeader(
                    isLoggedIn = state.settings.refreshToken.isNotBlank(),
                    account = state.currentAccount,
                    onClick = viewModel::openAccountSwitcher,
                )
            }
            item { ActionList(actions = quickActions + utilityActions) }
        }
    }
}

@Composable
private fun rememberQuickActions(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
): List<MoreAction> {
    val context = LocalContext.current
    return remember(
        state.activeDownloads,
        state.settings.favoriteTags.size,
        state.settings.mutedIllusts.size,
        state.settings.mutedUsers.size,
        state.settings.mutedTags.size,
        state.settings.viewHistory.size,
    ) {
        val mutedTotal = state.settings.mutedIllusts.size +
                state.settings.mutedUsers.size +
                state.settings.mutedTags.size

        listOf(
            MoreAction(
                title = context.getString(R.string.more_settings),
                summary = context.getString(R.string.more_settings_summary),
                icon = MiuixIcons.Settings,
                onClick = viewModel::openSettings,
            ),
            MoreAction(
                title = context.getString(R.string.more_view_history),
                summary = context.getString(R.string.data_items_count, state.settings.viewHistory.size),
                icon = MiuixIcons.Timer,
                badge = state.settings.viewHistory.size.badgeText(),
                onClick = viewModel::openViewHistory,
            ),
            MoreAction(
                title = context.getString(R.string.more_favorite_tags),
                summary = context.getString(R.string.data_items_count, state.settings.favoriteTags.size),
                icon = MiuixIcons.FavoritesFill,
                badge = state.settings.favoriteTags.size.badgeText(),
                onClick = viewModel::openFavoriteTags,
            ),
            MoreAction(
                title = context.getString(R.string.more_mute_settings),
                summary = context.getString(R.string.data_items_count, mutedTotal),
                icon = MiuixIcons.Filter,
                badge = mutedTotal.badgeText(),
                onClick = viewModel::openMuteSettings,
            ),
            MoreAction(
                title = context.getString(R.string.more_save_progress),
                summary = if (state.activeDownloads > 0) context.getString(R.string.more_save_progress_saving, state.activeDownloads) else context.getString(R.string.more_save_progress_idle),
                icon = MiuixIcons.Download,
                badge = state.activeDownloads.badgeText(),
                onClick = {
                    viewModel.showMessage(
                        if (state.activeDownloads > 0) {
                            context.getString(R.string.more_saving_message, state.activeDownloads, state.settings.simultaneousDownloads.coerceIn(1, 4))
                        } else {
                            context.getString(R.string.more_no_background_task)
                        },
                    )
                },
            ),
            MoreAction(
                title = context.getString(R.string.more_app_data),
                summary = context.getString(R.string.more_app_data_summary),
                icon = MiuixIcons.Photos,
                onClick = viewModel::openAppData,
            ),
        )
    }
}

@Composable
private fun rememberUtilityActions(viewModel: IllustiaViewModel): List<MoreAction> {
    val context = LocalContext.current
    return remember(viewModel) {
        listOf(
            MoreAction(
                title = context.getString(R.string.more_about),
                summary = "v1.0.2",
                icon = MiuixIcons.More,
                onClick = viewModel::openAbout,
            ),
        )
    }
}

@Composable
private fun MoreHeader(
    isLoggedIn: Boolean,
    account: UserProfile?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onBackground,
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AccountAvatar(account = account)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = accountTitle(isLoggedIn, account),
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = accountSubtitle(isLoggedIn, account),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AccountAvatar(account: UserProfile?) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = account?.profileImageUrl
        if (!avatarUrl.isNullOrBlank()) {
            PixivImage(
                url = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
        } else {
            Icon(
                imageVector = MiuixIcons.Contacts,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun ActionList(actions: List<MoreAction>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) {
                HorizontalDivider()
            }
            ActionRow(action = action)
        }
    }
}

@Composable
private fun ActionRow(action: MoreAction) {
    BasicComponent(
        title = action.title,
        summary = action.summary,
        modifier = Modifier.fillMaxWidth(),
        startAction = { IconTile(icon = action.icon, size = 36.dp, iconSize = 20.dp) },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                action.badge?.let { Badge(it) }
                Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        },
        onClick = action.onClick,
    )
}

@Composable
private fun IconTile(
    icon: ImageVector,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(IconTileShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.error)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onError,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Black,
        )
    }
}

private data class MoreAction(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val badge: String? = null,
    val onClick: () -> Unit,
)

private fun Int.badgeText(): String? {
    if (this <= 0) return null
    return if (this > 99) "99+" else toString()
}

@Composable
private fun accountTitle(isLoggedIn: Boolean, account: UserProfile?): String {
    return when {
        !isLoggedIn -> stringResource(R.string.more_not_logged_in)
        account != null -> account.name.ifBlank { "@${account.account}" }
        else -> stringResource(R.string.more_logged_in_account)
    }
}

@Composable
private fun accountSubtitle(
    isLoggedIn: Boolean,
    account: UserProfile?,
): String {
    return when {
        !isLoggedIn -> stringResource(R.string.more_login_prompt)
        account != null && account.account.isNotBlank() -> "@${account.account}"
        else -> stringResource(R.string.more_connected_pixiv)
    }
}
