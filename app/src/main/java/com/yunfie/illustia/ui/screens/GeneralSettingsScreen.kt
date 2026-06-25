package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.appFontLabelRes
import com.yunfie.illustia.settings.appFontOptions
import com.yunfie.illustia.settings.appLanguageLabelRes
import com.yunfie.illustia.settings.appLanguageOptions
import com.yunfie.illustia.settings.appThemeLabel
import com.yunfie.illustia.settings.appThemeOptions
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GeneralSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.general_settings_title),
                largeTitle = stringResource(R.string.general_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Section(stringResource(R.string.general_section_display)) {
                ElevatedPanel {
                    SettingDropdownRow(
                        title = stringResource(R.string.general_theme),
                        summary = stringResource(R.string.general_theme_desc),
                        values = appThemeOptions(),
                        selected = state.settings.themeMode,
                        label = { appThemeLabel(it) },
                        onSelect = viewModel::updateThemeMode,
                        icon = MiuixIcons.Theme,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.general_language),
                        summary = stringResource(R.string.general_language_desc),
                        values = appLanguageOptions(),
                        selected = state.settings.appLanguage,
                        label = { stringResource(appLanguageLabelRes(it)) },
                        onSelect = viewModel::updateAppLanguage,
                        icon = MiuixIcons.Community,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_r18),
                        checked = state.settings.allowR18,
                        onCheckedChange = viewModel::updateAllowR18,
                        summary = stringResource(R.string.general_r18_desc),
                        icon = MiuixIcons.MoreCircle,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_ai_badge),
                        checked = state.settings.showAiBadge,
                        onCheckedChange = viewModel::updateShowAiBadge,
                        summary = stringResource(R.string.general_ai_badge_desc),
                        icon = MiuixIcons.Ok,
                    )
                }
            }}

            item { Section(stringResource(R.string.general_section_interaction)) {
                ElevatedPanel {
                    SettingSwitchRow(
                        title = stringResource(R.string.general_smooth),
                        checked = state.settings.smoothTransitions,
                        onCheckedChange = viewModel::updateSmoothTransitions,
                        summary = stringResource(R.string.general_smooth_desc),
                        icon = MiuixIcons.Timer,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_notch),
                        checked = state.settings.notchOptimization,
                        onCheckedChange = viewModel::updateNotchOptimization,
                        summary = stringResource(R.string.general_notch_desc),
                        icon = MiuixIcons.SelectAll,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_swipe),
                        checked = state.settings.swipeToSwitchWorks,
                        onCheckedChange = viewModel::updateSwipeToSwitchWorks,
                        summary = stringResource(R.string.general_swipe_desc),
                        icon = MiuixIcons.Sort,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_double_back),
                        checked = state.settings.doubleBackToExit,
                        onCheckedChange = viewModel::updateDoubleBackToExit,
                        summary = stringResource(R.string.general_double_back_desc),
                        icon = MiuixIcons.ChevronForward,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_secure),
                        checked = state.settings.secureWindow,
                        onCheckedChange = viewModel::updateSecureWindow,
                        summary = stringResource(R.string.general_secure_desc),
                        icon = MiuixIcons.Lock,
                    )
                }
            }}

            item { Section(stringResource(R.string.app_lock_section_title)) {
                ElevatedPanel {
                    SettingLinkRow(
                        title = stringResource(R.string.app_lock_enable),
                        onClick = { viewModel.openAppLockSetup() },
                        summary = if (state.settings.appLockEnabled)
                            stringResource(R.string.app_lock_enabled)
                        else
                            stringResource(R.string.app_lock_disabled),
                        icon = MiuixIcons.Lock,
                    )
                }
            }}

            item { Section(stringResource(R.string.general_section_startup)) {
                ElevatedPanel {
                    SettingDropdownRow(
                        title = stringResource(R.string.general_startup_screen),
                        summary = stringResource(R.string.general_startup_screen_desc),
                        values = listOf("home", "ranking", "bookmarks", "search", "more"),
                        selected = state.settings.startupScreen,
                        label = { startupLabel(it) },
                        onSelect = viewModel::updateStartupScreen,
                        icon = MiuixIcons.VerticalSplit,
                    )
                }
            }}

            item { Section(stringResource(R.string.general_section_font)) {
                ElevatedPanel {
                    SettingDropdownRow(
                        title = stringResource(R.string.general_font),
                        summary = stringResource(R.string.general_font_desc),
                        values = appFontOptions(),
                        selected = state.settings.appFont,
                        label = { stringResource(appFontLabelRes(it)) },
                        onSelect = viewModel::updateAppFont,
                        icon = MiuixIcons.More,
                    )
                }
            }}
        }
    }
}

@Composable
private fun startupLabel(value: String): String {
    return when (value) {
        "ranking" -> stringResource(R.string.nav_ranking)
        "bookmarks" -> stringResource(R.string.nav_bookmarks)
        "search" -> stringResource(R.string.nav_search)
        "more" -> stringResource(R.string.nav_more)
        else -> stringResource(R.string.nav_home)
    }
}
