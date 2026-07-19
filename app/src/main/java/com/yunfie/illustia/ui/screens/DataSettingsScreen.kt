package com.yunfie.illustia.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DataSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    var showCacheDeleteConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri -> uri?.let(viewModel::exportManagedData) },
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> pendingImportUri = uri },
    )

    if (showCacheDeleteConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.data_delete_cache),
            summary = stringResource(R.string.data_delete_cache_desc),
            confirmText = stringResource(R.string.action_delete),
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
            title = stringResource(R.string.data_import_confirm_title),
            summary = stringResource(R.string.data_import_confirm_desc),
            confirmText = stringResource(R.string.data_import),
            onConfirm = {
                pendingImportUri = null
                viewModel.importManagedData(uri)
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.data_settings_title),
                largeTitle = stringResource(R.string.data_settings_title),
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
            item { Section(stringResource(R.string.data_section_history)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingSwitchRow(stringResource(R.string.data_save_view_history), state.settings.saveViewHistory, viewModel::updateSaveViewHistory, stringResource(R.string.data_save_view_history_desc))
                    DividerLine()
                    SettingSwitchRow(stringResource(R.string.data_save_search_history), state.settings.saveSearchHistory, viewModel::updateSaveSearchHistory, stringResource(R.string.data_save_search_history_desc))
                }
            }}

            item { Section(stringResource(R.string.data_section_transfer)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingLinkRow(stringResource(R.string.data_export)) {
                        exportLauncher.launch("illustia-data-${LocalDate.now()}.json")
                    }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.data_import)) {
                        importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                    }
                }
                Text(
                    text = stringResource(R.string.data_import_export_note),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }}

            item { Section(stringResource(R.string.data_section_cleanup)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingLinkRow(stringResource(R.string.data_delete_cache)) { showCacheDeleteConfirm = true }
                }
            }}
        }
    }
}
