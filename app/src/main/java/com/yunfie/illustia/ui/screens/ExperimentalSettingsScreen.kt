package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.DEFAULT_DETAIL_SECTION_ORDER
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.ThemeSwitchSettingRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ExperimentalSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    var showAmoledWarningDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.experimental_settings_title),
                largeTitle = stringResource(R.string.experimental_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Section(stringResource(R.string.general_section_display)) {
                    ElevatedPanel {
                        ThemeSwitchSettingRow(
                            title = stringResource(R.string.general_amoled),
                            checked = state.settings.amoledMode,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showAmoledWarningDialog = true
                                } else {
                                    viewModel.updateAmoledMode(false)
                                }
                            },
                            summary = stringResource(R.string.general_amoled_desc),
                        )
                    }
                }
            }

            item {
                val activeIds = activeNavigationIds(state.settings.shortsFeedEnabled)
                val orderedIds = normalizeNavigationOrder(state.settings.navigationOrder, activeIds)
                val hiddenIds =
                    state.settings.hiddenNavigationTabs.mapTo(mutableSetOf()) { id ->
                        when {
                            state.settings.shortsFeedEnabled && id == "search" -> "shorts"
                            !state.settings.shortsFeedEnabled && id == "shorts" -> "search"
                            else -> id
                        }
                    }
                val visibleIds = orderedIds.filterNot(hiddenIds::contains)
                val selectableStartIds = visibleIds.ifEmpty { orderedIds.take(2) }
                val activeStartId =
                    when {
                        state.settings.shortsFeedEnabled && state.settings.startupScreen == "search" -> "shorts"
                        !state.settings.shortsFeedEnabled && state.settings.startupScreen == "shorts" -> "search"
                        else -> state.settings.startupScreen
                    }
                Section(stringResource(R.string.experimental_navigation_section)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(R.string.experimental_navigation_style),
                            summary = stringResource(R.string.experimental_navigation_style_desc),
                            selected = state.settings.navigationStyle,
                            values = listOf("standard", "floating", "auto"),
                            label = { navigationStyleLabel(it) },
                            onSelect = viewModel::updateNavigationStyle,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.experimental_start_tab),
                            summary = stringResource(R.string.experimental_start_tab_desc),
                            selected = activeStartId.takeIf { it in selectableStartIds } ?: selectableStartIds.first(),
                            values = selectableStartIds,
                            label = { navigationLabel(it) },
                            onSelect = viewModel::updateStartupScreen,
                        )
                    }
                    ElevatedPanel {
                        orderedIds.forEachIndexed { index, id ->
                            NavigationEditorRow(
                                title = navigationLabel(id),
                                visible = id !in hiddenIds,
                                canMoveUp = index > 0,
                                canMoveDown = index < orderedIds.lastIndex,
                                canHide = visibleIds.size > 2 && activeStartId != id,
                                onMoveUp = {
                                    viewModel.updateNavigationOrder(orderedIds.moved(index, index - 1))
                                },
                                onMoveDown = {
                                    viewModel.updateNavigationOrder(orderedIds.moved(index, index + 1))
                                },
                                onVisibleChange = { visible ->
                                    viewModel.updateHiddenNavigationTabs(
                                        (if (visible) hiddenIds - id else hiddenIds + id).toList(),
                                    )
                                },
                            )
                            if (index < orderedIds.lastIndex) DividerLine()
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.experimental_card_section)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_title),
                            checked = state.settings.showCardTitle,
                            onCheckedChange = viewModel::updateShowCardTitle,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_artist),
                            checked = state.settings.showCardArtist,
                            onCheckedChange = viewModel::updateShowCardArtist,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_tags),
                            checked = state.settings.showCardTags,
                            onCheckedChange = viewModel::updateShowCardTags,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_rating),
                            checked = state.settings.showCardBookmarkCount,
                            onCheckedChange = viewModel::updateShowCardBookmarkCount,
                            summary = stringResource(R.string.experimental_card_rating_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_ai),
                            checked = state.settings.showAiBadge,
                            onCheckedChange = viewModel::updateShowAiBadge,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.experimental_artwork_theme_section)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_artwork_theme),
                            checked = state.settings.artworkThemeEnabled,
                            onCheckedChange = viewModel::updateArtworkThemeEnabled,
                            summary = stringResource(R.string.experimental_artwork_theme_desc),
                        )
                    }
                }
            }

            item {
                val orderedSections = normalizeDetailOrder(state.settings.detailSectionOrder)
                Section(stringResource(R.string.experimental_detail_section)) {
                    ElevatedPanel {
                        orderedSections.forEachIndexed { index, id ->
                            OrderEditorRow(
                                title = detailSectionLabel(id),
                                canMoveUp = index > 0,
                                canMoveDown = index < orderedSections.lastIndex,
                                onMoveUp = {
                                    viewModel.updateDetailSectionOrder(orderedSections.moved(index, index - 1))
                                },
                                onMoveDown = {
                                    viewModel.updateDetailSectionOrder(orderedSections.moved(index, index + 1))
                                },
                            )
                            if (index < orderedSections.lastIndex) DividerLine()
                        }
                    }
                }
            }
        }

        MiuixConfirmDialog(
            show = showAmoledWarningDialog,
            title = stringResource(R.string.general_experimental_feature),
            summary = stringResource(R.string.general_amoled_warning_desc),
            confirmText = stringResource(R.string.action_enable),
            onConfirm = {
                viewModel.updateAmoledMode(true)
                showAmoledWarningDialog = false
            },
            onDismiss = { showAmoledWarningDialog = false },
        )
    }
}

@Composable
private fun NavigationEditorRow(
    title: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canHide: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    BasicComponent(
        title = title,
        summary = stringResource(if (visible) R.string.experimental_tab_visible else R.string.experimental_tab_hidden),
        modifier = Modifier.fillMaxWidth(),
        endActions = {
            MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown)
            Switch(
                checked = visible,
                onCheckedChange = onVisibleChange,
                enabled = visible.not() || canHide,
            )
        },
    )
}

@Composable
private fun OrderEditorRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    BasicComponent(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        endActions = { MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown) },
    )
}

@Composable
private fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_up),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(-90f),
        )
    }
    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_down),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(90f),
        )
    }
}

private fun activeNavigationIds(shortsEnabled: Boolean): List<String> =
    listOf(
        "home",
        if (shortsEnabled) "shorts" else "search",
        "bookmarks",
        "ranking",
        "more",
    )

private fun normalizeNavigationOrder(
    order: List<String>,
    active: List<String>,
): List<String> {
    val translated =
        order.map { id ->
            when {
                "shorts" in active && id == "search" -> "shorts"
                "search" in active && id == "shorts" -> "search"
                else -> id
            }
        }
    return translated.filter { it in active }.distinct() + active.filterNot { it in translated }
}

private fun normalizeDetailOrder(order: List<String>): List<String> =
    order.filter { it in DEFAULT_DETAIL_SECTION_ORDER }.distinct() +
        DEFAULT_DETAIL_SECTION_ORDER.filterNot { it in order }

private fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> =
    toMutableList().apply {
        add(to, removeAt(from))
    }

@Composable
private fun navigationLabel(id: String): String =
    when (id) {
        "ranking" -> stringResource(R.string.nav_ranking)
        "bookmarks" -> stringResource(R.string.nav_bookmarks_full)
        "search" -> stringResource(R.string.nav_search)
        "shorts" -> stringResource(R.string.nav_shorts_feed)
        "more" -> stringResource(R.string.nav_more)
        else -> stringResource(R.string.nav_home)
    }

@Composable
private fun navigationStyleLabel(value: String): String =
    when (value) {
        "floating" -> stringResource(R.string.experimental_navigation_floating)
        "auto" -> stringResource(R.string.experimental_navigation_auto)
        else -> stringResource(R.string.experimental_navigation_standard)
    }

@Composable
private fun detailSectionLabel(id: String): String =
    when (id) {
        "tags" -> stringResource(R.string.experimental_detail_tags)
        "description" -> stringResource(R.string.experimental_detail_description)
        "related" -> stringResource(R.string.experimental_detail_related)
        else -> stringResource(R.string.experimental_detail_artist)
    }
