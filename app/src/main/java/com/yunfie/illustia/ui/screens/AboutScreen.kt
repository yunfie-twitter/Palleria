package com.yunfie.illustia.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.updater.UpdateCheckState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val FDROID_URL = "https://yunfi.f5.si/Illustia-dev/repo"

@Composable
fun AboutScreen(
    @Suppress("UnusedParameter") state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    var showLicensesSheet by remember { mutableStateOf(false) }
    val updateState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val appVersion =
        remember {
            runCatching {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName
            }.getOrNull() ?: "1.0.0"
        }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.about_title),
                largeTitle = stringResource(R.string.about_title),
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
            // アプリアイコン + バージョン
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(96.dp)
                                .squircleSurface(
                                    color = MiuixTheme.colorScheme.surfaceContainerHigh,
                                    cornerRadius = 24.dp,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = stringResource(R.string.about_version, appVersion),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                    Text(
                        text = stringResource(R.string.about_tagline),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        style = MiuixTheme.textStyles.footnote1,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // アプリ内アップデーター
            item {
                Section(stringResource(R.string.update_in_app_updater)) {
                    ElevatedPanel {
                        SettingRow(
                            title = stringResource(R.string.about_version_label),
                            summary = appVersion,
                        ) {
                            Button(
                                onClick = { viewModel.checkForUpdates(silent = false) },
                                enabled = updateState !is UpdateCheckState.Checking && updateState !is UpdateCheckState.Downloading,
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    stringResource(R.string.update_check_button),
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
                                        stringResource(R.string.update_checking),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.body2,
                                    )
                                }
                            }

                            is UpdateCheckState.UpToDate -> {
                                DividerLine()
                                Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(
                                        stringResource(R.string.update_up_to_date, currentUpdate.currentVersion),
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
                                        stringResource(R.string.update_available_title, release.versionName),
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
                                        onClick = { viewModel.downloadUpdate(release) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        Text(
                                            stringResource(R.string.update_download_button),
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
                                        String.format(
                                            java.util.Locale.US,
                                            "%.1f",
                                            currentUpdate.downloadedBytes / (1024f * 1024f),
                                        )
                                    val totalMb =
                                        String.format(
                                            java.util.Locale.US,
                                            "%.1f",
                                            currentUpdate.totalBytes / (1024f * 1024f),
                                        )
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
                                        onClick = { viewModel.cancelDownloadUpdate() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            stringResource(R.string.update_cancel_download),
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
                                        stringResource(R.string.update_ready_to_install),
                                        color = MiuixTheme.colorScheme.primary,
                                        style = MiuixTheme.textStyles.body2,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Button(
                                        onClick = { viewModel.installUpdate(currentUpdate.apkFile) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        Text(
                                            stringResource(R.string.update_install_button),
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
                                        stringResource(R.string.update_installing),
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
                                                viewModel.downloadUpdate(targetRelease)
                                            } else {
                                                viewModel.checkForUpdates(silent = false)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                    ) {
                                        Text(
                                            stringResource(R.string.update_retry_download),
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
                Section(stringResource(R.string.about_section_links)) {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        SettingLinkRow(stringResource(R.string.about_fdroid)) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FDROID_URL)))
                            }
                        }
                        DividerLine()
                        SettingLinkRow(stringResource(R.string.about_pixiv)) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/")))
                            }
                        }
                        DividerLine()
                        SettingLinkRow(stringResource(R.string.update_view_on_github)) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/yunfie-twitter/Palleria/releases"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            // 使用ライブラリ
            item {
                Section(stringResource(R.string.about_section_libraries)) {
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
                        SettingLinkRow(stringResource(R.string.about_open_source_licenses)) {
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
                        stringResource(R.string.about_disclaimer),
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

    OpenSourceLicensesSheet(
        show = showLicensesSheet,
        onDismiss = { showLicensesSheet = false },
    )
}

private data class ThirdPartyLicenseItem(
    val name: String,
    val category: String,
    val version: String,
    val author: String,
    val license: String,
    val url: String,
)

@Composable
private fun OpenSourceLicensesSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val context = LocalContext.current
    val licenses =
        remember {
            runCatching {
                context.assets.open("licenses.json").bufferedReader().use { reader ->
                    val jsonArray = org.json.JSONArray(reader.readText())
                    val list = ArrayList<ThirdPartyLicenseItem>(jsonArray.length())
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            ThirdPartyLicenseItem(
                                name = obj.optString("name"),
                                category = obj.optString("category"),
                                version = obj.optString("version"),
                                author = obj.optString("author"),
                                license = obj.optString("license"),
                                url = obj.optString("url"),
                            ),
                        )
                    }
                    list
                }
            }.getOrElse { emptyList() }
        }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp.dp * 0.75f).coerceAtLeast(300.dp)

    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.about_open_source_licenses),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
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
                    text = "No license information available",
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
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
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
