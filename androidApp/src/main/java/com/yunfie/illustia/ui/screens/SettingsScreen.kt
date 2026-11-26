package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.app_name
import com.yunfie.illustia.resources.feature_flags_summary
import com.yunfie.illustia.resources.feature_flags_title
import com.yunfie.illustia.resources.feature_flags_unlocked
import com.yunfie.illustia.resources.settings_footer
import com.yunfie.illustia.resources.settings_title
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.SettingLinkRow
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.jetbrains.compose.resources.stringResource as resString

private data class SettingsCategory(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val route: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val appVersion =
        remember {
            runCatching {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            }.getOrNull() ?: "1.0.0"
        }
    val scrollBehavior = MiuixScrollBehavior()
    val mutedTotal = settings.mutedIllusts.size + settings.mutedUsers.size + settings.mutedTags.size

    var versionTapCount by remember { mutableIntStateOf(0) }
    var lastVersionTapTime by remember { mutableLongStateOf(0L) }

    val hasActiveFeatureFlags = settings.featureFlags.any { it.value }

    val featureFlagsTitle = resString(Res.string.feature_flags_title)
    val featureFlagsSummary = resString(Res.string.feature_flags_summary)
    val featureFlagsUnlocked = resString(Res.string.feature_flags_unlocked)

    val categories =
        remember(
            settings.refreshToken,
            settings.viewHistory.size,
            mutedTotal,
            hasActiveFeatureFlags,
            featureFlagsTitle,
            featureFlagsSummary,
        ) {
            buildList {
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.SettingsGeneral),
                        AppStrings.get(StringKey.SettingsGeneralSummary),
                        MiuixIcons.More,
                    ) {
                        viewModel.openGeneralSettings()
                    },
                )
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.SettingsImage),
                        AppStrings.get(StringKey.SettingsImageSummary),
                        MiuixIcons.Photos,
                    ) {
                        viewModel.openImageSettings()
                    },
                )
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.SettingsBookmark),
                        AppStrings.get(StringKey.SettingsBookmarkSummary),
                        MiuixIcons.FavoritesFill,
                    ) {
                        viewModel.openBookmarkSettings()
                    },
                )
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.SettingsAccount),
                        if (settings.refreshToken.isNotBlank()) {
                            AppStrings.get(StringKey.SettingsLoggedIn)
                        } else {
                            AppStrings.get(StringKey.SettingsNotLoggedIn)
                        },
                        MiuixIcons.Contacts,
                    ) {
                        viewModel.openAccountSettings()
                    },
                )
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.SettingsData),
                        "${AppStrings.get(StringKey.MoreViewHistory)} ${AppStrings.get(
                            StringKey.DataItemsCount,
                            settings.viewHistory.size,
                        )} / ${AppStrings.get(StringKey.MoreMuteSettings)} ${AppStrings.get(StringKey.DataItemsCount, mutedTotal)}",
                        MiuixIcons.Timer,
                    ) {
                        viewModel.openDataSettings()
                    },
                )
                add(
                    SettingsCategory(
                        AppStrings.get(StringKey.UpdateSettingsTitle),
                        AppStrings.get(StringKey.UpdateSettingsSummary),
                        MiuixIcons.TopDownloads,
                    ) {
                        viewModel.openUpdateSettings()
                    },
                )
                if (hasActiveFeatureFlags) {
                    add(
                        SettingsCategory(
                            featureFlagsTitle,
                            featureFlagsSummary,
                            MiuixIcons.Tune,
                        ) {
                            viewModel.openFeatureFlags()
                        },
                    )
                }
            }
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.settings_title),
                largeTitle = stringResource(Res.string.settings_title),
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
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    categories.forEachIndexed { index, cat ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            )
                        }
                        SettingLinkRow(
                            title = cat.title,
                            summary = cat.summary,
                            icon = cat.icon,
                            onClick = cat.route,
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${stringResource(Res.string.app_name)} v$appVersion",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        style = MiuixTheme.textStyles.footnote1,
                        modifier =
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                val now = System.currentTimeMillis()
                                if (now - lastVersionTapTime > 3000L) {
                                    versionTapCount = 1
                                } else {
                                    versionTapCount++
                                }
                                lastVersionTapTime = now
                                if (versionTapCount >= 5) {
                                    versionTapCount = 0
                                    viewModel.showMessage(featureFlagsUnlocked)
                                    viewModel.openFeatureFlags()
                                }
                            },
                    )
                    Text(
                        stringResource(Res.string.settings_footer),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        style = MiuixTheme.textStyles.footnote1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
