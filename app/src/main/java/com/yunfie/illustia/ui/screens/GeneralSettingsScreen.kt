package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.appFontLabelRes
import com.yunfie.illustia.settings.appFontOptions
import com.yunfie.illustia.settings.appHapticLabel
import com.yunfie.illustia.settings.appHapticOptions
import com.yunfie.illustia.settings.appLanguageLabelRes
import com.yunfie.illustia.settings.appLanguageOptions
import com.yunfie.illustia.settings.appThemeLabel
import com.yunfie.illustia.settings.appThemeOptions
import com.yunfie.illustia.settings.isDynamicColorAvailable
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.ThemeSwitchSettingRow
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
    var showAmoledWarningDialog by remember { mutableStateOf(false) }
    val dynamicColorAvailable = isDynamicColorAvailable()

    if (showAmoledWarningDialog) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.general_experimental_feature),
            summary = stringResource(R.string.general_amoled_warning_desc),
            confirmText = stringResource(R.string.action_enable),
            destructive = false,
            onConfirm = {
                viewModel.updateAmoledMode(true)
                showAmoledWarningDialog = false
            },
            onDismiss = { showAmoledWarningDialog = false },
        )
    }

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
                    )
                    DividerLine()
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
                    DividerLine()
                    ThemeSwitchSettingRow(
                        title = stringResource(R.string.general_dynamic_color),
                        checked = state.settings.useDynamicColor,
                        onCheckedChange = viewModel::updateUseDynamicColor,
                        summary = if (dynamicColorAvailable) {
                            stringResource(R.string.general_dynamic_color_desc)
                        } else {
                            stringResource(R.string.general_dynamic_color_unsupported_desc)
                        },
                        enabled = dynamicColorAvailable,
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.general_language),
                        summary = stringResource(R.string.general_language_desc),
                        values = appLanguageOptions(),
                        selected = state.settings.appLanguage,
                        label = { stringResource(appLanguageLabelRes(it)) },
                        onSelect = viewModel::updateAppLanguage,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_r18),
                        checked = state.settings.allowR18,
                        onCheckedChange = viewModel::updateAllowR18,
                        summary = stringResource(R.string.general_r18_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_ai_badge),
                        checked = state.settings.showAiBadge,
                        onCheckedChange = viewModel::updateShowAiBadge,
                        summary = stringResource(R.string.general_ai_badge_desc),
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
                    )
                    DividerLine()
                    SettingDropdownRow(
                        title = stringResource(R.string.general_haptics),
                        summary = stringResource(R.string.general_haptics_desc),
                        values = appHapticOptions(),
                        selected = state.settings.hapticMode,
                        label = { appHapticLabel(it) },
                        onSelect = viewModel::updateHapticMode,
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_notch),
                        checked = state.settings.notchOptimization,
                        onCheckedChange = viewModel::updateNotchOptimization,
                        summary = stringResource(R.string.general_notch_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_swipe),
                        checked = state.settings.swipeToSwitchWorks,
                        onCheckedChange = viewModel::updateSwipeToSwitchWorks,
                        summary = stringResource(R.string.general_swipe_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_auto_load_more),
                        checked = state.settings.autoLoadMore,
                        onCheckedChange = viewModel::updateAutoLoadMore,
                        summary = stringResource(R.string.general_auto_load_more_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_double_back),
                        checked = state.settings.doubleBackToExit,
                        onCheckedChange = viewModel::updateDoubleBackToExit,
                        summary = stringResource(R.string.general_double_back_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_secure),
                        checked = state.settings.secureWindow,
                        onCheckedChange = viewModel::updateSecureWindow,
                        summary = stringResource(R.string.general_secure_desc),
                    )
                    DividerLine()
                    SettingSwitchRow(
                        title = stringResource(R.string.general_user_profile_bottom_sheet),
                        checked = state.settings.userProfileBottomSheetEnabled,
                        onCheckedChange = viewModel::updateUserProfileBottomSheetEnabled,
                        summary = stringResource(R.string.general_user_profile_bottom_sheet_desc),
                    )
                    DividerLine()
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
