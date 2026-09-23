package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.appFontLabelRes
import com.yunfie.illustia.settings.appFontOptions
import com.yunfie.illustia.settings.appLanguageLabelRes
import com.yunfie.illustia.settings.appLanguageOptions
import com.yunfie.illustia.settings.appThemeLabel
import com.yunfie.illustia.settings.appThemeOptions
import com.yunfie.illustia.settings.effectiveAppHapticMode
import com.yunfie.illustia.settings.isDynamicColorAvailable
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.ThemeSwitchSettingRow
import com.yunfie.illustia.ui.components.isAppHapticsSupported
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GeneralSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenNavigationSettings: () -> Unit = { viewModel.openNavigationSettings() },
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicColorAvailable = isDynamicColorAvailable()
    val context = LocalContext.current
    val hapticsSupported = remember(context) { isAppHapticsSupported(context) }
    val effectiveHapticMode = effectiveAppHapticMode(state.settings.hapticMode, hapticsSupported)
    val hapticsEnabled = effectiveHapticMode != AppHapticMode.Off

    val customAppIconFlag = state.settings.isFeatureEnabled(FeatureFlag.CustomAppIcon)
    val artworkDynamicThemeFlag = state.settings.isFeatureEnabled(FeatureFlag.ArtworkDynamicTheme)
    val navigationCustomizationFlag = state.settings.isFeatureEnabled(FeatureFlag.NavigationCustomization)
    val shortsFeedFlag = state.settings.isFeatureEnabled(FeatureFlag.ShortsFeed)
    val hideHomeNovelButtonFlag = state.settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton)

    val hasNavigationSettings = navigationCustomizationFlag || shortsFeedFlag || hideHomeNovelButtonFlag

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
                            title = stringResource(R.string.general_dynamic_color),
                            checked = state.settings.useDynamicColor,
                            onCheckedChange = viewModel::updateUseDynamicColor,
                            summary =
                                if (dynamicColorAvailable) {
                                    stringResource(R.string.general_dynamic_color_desc)
                                } else {
                                    stringResource(R.string.general_dynamic_color_unsupported_desc)
                                },
                            enabled = dynamicColorAvailable,
                        )
                        if (artworkDynamicThemeFlag) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.experimental_artwork_theme),
                                checked = state.settings.artworkThemeEnabled,
                                onCheckedChange = viewModel::updateArtworkThemeEnabled,
                                summary = stringResource(R.string.experimental_artwork_theme_desc),
                            )
                        }
                        if (customAppIconFlag) {
                            DividerLine()
                            SettingDropdownRow(
                                title = stringResource(R.string.experimental_app_icon),
                                summary = stringResource(R.string.experimental_app_icon_desc),
                                selected = state.settings.appIconVariant,
                                values = listOf("default", "cat"),
                                label = { variant ->
                                    when (variant) {
                                        "cat" -> stringResource(R.string.experimental_app_icon_cat)
                                        else -> stringResource(R.string.experimental_app_icon_default)
                                    }
                                },
                                onSelect = viewModel::updateAppIconVariant,
                            )
                        }
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.general_language),
                            summary = stringResource(R.string.general_language_desc),
                            values = appLanguageOptions(),
                            selected = state.settings.appLanguage,
                            label = { stringResource(appLanguageLabelRes(it)) },
                            onSelect = viewModel::updateAppLanguage,
                            dialogButtonString = stringResource(R.string.action_cancel),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.general_font),
                            summary = stringResource(R.string.general_font_desc),
                            values = appFontOptions(),
                            selected = state.settings.appFont,
                            label = { stringResource(appFontLabelRes(it)) },
                            onSelect = viewModel::updateAppFont,
                        )
                    }
                }
            }

            if (hasNavigationSettings) {
                item {
                    Section(stringResource(R.string.experimental_navigation_section)) {
                        ElevatedPanel {
                            SettingLinkRow(
                                title = stringResource(R.string.navigation_settings_title),
                                summary = stringResource(R.string.navigation_settings_summary),
                                onClick = onOpenNavigationSettings,
                            )
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.general_section_filter)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.general_r18),
                            checked = state.settings.allowR18,
                            onCheckedChange = viewModel::updateAllowR18,
                            summary = stringResource(R.string.general_r18_desc),
                        )
                        if (state.settings.allowR18) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.general_r18g),
                                checked = state.settings.allowR18G,
                                onCheckedChange = viewModel::updateAllowR18G,
                                summary = stringResource(R.string.general_r18g_desc),
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_hide_ai_works),
                            checked = state.settings.hideAiWorks,
                            onCheckedChange = viewModel::updateHideAiWorks,
                            summary = stringResource(R.string.settings_hide_ai_works_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.general_section_gestures)) {
                    ElevatedPanel {
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
                            title = stringResource(R.string.general_clipboard_auto_detect),
                            checked = state.settings.autoDetectClipboard,
                            onCheckedChange = viewModel::updateClipboardAutoDetect,
                            summary = stringResource(R.string.general_clipboard_auto_detect_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.general_section_feedback)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.general_smooth),
                            checked = state.settings.smoothTransitions,
                            onCheckedChange = viewModel::updateSmoothTransitions,
                            summary = stringResource(R.string.general_smooth_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.general_haptics),
                            summary =
                                stringResource(
                                    if (hapticsSupported) {
                                        R.string.general_haptics_desc
                                    } else {
                                        R.string.general_haptics_unsupported_desc
                                    },
                                ),
                            checked = hapticsEnabled,
                            onCheckedChange = viewModel::updateHapticsEnabled,
                            enabled = hapticsSupported,
                        )
                        if (hapticsEnabled) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.general_haptics_rich),
                                summary = stringResource(R.string.general_haptics_rich_desc),
                                checked = state.settings.hapticMode == AppHapticMode.Rich.value,
                                onCheckedChange = { rich ->
                                    viewModel.updateHapticMode(
                                        if (rich) AppHapticMode.Rich.value else AppHapticMode.Clear.value,
                                    )
                                },
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.general_notch),
                            checked = state.settings.notchOptimization,
                            onCheckedChange = viewModel::updateNotchOptimization,
                            summary = stringResource(R.string.general_notch_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.app_lock_section_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(R.string.app_lock_enable),
                            onClick = { viewModel.openAppLockSetup() },
                            summary =
                                if (state.settings.appLockEnabled) {
                                    stringResource(R.string.app_lock_enabled)
                                } else {
                                    stringResource(R.string.app_lock_disabled)
                                },
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.privacy_mode_title),
                            onClick = { viewModel.openPrivacyModeSettings() },
                            summary =
                                if (state.settings.privacyModeEnabled) {
                                    stringResource(R.string.privacy_settings_enabled)
                                } else {
                                    stringResource(R.string.privacy_settings_disabled)
                                },
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.general_secure),
                            checked = state.settings.secureWindow,
                            onCheckedChange = viewModel::updateSecureWindow,
                            summary = stringResource(R.string.general_secure_desc),
                        )
                    }
                }
            }
        }
    }
}
