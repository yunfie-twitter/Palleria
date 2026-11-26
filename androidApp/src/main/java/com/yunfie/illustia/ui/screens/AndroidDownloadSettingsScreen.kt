package com.yunfie.illustia.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.nativebridge.NativeImageStore
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.download.DownloadSettingsActions

/**
 * Android 側の糊。フォルダ選択（SAF）と保存先ラベルの読み出しだけを担い、
 * 画面本体は共有モジュールの DownloadSettingsScreen が描画する。
 */
@Composable
fun AndroidDownloadSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
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

    DownloadSettingsScreen(
        settings = settings,
        viewModel = viewModel.asDownloadSettingsActions(),
        onBack = onBack,
        saveLocation = saveLocation,
        onPickSaveLocation = { folderPicker.launch(null) },
    )
}

@Composable
private fun IllustiaViewModel.asDownloadSettingsActions(): DownloadSettingsActions =
    remember(this) {
        object : DownloadSettingsActions {
            override fun updateDownloadFolderByArtist(value: Boolean) = this@asDownloadSettingsActions.updateDownloadFolderByArtist(value)

            override fun updateDownloadFolderByWork(value: Boolean) = this@asDownloadSettingsActions.updateDownloadFolderByWork(value)

            override fun updateDuplicateSaveMode(value: String) = this@asDownloadSettingsActions.updateDuplicateSaveMode(value)

            override fun updateUgoiraSaveFormat(value: String) = this@asDownloadSettingsActions.updateUgoiraSaveFormat(value)

            override fun updateSimultaneousDownloads(value: Int) = this@asDownloadSettingsActions.updateSimultaneousDownloads(value)

            override fun updateEmbedMetadata(enabled: Boolean) = this@asDownloadSettingsActions.updateEmbedMetadata(enabled)
        }
    }
