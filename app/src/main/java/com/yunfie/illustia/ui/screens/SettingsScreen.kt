package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class SettingsCategory(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: () -> Unit,
)

@Composable
fun SettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val appVersion = remember {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        }.getOrNull() ?: "1.0.0"
    }
    val scrollBehavior = MiuixScrollBehavior()
    val mutedTotal = state.settings.mutedIllusts.size + state.settings.mutedUsers.size + state.settings.mutedTags.size

    val categories = remember(state.settings.refreshToken, state.settings.viewHistory.size, mutedTotal, state.settings.privacyModeEnabled) {
        listOf(
            SettingsCategory(context.getString(R.string.settings_general), context.getString(R.string.settings_general_summary), MiuixIcons.More) { viewModel.openGeneralSettings() },
            SettingsCategory(context.getString(R.string.settings_image), context.getString(R.string.settings_image_summary), MiuixIcons.Photos) { viewModel.openImageSettings() },
            SettingsCategory(context.getString(R.string.settings_bookmark), context.getString(R.string.settings_bookmark_summary), MiuixIcons.FavoritesFill) { viewModel.openBookmarkSettings() },
            SettingsCategory(context.getString(R.string.settings_account), if (state.settings.refreshToken.isNotBlank()) context.getString(R.string.settings_logged_in) else context.getString(R.string.settings_not_logged_in), MiuixIcons.Contacts) { viewModel.openAccountSettings() },
            SettingsCategory(context.getString(R.string.settings_data), "${context.getString(R.string.more_view_history)} ${context.getString(R.string.data_items_count, state.settings.viewHistory.size)} / ${context.getString(R.string.more_mute_settings)} ${context.getString(R.string.data_items_count, mutedTotal)}", MiuixIcons.Timer) { viewModel.openDataSettings() },
            SettingsCategory("プライバシーモード", if (state.settings.privacyModeEnabled) "有効 — 電卓でカモフラージュ中" else "無効", MiuixIcons.Lock) { viewModel.openPrivacyModeSettings() },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_title),
                largeTitle = stringResource(R.string.settings_title),
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
            item {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    categories.forEachIndexed { index, cat ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            )
                        }
                        SettingsCategoryRow(category = cat)
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${stringResource(R.string.app_name)} v$appVersion", color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f), style = MiuixTheme.textStyles.footnote1)
                    Text("Developed with Love for Art", color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f), style = MiuixTheme.textStyles.footnote1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(category: SettingsCategory) {
    BasicComponent(
        modifier = Modifier
            .fillMaxWidth()
            .miuixClickable(onClick = category.route),
        insideMargin = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        startAction = {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 12.dp),
            )
        },
    ) {
        Column {
            Text(
                text = category.title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
            )
            Text(
                text = category.summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
}
