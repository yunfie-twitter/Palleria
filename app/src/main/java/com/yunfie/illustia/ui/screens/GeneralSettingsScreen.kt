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
fun GeneralSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val dynamicColorAvailable = isDynamicColorAvailable()
    val context = LocalContext.current
    val hapticsSupported = remember(context) { isAppHapticsSupported(context) }
    val effectiveHapticMode = effectiveAppHapticMode(state.settings.hapticMode, hapticsSupported)
    val hapticsEnabled = effectiveHapticMode != AppHapticMode.Off
    var showAmoledWarningDialog by remember { mutableStateOf(false) }

    val amoledFlag = state.settings.isFeatureEnabled(FeatureFlag.AmoledTheme)
    val userProfileBottomSheetFlag = state.settings.isFeatureEnabled(FeatureFlag.UserProfileBottomSheet)
    val customAppIconFlag = state.settings.isFeatureEnabled(FeatureFlag.CustomAppIcon)
    val artworkDynamicThemeFlag = state.settings.isFeatureEnabled(FeatureFlag.ArtworkDynamicTheme)
    val navigationCustomizationFlag = state.settings.isFeatureEnabled(FeatureFlag.NavigationCustomization)
    val shortsFeedFlag = state.settings.isFeatureEnabled(FeatureFlag.ShortsFeed)
    val hideHomeNovelButtonFlag = state.settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton)

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
                        if (amoledFlag) {
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
                        }
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
                        if (userProfileBottomSheetFlag) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(R.string.general_user_profile_bottom_sheet),
                                checked = state.settings.userProfileBottomSheetEnabled,
                                onCheckedChange = viewModel::updateUserProfileBottomSheetEnabled,
                                summary = stringResource(R.string.general_user_profile_bottom_sheet_desc),
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
                    }
                }
            }

            if (navigationCustomizationFlag || shortsFeedFlag || hideHomeNovelButtonFlag) {
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

                        if (navigationCustomizationFlag) {
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

            item {
                Section(stringResource(R.string.general_section_interaction)) {
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
                            title = stringResource(R.string.general_clipboard_auto_detect),
                            checked = state.settings.autoDetectClipboard,
                            onCheckedChange = viewModel::updateClipboardAutoDetect,
                            summary = stringResource(R.string.general_clipboard_auto_detect_desc),
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
                            title = stringResource(R.string.settings_hide_ai_works),
                            checked = state.settings.hideAiWorks,
                            onCheckedChange = viewModel::updateHideAiWorks,
                            summary = stringResource(R.string.settings_hide_ai_works_desc),
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
                    }
                }
            }

            item {
                Section(stringResource(R.string.general_section_font)) {
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
