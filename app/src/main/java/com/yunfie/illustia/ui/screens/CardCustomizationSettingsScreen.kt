package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CardCustomizationSettingsScreen(
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
                title = stringResource(R.string.card_customization_settings_title),
                largeTitle = stringResource(R.string.card_customization_settings_title),
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
                Section(stringResource(R.string.card_customization_section_elements)) {
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
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_r18),
                            checked = state.settings.showR18Badge,
                            onCheckedChange = viewModel::updateShowR18Badge,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.card_customization_section_actions)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_bookmark_button),
                            checked = state.settings.showCardBookmarkButton,
                            onCheckedChange = viewModel::updateShowCardBookmarkButton,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_double_tap_to_bookmark),
                            checked = state.settings.doubleTapToBookmark,
                            onCheckedChange = viewModel::updateDoubleTapToBookmark,
                            summary = stringResource(R.string.experimental_card_double_tap_to_bookmark_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.card_customization_section_related)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.experimental_card_related_r18),
                            checked = state.settings.showRelatedR18,
                            onCheckedChange = viewModel::updateShowRelatedR18,
                            summary = stringResource(R.string.experimental_card_related_r18_desc),
                            enabled = state.settings.showR18Badge,
                        )
                    }
                }
            }
        }
    }
}
