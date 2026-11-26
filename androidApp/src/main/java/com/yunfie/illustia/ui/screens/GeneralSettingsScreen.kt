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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_cancel
import com.yunfie.illustia.resources.app_lock_disabled
import com.yunfie.illustia.resources.app_lock_enable
import com.yunfie.illustia.resources.app_lock_enabled
import com.yunfie.illustia.resources.app_lock_section_title
import com.yunfie.illustia.resources.experimental_app_icon
import com.yunfie.illustia.resources.experimental_app_icon_cat
import com.yunfie.illustia.resources.experimental_app_icon_default
import com.yunfie.illustia.resources.experimental_app_icon_desc
import com.yunfie.illustia.resources.experimental_artwork_theme
import com.yunfie.illustia.resources.experimental_artwork_theme_desc
import com.yunfie.illustia.resources.experimental_navigation_section
import com.yunfie.illustia.resources.general_auto_load_more
import com.yunfie.illustia.resources.general_auto_load_more_desc
import com.yunfie.illustia.resources.general_clipboard_auto_detect
import com.yunfie.illustia.resources.general_clipboard_auto_detect_desc
import com.yunfie.illustia.resources.general_double_back
import com.yunfie.illustia.resources.general_double_back_desc
import com.yunfie.illustia.resources.general_dynamic_color
import com.yunfie.illustia.resources.general_dynamic_color_desc
import com.yunfie.illustia.resources.general_dynamic_color_unsupported_desc
import com.yunfie.illustia.resources.general_font
import com.yunfie.illustia.resources.general_font_desc
import com.yunfie.illustia.resources.general_haptics
import com.yunfie.illustia.resources.general_haptics_desc
import com.yunfie.illustia.resources.general_haptics_rich
import com.yunfie.illustia.resources.general_haptics_rich_desc
import com.yunfie.illustia.resources.general_haptics_unsupported_desc
import com.yunfie.illustia.resources.general_language
import com.yunfie.illustia.resources.general_language_desc
import com.yunfie.illustia.resources.general_notch
import com.yunfie.illustia.resources.general_notch_desc
import com.yunfie.illustia.resources.general_r18
import com.yunfie.illustia.resources.general_r18_desc
import com.yunfie.illustia.resources.general_r18g
import com.yunfie.illustia.resources.general_r18g_desc
import com.yunfie.illustia.resources.general_section_display
import com.yunfie.illustia.resources.general_section_feedback
import com.yunfie.illustia.resources.general_section_filter
import com.yunfie.illustia.resources.general_section_gestures
import com.yunfie.illustia.resources.general_secure
import com.yunfie.illustia.resources.general_secure_desc
import com.yunfie.illustia.resources.general_settings_title
import com.yunfie.illustia.resources.general_smooth
import com.yunfie.illustia.resources.general_smooth_desc
import com.yunfie.illustia.resources.general_theme
import com.yunfie.illustia.resources.general_theme_desc
import com.yunfie.illustia.resources.navigation_settings_summary
import com.yunfie.illustia.resources.navigation_settings_title
import com.yunfie.illustia.resources.privacy_mode_title
import com.yunfie.illustia.resources.privacy_settings_disabled
import com.yunfie.illustia.resources.privacy_settings_enabled
import com.yunfie.illustia.resources.settings_hide_ai_works
import com.yunfie.illustia.resources.settings_hide_ai_works_desc
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.settings.AppSettings
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
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GeneralSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenNavigationSettings: () -> Unit = { viewModel.openNavigationSettings() },
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicColorAvailable = isDynamicColorAvailable()
    val context = LocalContext.current
    val hapticsSupported = remember(context) { isAppHapticsSupported(context) }
    val effectiveHapticMode = effectiveAppHapticMode(settings.hapticMode, hapticsSupported)
    val hapticsEnabled = effectiveHapticMode != AppHapticMode.Off

    val artworkDynamicThemeFlag = settings.isFeatureEnabled(FeatureFlag.ArtworkDynamicTheme)
    val navigationCustomizationFlag = settings.isFeatureEnabled(FeatureFlag.NavigationCustomization)
    val shortsFeedFlag = settings.isFeatureEnabled(FeatureFlag.ShortsFeed)
    val hideHomeNovelButtonFlag = settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton)

    val hasNavigationSettings = navigationCustomizationFlag || shortsFeedFlag || hideHomeNovelButtonFlag

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.general_settings_title),
                largeTitle = stringResource(Res.string.general_settings_title),
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
                Section(stringResource(Res.string.general_section_display)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(Res.string.general_theme),
                            summary = stringResource(Res.string.general_theme_desc),
                            values = appThemeOptions(),
                            selected = settings.themeMode,
                            label = { appThemeLabel(it) },
                            onSelect = viewModel::updateThemeMode,
                        )
                        DividerLine()
                        ThemeSwitchSettingRow(
                            title = stringResource(Res.string.general_dynamic_color),
                            checked = settings.useDynamicColor,
                            onCheckedChange = viewModel::updateUseDynamicColor,
                            summary =
                                if (dynamicColorAvailable) {
                                    stringResource(Res.string.general_dynamic_color_desc)
                                } else {
                                    stringResource(Res.string.general_dynamic_color_unsupported_desc)
                                },
                            enabled = dynamicColorAvailable,
                        )
                        if (artworkDynamicThemeFlag) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.experimental_artwork_theme),
                                checked = settings.artworkThemeEnabled,
                                onCheckedChange = viewModel::updateArtworkThemeEnabled,
                                summary = stringResource(Res.string.experimental_artwork_theme_desc),
                            )
                        }
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.experimental_app_icon),
                            summary = stringResource(Res.string.experimental_app_icon_desc),
                            selected = settings.appIconVariant,
                            values = listOf("default", "cat"),
                            label = { variant ->
                                when (variant) {
                                    "cat" -> stringResource(Res.string.experimental_app_icon_cat)
                                    else -> stringResource(Res.string.experimental_app_icon_default)
                                }
                            },
                            onSelect = viewModel::updateAppIconVariant,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.general_language),
                            summary = stringResource(Res.string.general_language_desc),
                            values = appLanguageOptions(),
                            selected = settings.appLanguage,
                            label = { stringResource(appLanguageLabelRes(it)) },
                            onSelect = viewModel::updateAppLanguage,
                            dialogButtonString = stringResource(Res.string.action_cancel),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.general_font),
                            summary = stringResource(Res.string.general_font_desc),
                            values = appFontOptions(),
                            selected = settings.appFont,
                            label = { stringResource(appFontLabelRes(it)) },
                            onSelect = viewModel::updateAppFont,
                        )
                    }
                }
            }

            if (hasNavigationSettings) {
                item {
                    Section(stringResource(Res.string.experimental_navigation_section)) {
                        ElevatedPanel {
                            SettingLinkRow(
                                title = stringResource(Res.string.navigation_settings_title),
                                summary = stringResource(Res.string.navigation_settings_summary),
                                onClick = onOpenNavigationSettings,
                            )
                        }
                    }
                }
            }

            item {
                Section(stringResource(Res.string.general_section_filter)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_r18),
                            checked = settings.allowR18,
                            onCheckedChange = viewModel::updateAllowR18,
                            summary = stringResource(Res.string.general_r18_desc),
                        )
                        if (settings.allowR18) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.general_r18g),
                                checked = settings.allowR18G,
                                onCheckedChange = viewModel::updateAllowR18G,
                                summary = stringResource(Res.string.general_r18g_desc),
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.settings_hide_ai_works),
                            checked = settings.hideAiWorks,
                            onCheckedChange = viewModel::updateHideAiWorks,
                            summary = stringResource(Res.string.settings_hide_ai_works_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.general_section_gestures)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_auto_load_more),
                            checked = settings.autoLoadMore,
                            onCheckedChange = viewModel::updateAutoLoadMore,
                            summary = stringResource(Res.string.general_auto_load_more_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_double_back),
                            checked = settings.doubleBackToExit,
                            onCheckedChange = viewModel::updateDoubleBackToExit,
                            summary = stringResource(Res.string.general_double_back_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_clipboard_auto_detect),
                            checked = settings.autoDetectClipboard,
                            onCheckedChange = viewModel::updateClipboardAutoDetect,
                            summary = stringResource(Res.string.general_clipboard_auto_detect_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.general_section_feedback)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_smooth),
                            checked = settings.smoothTransitions,
                            onCheckedChange = viewModel::updateSmoothTransitions,
                            summary = stringResource(Res.string.general_smooth_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_haptics),
                            summary =
                                stringResource(
                                    if (hapticsSupported) {
                                        Res.string.general_haptics_desc
                                    } else {
                                        Res.string.general_haptics_unsupported_desc
                                    },
                                ),
                            checked = hapticsEnabled,
                            onCheckedChange = viewModel::updateHapticsEnabled,
                            enabled = hapticsSupported,
                        )
                        if (hapticsEnabled) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.general_haptics_rich),
                                summary = stringResource(Res.string.general_haptics_rich_desc),
                                checked = settings.hapticMode == AppHapticMode.Rich.value,
                                onCheckedChange = { rich ->
                                    viewModel.updateHapticMode(
                                        if (rich) AppHapticMode.Rich.value else AppHapticMode.Clear.value,
                                    )
                                },
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_notch),
                            checked = settings.notchOptimization,
                            onCheckedChange = viewModel::updateNotchOptimization,
                            summary = stringResource(Res.string.general_notch_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.app_lock_section_title)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(Res.string.app_lock_enable),
                            onClick = { viewModel.openAppLockSetup() },
                            summary =
                                if (settings.appLockEnabled) {
                                    stringResource(Res.string.app_lock_enabled)
                                } else {
                                    stringResource(Res.string.app_lock_disabled)
                                },
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(Res.string.privacy_mode_title),
                            onClick = { viewModel.openPrivacyModeSettings() },
                            summary =
                                if (settings.privacyModeEnabled) {
                                    stringResource(Res.string.privacy_settings_enabled)
                                } else {
                                    stringResource(Res.string.privacy_settings_disabled)
                                },
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.general_secure),
                            checked = settings.secureWindow,
                            onCheckedChange = viewModel::updateSecureWindow,
                            summary = stringResource(Res.string.general_secure_desc),
                        )
                    }
                }
            }
        }
    }
}
