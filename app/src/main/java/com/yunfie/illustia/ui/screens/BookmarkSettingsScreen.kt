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
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BookmarkSettingsScreen(
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
                title = stringResource(R.string.bookmark_settings_title),
                largeTitle = stringResource(R.string.bookmark_settings_title),
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
            item { Section(stringResource(R.string.bookmark_section_behavior)) {
                ElevatedPanel {
                    SettingSwitchRow(stringResource(R.string.bookmark_private_default), state.settings.privateBookmarkDefault, viewModel::updatePrivateBookmarkDefault, stringResource(R.string.bookmark_private_default_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.bookmark_follow_on_like), state.settings.followOnLike, viewModel::updateFollowOnLike, stringResource(R.string.bookmark_follow_on_like_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.bookmark_auto_tag), state.settings.autoTagOnBookmark, viewModel::updateAutoTagOnBookmark, stringResource(R.string.bookmark_auto_tag_desc))
                }
            }}

            item { Section(stringResource(R.string.bookmark_section_sync)) {
                ElevatedPanel {
                    SettingSwitchRow(stringResource(R.string.bookmark_download_on_bookmark), state.settings.autoDownloadOnBookmark, viewModel::updateAutoDownloadOnBookmark, stringResource(R.string.bookmark_download_on_bookmark_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.bookmark_bookmark_on_download), state.settings.autoBookmarkOnDownload, viewModel::updateAutoBookmarkOnDownload, stringResource(R.string.bookmark_bookmark_on_download_desc))
                }
            }}

            item { Section(stringResource(R.string.bookmark_section_confirm)) {
                ElevatedPanel {
                    SettingSwitchRow(stringResource(R.string.bookmark_confirm_on_long_press), state.settings.confirmOnLongPressSave, viewModel::updateConfirmOnLongPressSave, stringResource(R.string.bookmark_confirm_on_long_press_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.bookmark_skip_confirm_on_detail), state.settings.skipConfirmOnDetailSave, viewModel::updateSkipConfirmOnDetailSave, stringResource(R.string.bookmark_skip_confirm_on_detail_desc))
                }
            }}
        }
    }
}
