package com.yunfie.illustia.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DownloadSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val imageStore = remember(context) { NativeImageStore(context.applicationContext) }
    var saveLocation by remember(imageStore) { mutableStateOf(imageStore.currentPathLabel()) }

    val folderPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                imageStore.persistTreeUri(it)
                saveLocation = imageStore.currentPathLabel()
            }
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.download_settings_title),
                largeTitle = stringResource(R.string.download_settings_title),
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
                Section(stringResource(R.string.download_section_storage)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.image_download_folder_by_artist),
                            checked = state.settings.downloadFolderByArtist,
                            onCheckedChange = viewModel::updateDownloadFolderByArtist,
                            summary = stringResource(R.string.image_download_folder_by_artist_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.image_download_folder_by_work),
                            checked = state.settings.downloadFolderByWork,
                            onCheckedChange = viewModel::updateDownloadFolderByWork,
                            summary = stringResource(R.string.image_download_folder_by_work_desc),
                        )
                        DividerLine()
                        ArrowPreference(
                            title = stringResource(R.string.image_default_save_location),
                            summary = saveLocation.ifBlank { stringResource(R.string.image_default_save_location_fallback) },
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { folderPicker.launch(null) },
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.download_section_rules)) {
                    ElevatedPanel {
                        SettingDropdownRow(
                            title = stringResource(R.string.image_duplicate_save_mode),
                            summary = stringResource(R.string.image_duplicate_save_mode_desc),
                            values = listOf("skip", "overwrite", "always"),
                            selected = state.settings.duplicateSaveMode,
                            label = { duplicateSaveModeLabel(it) },
                            onSelect = viewModel::updateDuplicateSaveMode,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_ugoira_save_format),
                            summary = stringResource(R.string.image_ugoira_save_format_desc),
                            values = listOf("mp4", "gif"),
                            selected = state.settings.ugoiraSaveFormat,
                            label = { ugoiraSaveFormatLabel(it) },
                            onSelect = viewModel::updateUgoiraSaveFormat,
                        )
                        DividerLine()
                        SettingDropdownRow(
                            title = stringResource(R.string.image_simultaneous_downloads),
                            values = listOf(1, 2, 3, 4),
                            selected = state.settings.simultaneousDownloads.coerceIn(1, 4),
                            label = { stringResource(R.string.data_items_count, it) },
                            onSelect = viewModel::updateSimultaneousDownloads,
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
        "overwrite" -> stringResource(R.string.image_duplicate_mode_overwrite)
        "always" -> stringResource(R.string.image_duplicate_mode_always)
        else -> stringResource(R.string.image_duplicate_mode_skip)
    }

@Composable
private fun ugoiraSaveFormatLabel(value: String): String =
    when (value) {
        "gif" -> stringResource(R.string.image_ugoira_format_gif)
        else -> stringResource(R.string.image_ugoira_format_mp4)
    }
