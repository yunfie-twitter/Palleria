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
import com.yunfie.illustia.settings.appLanguageLabelRes
import com.yunfie.illustia.settings.appLanguageOptions
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
                        title = stringResource(R.string.general_language),
                        summary = stringResource(R.string.general_language_desc),
                        values = appLanguageOptions(),
                        selected = state.settings.appLanguage,
                        label = { stringResource(appLanguageLabelRes(it)) },
                        onSelect = viewModel::updateAppLanguage,
                    )
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_r18), state.settings.allowR18, viewModel::updateAllowR18, stringResource(R.string.general_r18_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_ai_badge), state.settings.showAiBadge, viewModel::updateShowAiBadge, stringResource(R.string.general_ai_badge_desc))
                }
            }}

            item { Section(stringResource(R.string.general_section_interaction)) {
                ElevatedPanel {
                    SettingSwitchRow(stringResource(R.string.general_smooth), state.settings.smoothTransitions, viewModel::updateSmoothTransitions, stringResource(R.string.general_smooth_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_notch), state.settings.notchOptimization, viewModel::updateNotchOptimization, stringResource(R.string.general_notch_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_swipe), state.settings.swipeToSwitchWorks, viewModel::updateSwipeToSwitchWorks, stringResource(R.string.general_swipe_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_double_back), state.settings.doubleBackToExit, viewModel::updateDoubleBackToExit, stringResource(R.string.general_double_back_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.general_secure), state.settings.secureWindow, viewModel::updateSecureWindow, stringResource(R.string.general_secure_desc))
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
