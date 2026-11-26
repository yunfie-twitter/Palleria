package com.yunfie.illustia.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.data.DataSettingsActions
import java.time.LocalDate

/**
 * Android 側の糊。エクスポート/インポートのファイル選択（SAF）だけを担い、
 * 画面本体は共有モジュールの DataSettingsScreen が描画する。
 */
@Composable
fun AndroidDataSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri -> uri?.let { viewModel.exportManagedData(it.toString()) } },
        )
    val importLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> pendingImportUri = uri },
        )

    DataSettingsScreen(
        settings = settings,
        viewModel = viewModel.asDataSettingsActions(),
        onBack = onBack,
        onExportData = { exportLauncher.launch("illustia-data-${LocalDate.now()}.json") },
        onImportData = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
        importUri = pendingImportUri?.toString(),
        onImportConfirmed = { pendingImportUri = null },
        onImportDismissed = { pendingImportUri = null },
    )
}

@Composable
private fun IllustiaViewModel.asDataSettingsActions(): DataSettingsActions =
    remember(this) {
        object : DataSettingsActions {
            override fun updateSaveViewHistory(value: Boolean) = this@asDataSettingsActions.updateSaveViewHistory(value)

            override fun updateSaveSearchHistory(value: Boolean) = this@asDataSettingsActions.updateSaveSearchHistory(value)

            override fun updateSendTelemetry(value: Boolean) = this@asDataSettingsActions.updateSendTelemetry(value)

            override fun exportManagedData(uri: String) = this@asDataSettingsActions.exportManagedData(uri)

            override fun importManagedData(uri: String) = this@asDataSettingsActions.importManagedData(uri)

            override fun clearAppCache() = this@asDataSettingsActions.clearAppCache()
        }
    }
