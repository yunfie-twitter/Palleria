package com.yunfie.illustia.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface

private const val APP_VERSION = "1.0.0"
private const val GITHUB_URL = "https://github.com/"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
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

        // アプリアイコン + バージョン
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .squircleSurface(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            cornerRadius = 24.dp,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.splashscreen_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Text(
                    text = "Palleria",
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.about_version, APP_VERSION),
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

        item {
            Section(stringResource(R.string.about_section_info)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingRow(stringResource(R.string.about_version_label), APP_VERSION) {
                        Text(stringResource(R.string.about_latest), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.about_supported_os), "Android 13+") {
                        Text("API 33", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.about_license), stringResource(R.string.about_open_source)) {
                        Text("MIT", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.about_section_links)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingLinkRow(stringResource(R.string.about_github)) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                        }
                    }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.about_pixiv)) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/")))
                        }
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.about_section_libraries)) {
                ElevatedPanel {
                    listOf(
                        "Jetpack Compose" to "Google",
                        "Miuix KMP" to "yukonga",
                        "Coil 3" to "Coil Contributors",
                        "OkHttp" to "Square",
                        "kotlinx.serialization" to "JetBrains",
                    ).forEachIndexed { index, (lib, author) ->
                        if (index > 0) DividerLine()
                        SettingRow(lib, author) {}
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
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
}
