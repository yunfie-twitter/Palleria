package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.labelResId
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.bookmark_auto_tag
import com.yunfie.illustia.resources.bookmark_auto_tag_desc
import com.yunfie.illustia.resources.bookmark_bookmark_on_download
import com.yunfie.illustia.resources.bookmark_bookmark_on_download_desc
import com.yunfie.illustia.resources.bookmark_confirm_on_long_press
import com.yunfie.illustia.resources.bookmark_confirm_on_long_press_desc
import com.yunfie.illustia.resources.bookmark_download_on_bookmark
import com.yunfie.illustia.resources.bookmark_download_on_bookmark_desc
import com.yunfie.illustia.resources.bookmark_follow_on_like
import com.yunfie.illustia.resources.bookmark_follow_on_like_desc
import com.yunfie.illustia.resources.bookmark_private_default
import com.yunfie.illustia.resources.bookmark_private_default_desc
import com.yunfie.illustia.resources.bookmark_section_behavior
import com.yunfie.illustia.resources.bookmark_section_confirm
import com.yunfie.illustia.resources.bookmark_section_sync
import com.yunfie.illustia.resources.bookmark_settings_title
import com.yunfie.illustia.resources.bookmark_skip_confirm_on_detail
import com.yunfie.illustia.resources.bookmark_skip_confirm_on_detail_desc
import com.yunfie.illustia.resources.follow_visibility
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.screens.bookmark.BookmarkSettingsActions
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BookmarkSettingsScreen(
    settings: AppSettings,
    viewModel: BookmarkSettingsActions,
    onBack: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.bookmark_settings_title),
                largeTitle = stringResource(Res.string.bookmark_settings_title),
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
                Section(stringResource(Res.string.bookmark_section_behavior)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_private_default),
                            settings.privateBookmarkDefault,
                            viewModel::updatePrivateBookmarkDefault,
                            stringResource(Res.string.bookmark_private_default_desc),
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.follow_visibility),
                            selected = settings.followRestrict,
                            values = Restrict.entries,
                            label = { stringResource(it.labelResId) },
                            onSelect = { restrict ->
                                viewModel.updateFollowRestrict(restrict)
                                viewModel.refreshTimeline(forceRefresh = true)
                                viewModel.refreshFollowingUsers(forceRefresh = true)
                            },
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_follow_on_like),
                            settings.followOnLike,
                            viewModel::updateFollowOnLike,
                            stringResource(Res.string.bookmark_follow_on_like_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_auto_tag),
                            settings.autoTagOnBookmark,
                            viewModel::updateAutoTagOnBookmark,
                            stringResource(Res.string.bookmark_auto_tag_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.bookmark_section_sync)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_download_on_bookmark),
                            settings.autoDownloadOnBookmark,
                            viewModel::updateAutoDownloadOnBookmark,
                            stringResource(Res.string.bookmark_download_on_bookmark_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_bookmark_on_download),
                            settings.autoBookmarkOnDownload,
                            viewModel::updateAutoBookmarkOnDownload,
                            stringResource(Res.string.bookmark_bookmark_on_download_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.bookmark_section_confirm)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_confirm_on_long_press),
                            settings.confirmOnLongPressSave,
                            viewModel::updateConfirmOnLongPressSave,
                            stringResource(Res.string.bookmark_confirm_on_long_press_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.bookmark_skip_confirm_on_detail),
                            settings.skipConfirmOnDetailSave,
                            viewModel::updateSkipConfirmOnDetailSave,
                            stringResource(Res.string.bookmark_skip_confirm_on_detail_desc),
                        )
                    }
                }
            }
        }
    }
}
