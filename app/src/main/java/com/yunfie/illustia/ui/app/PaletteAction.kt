package com.yunfie.illustia.ui.app

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class PaletteAction(
    @param:StringRes val title: Int,
    val icon: ImageVector,
    @param:StringRes val categoryRes: Int,
    val keywords: List<String> = emptyList(),
) {
    Home(
        R.string.command_palette_home,
        MiuixIcons.Home,
        R.string.command_palette_category_navigation,
        listOf("home", "feed"),
    ),
    Search(
        R.string.command_palette_search,
        MiuixIcons.Search,
        R.string.command_palette_category_action,
    ),
    Ranking(
        R.string.nav_ranking,
        MiuixIcons.TopDownloads,
        R.string.command_palette_category_navigation,
        listOf("ranking", "top"),
    ),
    Bookmarks(
        R.string.nav_bookmarks_full,
        MiuixIcons.FavoritesFill,
        R.string.command_palette_category_navigation,
        listOf("bookmarks", "fav", "bm"),
    ),
    Novel(
        R.string.nav_novel,
        MiuixIcons.Notes,
        R.string.command_palette_category_navigation,
        listOf("novel", "book"),
    ),
    ShortsFeed(
        R.string.command_palette_shorts_feed,
        MiuixIcons.Photos,
        R.string.command_palette_category_navigation,
        listOf("shorts", "tiktok", "reel"),
    ),
    ViewHistory(
        R.string.command_palette_view_history,
        MiuixIcons.Timer,
        R.string.command_palette_category_navigation,
        listOf("history", "viewed", "rireki"),
    ),
    FavoriteTags(
        R.string.command_palette_favorite_tags,
        MiuixIcons.FavoritesFill,
        R.string.command_palette_category_navigation,
        listOf("tag", "favorites", "favtags"),
    ),
    DownloadQueue(
        R.string.command_palette_download_queue,
        MiuixIcons.Download,
        R.string.command_palette_category_navigation,
        listOf("downloads", "dl", "queue"),
    ),
    OfflineLibrary(
        R.string.command_palette_offline_library,
        MiuixIcons.Favorites,
        R.string.command_palette_category_navigation,
        listOf("offline", "saved", "hozon"),
    ),
    Notifications(
        R.string.command_palette_notifications,
        MiuixIcons.Messages,
        R.string.command_palette_category_navigation,
        listOf("notifications", "news", "tsuuchi"),
    ),
    PallaSyncDevices(
        R.string.command_palette_pallasync_devices,
        MiuixIcons.Share,
        R.string.command_palette_category_settings,
        listOf("pallasync", "devices", "sync"),
    ),
    PallaSyncSync(
        R.string.command_palette_pallasync_sync,
        MiuixIcons.Refresh,
        R.string.command_palette_category_action,
        listOf("sync now", "push", "sync"),
    ),
    SwitchAccount(
        R.string.command_palette_switch_account,
        MiuixIcons.Contacts,
        R.string.command_palette_category_action,
        listOf("account", "switch user", "login"),
    ),
    ClearCache(
        R.string.command_palette_clear_cache,
        MiuixIcons.Remove,
        R.string.command_palette_category_action,
        listOf("clear cache", "purge", "gc"),
    ),
    RefreshFeed(
        R.string.command_palette_refresh_feed,
        MiuixIcons.Refresh,
        R.string.command_palette_category_action,
        listOf("refresh", "reload", "sync"),
    ),
    OpenFromClipboard(
        R.string.command_palette_open_clipboard,
        MiuixIcons.Import,
        R.string.command_palette_category_action,
        listOf("id", "pixiv.net", "jump"),
    ),
    ToggleAi(
        R.string.command_palette_toggle_ai,
        MiuixIcons.Filter,
        R.string.command_palette_category_appearance,
        listOf("ai", "toggle ai", "filter"),
    ),
    ToggleR18(
        R.string.command_palette_toggle_r18,
        MiuixIcons.Show,
        R.string.command_palette_category_appearance,
        listOf("r18", "nsfw", "safe"),
    ),
    ToggleGridColumns(
        R.string.command_palette_toggle_grid_columns,
        MiuixIcons.Photos,
        R.string.command_palette_category_appearance,
        listOf("grid", "columns", "layout"),
    ),
    ToggleImageQuality(
        R.string.command_palette_toggle_image_quality,
        MiuixIcons.Background,
        R.string.command_palette_category_appearance,
        listOf("quality", "hd", "original"),
    ),
    ToggleUgoiraAutoPlay(
        R.string.command_palette_toggle_ugoira_autoplay,
        MiuixIcons.Timer,
        R.string.command_palette_category_appearance,
        listOf("ugoira", "autoplay", "gif"),
    ),
    MuteSettings(
        R.string.command_palette_mute_settings,
        MiuixIcons.Close,
        R.string.command_palette_category_settings,
        listOf("mute", "blocked", "ng"),
    ),
    AppData(
        R.string.command_palette_app_data,
        MiuixIcons.Tune,
        R.string.command_palette_category_settings,
        listOf("storage", "data", "appdata"),
    ),
    Settings(
        R.string.command_palette_settings,
        MiuixIcons.Settings,
        R.string.command_palette_category_settings,
        listOf("settings", "pref"),
    ),
    Flags(
        R.string.command_palette_flags,
        MiuixIcons.Tune,
        R.string.command_palette_category_settings,
        listOf("flags", "experiment"),
    ),
    Light(
        R.string.command_palette_light,
        MiuixIcons.Theme,
        R.string.command_palette_category_appearance,
        listOf("light", "theme"),
    ),
    Dark(
        R.string.command_palette_dark,
        MiuixIcons.Theme,
        R.string.command_palette_category_appearance,
        listOf("dark", "theme"),
    ),
    System(
        R.string.command_palette_system,
        MiuixIcons.Theme,
        R.string.command_palette_category_appearance,
        listOf("system", "theme"),
    ),
}

internal fun matchingPaletteActions(
    query: String,
    labels: Map<PaletteAction, String>,
): List<PaletteAction> {
    val trimmed = query.trim()
    val commands =
        PaletteAction.entries.filter {
            it != PaletteAction.Search &&
                (
                    labels.getValue(it).contains(trimmed, ignoreCase = true) ||
                        it.keywords.any { kw -> kw.contains(trimmed, ignoreCase = true) }
                )
        }
    return if (trimmed.isBlank()) commands else commands + PaletteAction.Search
}

@Composable
internal fun CommandPalette(
    onDismiss: () -> Unit,
    onAction: (PaletteAction, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val labels =
        PaletteAction.entries.associateWith {
            if (it == PaletteAction.Search) stringResource(it.title, query.trim()) else stringResource(it.title)
        }
    val actions = matchingPaletteActions(query, labels)
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun execute() {
        actions.getOrNull(selectedIndex)?.let { onAction(it, query.trim()) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val colorScheme = MiuixTheme.colorScheme

        Column(
            modifier =
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    ).clip(RoundedCornerShape(24.dp))
                    .background(colorScheme.surfaceContainerHigh)
                    .border(
                        width = 1.dp,
                        color = colorScheme.outline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(24.dp),
                    ).onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) {
                            false
                        } else {
                            when (it.key) {
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }

                                Key.DirectionDown -> {
                                    selectedIndex = (selectedIndex + 1).coerceAtMost(actions.lastIndex)
                                    true
                                }

                                Key.DirectionUp -> {
                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                    true
                                }

                                Key.Enter, Key.NumPadEnter -> {
                                    execute()
                                    true
                                }

                                else -> {
                                    false
                                }
                            }
                        }
                    },
        ) {
            // Search Input Header
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Search,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        selectedIndex = 0
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focus),
                    singleLine = true,
                    textStyle =
                        TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    cursorBrush = SolidColor(colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.command_palette_hint),
                                    color = colorScheme.onSurfaceVariantSummary,
                                    fontSize = 16.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            selectedIndex = 0
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    KeyCapBadge(text = "ESC", onClick = onDismiss)
                }
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colorScheme.outline.copy(alpha = 0.08f)),
            )

            // Commands List
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .heightIn(max = 380.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                if (actions.isEmpty()) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Search,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.command_palette_no_results),
                                color = colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                } else {
                    itemsIndexed(actions, key = { _, action -> action.name }) { index, action ->
                        val isSelected = selectedIndex == index
                        val itemBackground by animateColorAsState(
                            targetValue =
                                if (isSelected) {
                                    colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    Color.Transparent
                                },
                            animationSpec = tween(150),
                            label = "itemBg",
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(itemBackground)
                                    .semantics { selected = isSelected }
                                    .clickable { onAction(action, query.trim()) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Icon Container
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) {
                                                colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                            },
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint =
                                        if (isSelected) {
                                            colorScheme.primary
                                        } else {
                                            colorScheme.onSurfaceVariantSummary
                                        },
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Action Title
                            Text(
                                text = labels.getValue(action),
                                color =
                                    if (isSelected) {
                                        colorScheme.onSurface
                                    } else {
                                        colorScheme.onSurface.copy(alpha = 0.88f)
                                    },
                                fontSize = 14.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Category Pill Badge
                            Box(
                                modifier =
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) {
                                                colorScheme.primary.copy(alpha = 0.15f)
                                            } else {
                                                colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                            },
                                        ).padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = stringResource(action.categoryRes),
                                    color =
                                        if (isSelected) {
                                            colorScheme.primary
                                        } else {
                                            colorScheme.onSurfaceVariantSummary
                                        },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            // Enter KeyCap Badge when selected
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                KeyCapBadge(text = "↵")
                            }
                        }
                    }
                }
            }

            // Divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colorScheme.outline.copy(alpha = 0.08f)),
            )

            // Keyboard Shortcut Footer Bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShortcutHint(
                        keys = listOf("↑", "↓"),
                        label = stringResource(R.string.command_palette_shortcut_navigate),
                    )
                    ShortcutHint(
                        keys = listOf("↵"),
                        label = stringResource(R.string.command_palette_shortcut_select),
                    )
                    ShortcutHint(
                        keys = listOf("ESC"),
                        label = stringResource(R.string.command_palette_shortcut_close),
                    )
                }

                Text(
                    text = stringResource(R.string.flag_command_palette_title),
                    color = colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        LaunchedEffect(Unit) { focus.requestFocus() }
        LaunchedEffect(selectedIndex, query) {
            if (actions.isNotEmpty()) listState.animateScrollToItem(selectedIndex)
        }
    }
}

@Composable
private fun KeyCapBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colorScheme = MiuixTheme.colorScheme
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .background(colorScheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                ).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShortcutHint(
    keys: List<String>,
    label: String,
) {
    val colorScheme = MiuixTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        keys.forEachIndexed { i, k ->
            if (i > 0) Spacer(modifier = Modifier.width(3.dp))
            KeyCapBadge(text = k)
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            color = colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )
    }
}
