package com.yunfie.illustia.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_delete
import com.yunfie.illustia.resources.data_delete_cache
import com.yunfie.illustia.resources.data_delete_cache_desc
import com.yunfie.illustia.resources.data_export
import com.yunfie.illustia.resources.data_import
import com.yunfie.illustia.resources.data_import_confirm_desc
import com.yunfie.illustia.resources.data_import_confirm_title
import com.yunfie.illustia.resources.data_import_export_note
import com.yunfie.illustia.resources.data_save_search_history
import com.yunfie.illustia.resources.data_save_search_history_desc
import com.yunfie.illustia.resources.data_save_view_history
import com.yunfie.illustia.resources.data_save_view_history_desc
import com.yunfie.illustia.resources.data_section_cleanup
import com.yunfie.illustia.resources.data_section_history
import com.yunfie.illustia.resources.data_section_privacy_telemetry
import com.yunfie.illustia.resources.data_section_transfer
import com.yunfie.illustia.resources.data_send_telemetry
import com.yunfie.illustia.resources.data_send_telemetry_desc
import com.yunfie.illustia.resources.data_settings_title
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Composable
fun DataSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    var showCacheDeleteConfirm by remember { mutableStateOf(false) }
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

    if (showCacheDeleteConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(Res.string.data_delete_cache),
            summary = stringResource(Res.string.data_delete_cache_desc),
            confirmText = stringResource(Res.string.action_delete),
            destructive = true,
            onConfirm = {
                showCacheDeleteConfirm = false
                viewModel.clearAppCache()
            },
            onDismiss = { showCacheDeleteConfirm = false },
        )
    }

    pendingImportUri?.let { uri ->
        MiuixConfirmDialog(
            show = true,
            title = stringResource(Res.string.data_import_confirm_title),
            summary = stringResource(Res.string.data_import_confirm_desc),
            confirmText = stringResource(Res.string.data_import),
            onConfirm = {
                pendingImportUri = null
                viewModel.importManagedData(uri.toString())
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.data_settings_title),
                largeTitle = stringResource(Res.string.data_settings_title),
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
                Section(stringResource(Res.string.data_section_history)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingSwitchRow(
                            stringResource(Res.string.data_save_view_history),
                            settings.saveViewHistory,
                            viewModel::updateSaveViewHistory,
                            stringResource(Res.string.data_save_view_history_desc),
                        )
                        DividerLine()
                        SettingSwitchRow(
                            stringResource(Res.string.data_save_search_history),
                            settings.saveSearchHistory,
                            viewModel::updateSaveSearchHistory,
                            stringResource(Res.string.data_save_search_history_desc),
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.data_section_transfer)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingLinkRow(stringResource(Res.string.data_export)) {
                            exportLauncher.launch("illustia-data-${LocalDate.now()}.json")
                        }
                        DividerLine()
                        SettingLinkRow(stringResource(Res.string.data_import)) {
                            importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                        }
                    }
                    Text(
                        text = stringResource(Res.string.data_import_export_note),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            item {
                Section(stringResource(Res.string.data_section_cleanup)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingLinkRow(stringResource(Res.string.data_delete_cache)) { showCacheDeleteConfirm = true }
                    }
                }
            }

            item {
                Section(stringResource(Res.string.data_section_privacy_telemetry)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingSwitchRow(
                            title = stringResource(Res.string.data_send_telemetry),
                            checked = settings.sendTelemetry,
                            onCheckedChange = viewModel::updateSendTelemetry,
                            summary = stringResource(Res.string.data_send_telemetry_desc),
                        )
                    }
                }
            }
        }
    }
}
