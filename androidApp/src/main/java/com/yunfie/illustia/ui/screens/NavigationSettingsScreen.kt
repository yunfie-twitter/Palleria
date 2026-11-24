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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_move_down
import com.yunfie.illustia.resources.action_move_up
import com.yunfie.illustia.resources.experimental_hide_home_novel_button
import com.yunfie.illustia.resources.experimental_hide_home_novel_button_desc
import com.yunfie.illustia.resources.experimental_navigation_auto
import com.yunfie.illustia.resources.experimental_navigation_floating
import com.yunfie.illustia.resources.experimental_navigation_section
import com.yunfie.illustia.resources.experimental_navigation_standard
import com.yunfie.illustia.resources.experimental_navigation_style
import com.yunfie.illustia.resources.experimental_navigation_style_desc
import com.yunfie.illustia.resources.experimental_start_tab
import com.yunfie.illustia.resources.experimental_start_tab_desc
import com.yunfie.illustia.resources.experimental_tab_hidden
import com.yunfie.illustia.resources.experimental_tab_visible
import com.yunfie.illustia.resources.general_shorts_feed
import com.yunfie.illustia.resources.general_shorts_feed_desc
import com.yunfie.illustia.resources.nav_bookmarks
import com.yunfie.illustia.resources.nav_home
import com.yunfie.illustia.resources.nav_more
import com.yunfie.illustia.resources.nav_ranking
import com.yunfie.illustia.resources.nav_search
import com.yunfie.illustia.resources.nav_shorts_feed
import com.yunfie.illustia.resources.navigation_section_tabs
import com.yunfie.illustia.resources.navigation_settings_title
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import org.jetbrains.compose.resources.stringResource
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
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    val navigationCustomizationFlag = settings.isFeatureEnabled(FeatureFlag.NavigationCustomization)
    val shortsFeedFlag = settings.isFeatureEnabled(FeatureFlag.ShortsFeed)
    val hideHomeNovelButtonFlag = settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton)

    val activeIds = activeNavigationIds(settings.shortsFeedEnabled)
    val orderedIds = normalizeNavigationOrder(settings.navigationOrder, activeIds)
    val hiddenIds =
        settings.hiddenNavigationTabs.mapTo(mutableSetOf()) { id ->
            when {
                settings.shortsFeedEnabled && id == "search" -> "shorts"
                !settings.shortsFeedEnabled && id == "shorts" -> "search"
                else -> id
            }
        }
    val visibleIds = orderedIds.filterNot(hiddenIds::contains)
    val selectableStartIds = visibleIds.ifEmpty { orderedIds.take(2) }
    val activeStartId =
        when {
            settings.shortsFeedEnabled && settings.startupScreen == "search" -> "shorts"
            !settings.shortsFeedEnabled && settings.startupScreen == "shorts" -> "search"
            else -> settings.startupScreen
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.navigation_settings_title),
                largeTitle = stringResource(Res.string.navigation_settings_title),
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
                Section(stringResource(Res.string.experimental_navigation_section)) {
                    ElevatedPanel {
                        var hasItemAbove = false
                        if (navigationCustomizationFlag) {
                            SettingDropdownRow(
                                title = stringResource(Res.string.experimental_navigation_style),
                                summary = stringResource(Res.string.experimental_navigation_style_desc),
                                selected = settings.navigationStyle,
                                values = listOf("standard", "floating", "auto"),
                                label = { navigationStyleLabel(it) },
                                onSelect = viewModel::updateNavigationStyle,
                            )
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(Res.string.experimental_start_tab),
                                summary = stringResource(Res.string.experimental_start_tab_desc),
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
                                title = stringResource(Res.string.general_shorts_feed),
                                checked = settings.shortsFeedEnabled,
                                onCheckedChange = viewModel::updateShortsFeedEnabled,
                                summary = stringResource(Res.string.general_shorts_feed_desc),
                            )
                            hasItemAbove = true
                        }

                        if (hideHomeNovelButtonFlag) {
                            if (hasItemAbove) DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.experimental_hide_home_novel_button),
                                checked = settings.hideHomeNovelButton,
                                onCheckedChange = viewModel::updateHideHomeNovelButton,
                                summary = stringResource(Res.string.experimental_hide_home_novel_button_desc),
                            )
                        }
                    }
                }
            }

            if (navigationCustomizationFlag) {
                item {
                    Section(stringResource(Res.string.navigation_section_tabs)) {
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
        summary = stringResource(if (visible) Res.string.experimental_tab_visible else Res.string.experimental_tab_hidden),
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
                    contentDescription = stringResource(Res.string.action_move_up),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.rotate(-90f),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = MiuixIcons.ChevronForward,
                    contentDescription = stringResource(Res.string.action_move_down),
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
        "floating" -> stringResource(Res.string.experimental_navigation_floating)
        "auto" -> stringResource(Res.string.experimental_navigation_auto)
        else -> stringResource(Res.string.experimental_navigation_standard)
    }

@Composable
private fun navigationLabel(id: String): String =
    when (id) {
        "ranking" -> stringResource(Res.string.nav_ranking)
        "bookmarks" -> stringResource(Res.string.nav_bookmarks)
        "search" -> stringResource(Res.string.nav_search)
        "shorts" -> stringResource(Res.string.nav_shorts_feed)
        "more" -> stringResource(Res.string.nav_more)
        else -> stringResource(Res.string.nav_home)
    }
