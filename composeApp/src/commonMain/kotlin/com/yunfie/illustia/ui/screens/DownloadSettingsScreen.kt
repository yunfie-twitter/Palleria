package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.data_items_count
import com.yunfie.illustia.resources.download_section_rules
import com.yunfie.illustia.resources.download_section_storage
import com.yunfie.illustia.resources.download_settings_title
import com.yunfie.illustia.resources.flag_embed_metadata_desc
import com.yunfie.illustia.resources.flag_embed_metadata_title
import com.yunfie.illustia.resources.image_default_save_location
import com.yunfie.illustia.resources.image_default_save_location_fallback
import com.yunfie.illustia.resources.image_download_folder_by_artist
import com.yunfie.illustia.resources.image_download_folder_by_artist_desc
import com.yunfie.illustia.resources.image_download_folder_by_work
import com.yunfie.illustia.resources.image_download_folder_by_work_desc
import com.yunfie.illustia.resources.image_duplicate_mode_always
import com.yunfie.illustia.resources.image_duplicate_mode_overwrite
import com.yunfie.illustia.resources.image_duplicate_mode_skip
import com.yunfie.illustia.resources.image_duplicate_save_mode
import com.yunfie.illustia.resources.image_duplicate_save_mode_desc
import com.yunfie.illustia.resources.image_simultaneous_downloads
import com.yunfie.illustia.resources.image_ugoira_format_gif
import com.yunfie.illustia.resources.image_ugoira_format_mp4
import com.yunfie.illustia.resources.image_ugoira_save_format
import com.yunfie.illustia.resources.image_ugoira_save_format_desc
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.screens.download.DownloadSettingsActions
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DownloadSettingsScreen(
    settings: AppSettings,
    viewModel: DownloadSettingsActions,
    onBack: () -> Unit,
    saveLocation: String,
    onPickSaveLocation: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.download_settings_title),
                largeTitle = stringResource(Res.string.download_settings_title),
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
                Section(stringResource(Res.string.download_section_storage)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.image_download_folder_by_artist),
                            checked = settings.downloadFolderByArtist,
                            onCheckedChange = viewModel::updateDownloadFolderByArtist,
                            summary = stringResource(Res.string.image_download_folder_by_artist_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.image_download_folder_by_work),
                            checked = settings.downloadFolderByWork,
                            onCheckedChange = viewModel::updateDownloadFolderByWork,
                            summary = stringResource(Res.string.image_download_folder_by_work_desc),
                        )
                        DividerLine()
                        ArrowPreference(
                            title = stringResource(Res.string.image_default_save_location),
                            summary = saveLocation.ifBlank { stringResource(Res.string.image_default_save_location_fallback) },
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onPickSaveLocation,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.download_section_rules)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_duplicate_save_mode),
                            summary = stringResource(Res.string.image_duplicate_save_mode_desc),
                            values = listOf("skip", "overwrite", "always"),
                            selected = settings.duplicateSaveMode,
                            label = { duplicateSaveModeLabel(it) },
                            onSelect = viewModel::updateDuplicateSaveMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_ugoira_save_format),
                            summary = stringResource(Res.string.image_ugoira_save_format_desc),
                            values = listOf("mp4", "gif"),
                            selected = settings.ugoiraSaveFormat,
                            label = { ugoiraSaveFormatLabel(it) },
                            onSelect = viewModel::updateUgoiraSaveFormat,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(Res.string.image_simultaneous_downloads),
                            values = listOf(1, 2, 3, 4),
                            selected = settings.simultaneousDownloads.coerceIn(1, 4),
                            label = { stringResource(Res.string.data_items_count, it) },
                            onSelect = viewModel::updateSimultaneousDownloads,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.flag_embed_metadata_title),
                            checked = settings.embedMetadata,
                            onCheckedChange = viewModel::updateEmbedMetadata,
                            summary = stringResource(Res.string.flag_embed_metadata_desc),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun duplicateSaveModeLabel(value: String): String =
    when (value) {
        "overwrite" -> stringResource(Res.string.image_duplicate_mode_overwrite)
        "always" -> stringResource(Res.string.image_duplicate_mode_always)
        else -> stringResource(Res.string.image_duplicate_mode_skip)
    }

@Composable
private fun ugoiraSaveFormatLabel(value: String): String =
    when (value) {
        "gif" -> stringResource(Res.string.image_ugoira_format_gif)
        else -> stringResource(Res.string.image_ugoira_format_mp4)
    }
