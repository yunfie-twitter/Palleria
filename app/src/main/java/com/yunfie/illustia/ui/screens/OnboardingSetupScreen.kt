package com.yunfie.illustia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.settings.AppLanguage
import com.yunfie.illustia.settings.appLanguageLabelRes
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onRefreshTokenLogin: () -> Unit,
    showTokenLogin: Boolean = false,
    onTokenLoginDismiss: () -> Unit = {},
) {
    var page by rememberSaveable { mutableIntStateOf(if (state.settings.onboardingSetupCompleted) 2 else 0) }
    BackHandler(enabled = page > 0 && !showTokenLogin) { page-- }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (page == 2) {
                OnboardingLoginScreen(state, viewModel, onRefreshTokenLogin, showTokenLogin, onTokenLoginDismiss)
            } else {
                key(page) {
                    Column(
                        Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                            .statusBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text(
                            stringResource(if (page == 0) R.string.setup_language_title else R.string.setup_network_title),
                            fontSize = 32.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onBackground,
                        )
                        if (page == 0) LanguageSetup(state, viewModel) else NetworkSetup(state, viewModel)
                    }
                }
            }
        }
        SetupNavigation(page, onBack = { page-- }, onNext = {
            if (page == 1) viewModel.completeOnboardingSetup()
            page++
        })
    }
}

@Composable
private fun LanguageSetup(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(painterResource(R.drawable.splashscreen_logo), contentDescription = null, modifier = Modifier.size(88.dp))
        Text(
            stringResource(R.string.setup_welcome, stringResource(R.string.app_name)),
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.setup_intro),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body1,
        )
    }
    SetupSectionLabel(stringResource(R.string.general_language))
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp)) {
        AppLanguage.entries.forEach { language ->
            RadioButtonPreference(
                title = stringResource(appLanguageLabelRes(language.value)),
                summary = language.languageTag,
                selected = state.settings.appLanguage == language.value,
                onClick = { viewModel.updateAppLanguage(language.value) },
            )
        }
    }
}

@Composable
private fun NetworkSetup(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
) {
    SetupSectionLabel(stringResource(R.string.image_proxy_title))
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp)) {
        RadioButtonPreference(
            title = stringResource(R.string.setup_official_images),
            summary = "i.pximg.net",
            selected = state.settings.pixivImageProxyBaseUrl.isEmpty(),
            onClick = { viewModel.updatePixivImageProxyBaseUrl("") },
        )
        PixivImageProxyOptions.forEach { proxy ->
            RadioButtonPreference(
                title = proxy.name,
                summary = proxy.baseUrl,
                selected = state.settings.pixivImageProxyBaseUrl == proxy.baseUrl,
                onClick = { viewModel.updatePixivImageProxyBaseUrl(proxy.baseUrl) },
            )
        }
        val current = state.settings.pixivImageProxyBaseUrl
        if (current.isNotBlank() && PixivImageProxyOptions.none { it.baseUrl == current }) {
            RadioButtonPreference(
                title = stringResource(R.string.image_proxy_custom),
                summary = current,
                selected = true,
                onClick = {},
            )
        }
    }
    SetupSectionLabel(stringResource(R.string.image_pixiv_network_mode))
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp)) {
        listOf(
            Triple("standard", R.string.setup_standard, R.string.setup_standard_desc),
            Triple("compat", R.string.setup_compat, R.string.setup_compat_desc),
            Triple("ech", R.string.setup_ech, R.string.setup_ech_desc),
        ).forEach { (mode, title, summary) ->
            RadioButtonPreference(
                title = stringResource(title),
                summary = stringResource(summary),
                selected = state.settings.pixivNetworkMode == mode,
                onClick = { viewModel.updatePixivNetworkMode(mode) },
            )
        }
    }
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.setup_help_title), fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.title4)
            Text(stringResource(R.string.setup_help), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun SetupSectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SetupNavigation(
    page: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = stringResource(R.string.setup_progress, page + 1, 3)
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            if (page > 0) Button(onClick = onBack) { Text(stringResource(R.string.setup_previous)) }
        }
        Row(Modifier.semantics { contentDescription = progress }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .size(width = if (page == index) 24.dp else 8.dp, height = 8.dp)
                        .background(
                            if (page ==
                                index
                            ) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.surfaceContainerHighest
                            },
                            CircleShape,
                        ),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (page < 2) {
                Button(onClick = onNext, colors = ButtonDefaults.buttonColorsPrimary()) {
                    Text(stringResource(R.string.setup_next))
                }
            }
        }
    }
}
