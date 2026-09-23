package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
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
fun NavigationSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    val navigationCustomizationFlag = state.settings.isFeatureEnabled(FeatureFlag.NavigationCustomization)
    val shortsFeedFlag = state.settings.isFeatureEnabled(FeatureFlag.ShortsFeed)
    val hideHomeNovelButtonFlag = state.settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton)

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

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.navigation_settings_title),
                largeTitle = stringResource(R.string.navigation_settings_title),
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
                Section(stringResource(R.string.experimental_navigation_section)) {
                    ElevatedPanel {
                        var hasItemAbove = false
                        if (navigationCustomizationFlag) {
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
                            hasItemAbove = true
                        }

                        if (shortsFeedFlag) {
                            if (hasItemAbove) DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.general_shorts_feed),
                                checked = state.settings.shortsFeedEnabled,
                                onCheckedChange = viewModel::updateShortsFeedEnabled,
                                summary = stringResource(R.string.general_shorts_feed_desc),
                            )
                            if (state.settings.shortsFeedEnabled) {
                                DividerLine()
                                SettingSwitchRow(
                                    title = stringResource(R.string.general_shorts_feed_disable_horizontal_swipe),
                                    checked = state.settings.disableHorizontalSwipeInShortsFeed,
                                    onCheckedChange = viewModel::updateDisableHorizontalSwipeInShortsFeed,
                                    summary = stringResource(R.string.general_shorts_feed_disable_horizontal_swipe_desc),
                                )
                            }
                            hasItemAbove = true
                        }

                        if (hideHomeNovelButtonFlag) {
                            if (hasItemAbove) DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_hide_home_novel_button),
                                checked = state.settings.hideHomeNovelButton,
                                onCheckedChange = viewModel::updateHideHomeNovelButton,
                                summary = stringResource(R.string.experimental_hide_home_novel_button_desc),
                            )
                        }
                    }
                }
            }

            if (navigationCustomizationFlag) {
                item {
                    Section(stringResource(R.string.navigation_section_tabs)) {
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
            }
        }
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
        startAction = {
            Switch(
                checked = visible,
                onCheckedChange = onVisibleChange,
                enabled = canHide || !visible,
            )
        },
        endActions = {
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
        },
    )
}

private fun activeNavigationIds(shortsFeedEnabled: Boolean): List<String> =
    listOf("home", if (shortsFeedEnabled) "shorts" else "search", "bookmarks", "ranking", "more")

private fun normalizeNavigationOrder(
    order: List<String>,
    activeIds: List<String>,
): List<String> = order.filter { it in activeIds }.distinct() + activeIds.filterNot { it in order }

private fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> =
    toMutableList().apply {
        add(to, removeAt(from))
    }

@Composable
private fun navigationStyleLabel(value: String): String =
    when (value) {
        "floating" -> stringResource(R.string.experimental_navigation_floating)
        "auto" -> stringResource(R.string.experimental_navigation_auto)
        else -> stringResource(R.string.experimental_navigation_standard)
    }

@Composable
private fun navigationLabel(id: String): String =
    when (id) {
        "ranking" -> stringResource(R.string.nav_ranking)
        "bookmarks" -> stringResource(R.string.nav_bookmarks)
        "search" -> stringResource(R.string.nav_search)
        "shorts" -> stringResource(R.string.nav_shorts_feed)
        "more" -> stringResource(R.string.nav_more)
        else -> stringResource(R.string.nav_home)
    }
