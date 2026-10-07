package com.yunfie.illustia.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.AppLanguage
import com.yunfie.illustia.settings.appLanguageLabelRes
import com.yunfie.illustia.ui.components.miuixClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PING_TIMEOUT_MS = 3000

/**
 * Zero-Config Progressive Onboarding screen.
 * Instantly lands on the welcome screen with automatic OS language detection and
 * silent background connection diagnostics. Supports "Explore without login" (Guest Exploration).
 */
@Composable
fun OnboardingScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onRefreshTokenLogin: () -> Unit,
    showTokenLogin: Boolean = false,
    onTokenLoginDismiss: () -> Unit = {},
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var silentPingFailed by remember { mutableStateOf(false) }

    // Silent background connection test: verify official Pixiv server access without blocking the UI
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val connection =
                    (java.net.URL("https://app-api.pixiv.net/").openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = PING_TIMEOUT_MS
                        readTimeout = PING_TIMEOUT_MS
                        requestMethod = "HEAD"
                        instanceFollowRedirects = false
                    }
                connection.connect()
                val code = connection.responseCode
                connection.disconnect()
                silentPingFailed = code !in 200..499
            } catch (_: Exception) {
                silentPingFailed = true
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
    ) { scaffoldPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            // Top-right language switch button ("🌐")
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                IconButton(
                    onClick = { showLanguageDialog = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Tune,
                        contentDescription = stringResource(R.string.general_language),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                val useWideLayout = maxWidth >= 600.dp && maxWidth > maxHeight

                if (useWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WelcomeBrand(modifier = Modifier.weight(1f))
                        WelcomeActions(
                            state = state,
                            viewModel = viewModel,
                            silentPingFailed = silentPingFailed,
                            onSwitchEch = {
                                viewModel.updatePixivNetworkMode("ech")
                                viewModel.updatePixivImageProxyBaseUrl("https://i.pixiv.re")
                                silentPingFailed = false
                            },
                            onGuestExplore = viewModel::startGuestExploration,
                            onShowDetails = { showDetails = true },
                            onRefreshTokenLogin = onRefreshTokenLogin,
                            modifier = Modifier.weight(1f).widthIn(max = 560.dp),
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        WelcomeBrand(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(48.dp))
                        WelcomeActions(
                            state = state,
                            viewModel = viewModel,
                            silentPingFailed = silentPingFailed,
                            onSwitchEch = {
                                viewModel.updatePixivNetworkMode("ech")
                                viewModel.updatePixivImageProxyBaseUrl("https://i.pixiv.re")
                                silentPingFailed = false
                            },
                            onGuestExplore = viewModel::startGuestExploration,
                            onShowDetails = { showDetails = true },
                            onRefreshTokenLogin = onRefreshTokenLogin,
                        )
                    }
                }
            }
        }
    }

    // Quick Language Picker Dialog
    if (showLanguageDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.general_language),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showLanguageDialog = false },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppLanguage.entries.forEach { language ->
                    RadioButtonPreference(
                        title = stringResource(appLanguageLabelRes(language.value)),
                        summary = language.languageTag,
                        selected = state.settings.appLanguage == language.value,
                        onClick = {
                            viewModel.updateAppLanguage(language.value)
                            showLanguageDialog = false
                        },
                    )
                }
            }
        }
    }

    // About / Disclaimer Dialog
    if (showDetails) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.about_title),
            summary = stringResource(R.string.login_disclaimer),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showDetails = false },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.login_web_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body1,
                )
                Button(
                    onClick = { showDetails = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    insideMargin = PaddingValues(vertical = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.action_close),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (showTokenLogin) {
        RefreshTokenLoginBottomSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = onTokenLoginDismiss,
        )
    }
}

@Composable
private fun WelcomeBrand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.splashscreen_logo),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 44.sp,
            lineHeight = 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.2).sp,
        )
        Text(
            text = stringResource(R.string.setup_intro),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun WelcomeActions(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    silentPingFailed: Boolean,
    onSwitchEch: () -> Unit,
    onGuestExplore: () -> Unit,
    onShowDetails: () -> Unit,
    onRefreshTokenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Smart Assist Suggestion Banner when silent ping fails (e.g. firewall/censorship detected)
        AnimatedVisibility(
            visible = silentPingFailed,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200)),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                cornerRadius = 16.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_network_smart_suggest_title),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_network_smart_suggest_desc),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = onSwitchEch,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        insideMargin = PaddingValues(vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_network_smart_switch_btn),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // 1. Primary Action: Pixiv Web Login
        Button(
            onClick = viewModel::openWebLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.login_web_button),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        // 2. Secondary Action: Progressive Onboarding ("Explore without login")
        Button(
            onClick = onGuestExplore,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors =
                ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                ),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_guest_explore_button),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.dividerLine,
            )
            Text(
                text = stringResource(R.string.login_or),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.dividerLine,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomAction(
                label = stringResource(R.string.login_details),
                onClick = onShowDetails,
                modifier = Modifier.weight(1f),
            )
            BottomAction(
                label = stringResource(R.string.login_token_short),
                onClick = onRefreshTokenLogin,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        CheckboxPreference(
            title = stringResource(R.string.data_send_telemetry),
            summary = stringResource(R.string.data_send_telemetry_desc),
            checked = state.settings.sendTelemetry,
            onCheckedChange = viewModel::updateSendTelemetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BottomAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .miuixClickable(haptic = true, onClick = onClick)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
