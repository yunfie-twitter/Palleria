package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.platform.formatDecimal
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.about_disclaimer
import com.yunfie.illustia.resources.about_fdroid
import com.yunfie.illustia.resources.about_no_license_information
import com.yunfie.illustia.resources.about_open_source_licenses
import com.yunfie.illustia.resources.about_pixiv
import com.yunfie.illustia.resources.about_section_libraries
import com.yunfie.illustia.resources.about_section_links
import com.yunfie.illustia.resources.about_title
import com.yunfie.illustia.resources.about_version
import com.yunfie.illustia.resources.about_version_label
import com.yunfie.illustia.resources.action_close
import com.yunfie.illustia.resources.app_name
import com.yunfie.illustia.resources.update_available_title
import com.yunfie.illustia.resources.update_cancel_download
import com.yunfie.illustia.resources.update_check_button
import com.yunfie.illustia.resources.update_checking
import com.yunfie.illustia.resources.update_download_button
import com.yunfie.illustia.resources.update_in_app_updater
import com.yunfie.illustia.resources.update_install_button
import com.yunfie.illustia.resources.update_installing
import com.yunfie.illustia.resources.update_ready_to_install
import com.yunfie.illustia.resources.update_retry_download
import com.yunfie.illustia.resources.update_up_to_date
import com.yunfie.illustia.resources.update_view_on_github
import com.yunfie.illustia.ui.components.AppBottomSheet
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.currentWindowSizeDp
import com.yunfie.illustia.ui.components.rememberUrlOpener
import com.yunfie.illustia.updater.UpdateCheckState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val FDROID_URL = "https://yunfi.f5.si/Illustia-dev/repo"
private val ABOUT_HERO_SCROLL_DISTANCE = 300.dp

@Composable
fun AboutScreen(
    updateState: UpdateCheckState,
    actions: AboutActions,
    appIcon: Painter,
    onBack: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    val openUrl = rememberUrlOpener()
    var showLicensesSheet by remember { mutableStateOf(false) }

    val appVersion = remember(actions) { actions.appVersion() ?: "1.0.0" }

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val heroScrollDistancePx = with(LocalDensity.current) { ABOUT_HERO_SCROLL_DISTANCE.toPx() }
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / heroScrollDistancePx).coerceIn(0f, 1f)
            }
        }
    }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            SmallTopAppBar(
                title = stringResource(Res.string.about_title),
                scrollBehavior = scrollBehavior,
                color = MiuixTheme.colorScheme.surface.copy(alpha = scrollProgress),
                titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = scrollProgress),
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(scaffoldPadding.calculateTopPadding() + ABOUT_HERO_SCROLL_DISTANCE)
                        .graphicsLayer { alpha = 1f - scrollProgress }
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                                        MiuixTheme.colorScheme.surface.copy(alpha = 0.08f),
                                        MiuixTheme.colorScheme.surface.copy(alpha = 0f),
                                    ),
                            ),
                        ),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = scaffoldPadding.calculateTopPadding() + 40.dp)
                        .height(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Image(
                    painter = appIcon,
                    contentDescription = stringResource(Res.string.app_name),
                    modifier =
                        Modifier
                            .size(88.dp)
                            .graphicsLayer {
                                val iconProgress = ((scrollProgress - 0.35f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - iconProgress
                                scaleX = 1f - iconProgress * 0.05f
                                scaleY = 1f - iconProgress * 0.05f
                            },
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = stringResource(Res.string.app_name),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .padding(top = 12.dp, bottom = 5.dp)
                            .graphicsLayer {
                                val nameProgress = ((scrollProgress - 0.20f) / 0.15f).coerceIn(0f, 1f)
                                alpha = 1f - nameProgress
                                scaleX = 1f - nameProgress * 0.05f
                                scaleY = 1f - nameProgress * 0.05f
                            },
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier.graphicsLayer {
                            val detailsProgress = ((scrollProgress - 0.05f) / 0.15f).coerceIn(0f, 1f)
                            alpha = 1f - detailsProgress
                            scaleX = 1f - detailsProgress * 0.05f
                            scaleY = 1f - detailsProgress * 0.05f
                        },
                ) {
                    Text(
                        text = stringResource(Res.string.about_version, appVersion),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                state = listState,
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = scaffoldPadding.calculateTopPadding(),
                        bottom = 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "about_hero_spacer") {
                    Spacer(modifier = Modifier.height(ABOUT_HERO_SCROLL_DISTANCE))
                }

                // アプリ内アップデーター
                item {
                    Section(stringResource(Res.string.update_in_app_updater)) {
                        ElevatedPanel {
                            SettingRow(
                                title = stringResource(Res.string.about_version_label),
                                summary = appVersion,
                            ) {
                                Button(
                                    onClick = { actions.checkForUpdates(silent = false) },
                                    enabled = updateState !is UpdateCheckState.Checking && updateState !is UpdateCheckState.Downloading,
                                    colors = ButtonDefaults.buttonColorsPrimary(),
                                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        stringResource(Res.string.update_check_button),
                                        color = MiuixTheme.colorScheme.onPrimary,
                                        style = MiuixTheme.textStyles.footnote1,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            when (val currentUpdate = updateState) {
                                is UpdateCheckState.Checking -> {
                                    DividerLine()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(24.dp))
                                        Text(
                                            stringResource(Res.string.update_checking),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            style = MiuixTheme.textStyles.body2,
                                        )
                                    }
                                }

                                is UpdateCheckState.UpToDate -> {
                                    DividerLine()
                                    Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                        Text(
                                            stringResource(Res.string.update_up_to_date, currentUpdate.currentVersion),
                                            color = MiuixTheme.colorScheme.primary,
                                            style = MiuixTheme.textStyles.body2,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                is UpdateCheckState.UpdateAvailable -> {
                                    val release = currentUpdate.release
                                    DividerLine()
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            stringResource(Res.string.update_available_title, release.versionName),
                                            color = MiuixTheme.colorScheme.onBackground,
                                            style = MiuixTheme.textStyles.title2,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        if (release.releaseTitle.isNotBlank() && release.releaseTitle != release.versionName) {
                                            Text(
                                                release.releaseTitle,
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                style = MiuixTheme.textStyles.body2,
                                            )
                                        }
                                        if (release.releaseNotes.isNotBlank()) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MiuixTheme.colorScheme.surfaceContainer)
                                                        .padding(10.dp),
                                            ) {
                                                com.yunfie.illustia.ui.components.MarkdownText(
                                                    markdown = release.releaseNotes,
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = { actions.downloadUpdate(release) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) {
                                            Text(
                                                stringResource(Res.string.update_download_button),
                                                color = MiuixTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                is UpdateCheckState.Downloading -> {
                                    DividerLine()
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val downloadedMb =
                                            formatDecimal(currentUpdate.downloadedBytes / (1024f * 1024f).toDouble(), 1)
                                        val totalMb =
                                            formatDecimal(currentUpdate.totalBytes / (1024f * 1024f).toDouble(), 1)
                                        val percent = (currentUpdate.progress * 100).toInt()
                                        Text(
                                            "$downloadedMb MB / $totalMb MB ($percent%)",
                                            color = MiuixTheme.colorScheme.onBackground,
                                            style = MiuixTheme.textStyles.body2,
                                        )
                                        LinearProgressIndicator(
                                            progress = currentUpdate.progress,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Button(
                                            onClick = { actions.cancelDownloadUpdate() },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(
                                                stringResource(Res.string.update_cancel_download),
                                                style = MiuixTheme.textStyles.footnote1,
                                            )
                                        }
                                    }
                                }

                                is UpdateCheckState.ReadyToInstall -> {
                                    DividerLine()
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            stringResource(Res.string.update_ready_to_install),
                                            color = MiuixTheme.colorScheme.primary,
                                            style = MiuixTheme.textStyles.body2,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Button(
                                            onClick = { actions.installUpdate(currentUpdate.apkPath) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) {
                                            Text(
                                                stringResource(Res.string.update_install_button),
                                                color = MiuixTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                is UpdateCheckState.Installing -> {
                                    DividerLine()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(24.dp))
                                        Text(
                                            stringResource(Res.string.update_installing),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            style = MiuixTheme.textStyles.body2,
                                        )
                                    }
                                }

                                is UpdateCheckState.Error -> {
                                    DividerLine()
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            currentUpdate.message,
                                            color = MiuixTheme.colorScheme.error,
                                            style = MiuixTheme.textStyles.footnote1,
                                        )
                                        Button(
                                            onClick = {
                                                val targetRelease = currentUpdate.release
                                                if (targetRelease != null) {
                                                    actions.downloadUpdate(targetRelease)
                                                } else {
                                                    actions.checkForUpdates(silent = false)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColorsPrimary(),
                                        ) {
                                            Text(
                                                stringResource(Res.string.update_retry_download),
                                                color = MiuixTheme.colorScheme.onPrimary,
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }

                                UpdateCheckState.Idle -> {
                                    // No action needed
                                }
                            }
                        }
                    }
                }

                // リンク
                item {
                    Section(stringResource(Res.string.about_section_links)) {
                        ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                            SettingLinkRow(stringResource(Res.string.about_fdroid)) {
                                runCatching {
                                    openUrl(FDROID_URL)
                                }
                            }
                            DividerLine()
                            SettingLinkRow(stringResource(Res.string.about_pixiv)) {
                                runCatching {
                                    openUrl("https://www.pixiv.net/")
                                }
                            }
                            DividerLine()
                            SettingLinkRow(stringResource(Res.string.update_view_on_github)) {
                                openUrl("https://github.com/yunfie-twitter/Palleria/releases")
                            }
                        }
                    }
                }

                // 使用ライブラリ
                item {
                    Section(stringResource(Res.string.about_section_libraries)) {
                        ElevatedPanel {
                            listOf(
                                "Jetpack Compose" to "Google",
                                "Miuix KMP" to "yukonga",
                                "Coil 3" to "Coil Contributors",
                                "OkHttp" to "Square",
                                "kotlinx.serialization" to "JetBrains",
                                "Shizuku" to "RikkaApps",
                            ).forEachIndexed { index, (lib, author) ->
                                if (index > 0) DividerLine()
                                SettingRow(lib, author) {}
                            }
                            DividerLine()
                            SettingLinkRow(stringResource(Res.string.about_open_source_licenses)) {
                                showLicensesSheet = true
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(Res.string.about_disclaimer),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            style = MiuixTheme.textStyles.footnote1,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            "Developed with ❤️ for Art",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                }
            }
        }
    }

    OpenSourceLicensesSheet(
        show = showLicensesSheet,
        licenses = actions.readThirdPartyLicenses(),
        onDismiss = { showLicensesSheet = false },
    )
}

@Composable
private fun OpenSourceLicensesSheet(
    show: Boolean,
    licenses: List<ThirdPartyLicenseItem>,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val openUrl = rememberUrlOpener()

    val windowHeightDp = currentWindowSizeDp().heightDp
    val maxSheetHeight = (windowHeightDp.dp * 0.75f).coerceAtLeast(300.dp)

    AppBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(Res.string.about_open_source_licenses),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(Res.string.action_close))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        if (licenses.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.about_no_license_information),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxSheetHeight),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    count = licenses.size,
                    key = { index -> "${licenses[index].name}_$index" },
                ) { index ->
                    val item = licenses[index]
                    val summaryText =
                        buildString {
                            if (item.version.isNotEmpty()) {
                                append("v")
                                append(item.version)
                                append(" • ")
                            }
                            append(item.license)
                            if (item.author.isNotEmpty()) {
                                append(" • ")
                                append(item.author)
                            }
                        }
                    if (item.url.isNotEmpty()) {
                        SettingLinkRow(
                            title = item.name,
                            summary = summaryText,
                        ) {
                            runCatching {
                                openUrl(item.url)
                            }
                        }
                    } else {
                        SettingRow(
                            title = item.name,
                            summary = summaryText,
                        ) {}
                    }
                    if (index < licenses.lastIndex) {
                        DividerLine()
                    }
                }
            }
        }
    }
}
