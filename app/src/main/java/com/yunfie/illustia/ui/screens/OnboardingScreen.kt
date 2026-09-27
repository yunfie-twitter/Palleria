package com.yunfie.illustia.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun OnboardingLoginScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onRefreshTokenLogin: () -> Unit,
    showTokenLogin: Boolean = false,
    onTokenLoginDismiss: () -> Unit = {},
) {
    var showDetails by remember { mutableStateOf(false) }

    Scaffold { scaffoldPadding ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = 22.dp,
                        end = 22.dp,
                        top = scaffoldPadding.calculateTopPadding(),
                        bottom = scaffoldPadding.calculateBottomPadding() + 18.dp,
                    ),
        ) {
            val useWideLayout = maxWidth >= 600.dp && maxWidth > maxHeight

            if (useWideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandLockup(modifier = Modifier.weight(1f))
                    OnboardingActions(
                        state = state,
                        viewModel = viewModel,
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
                            .widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BrandLockup(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(72.dp))
                    OnboardingActions(
                        state = state,
                        viewModel = viewModel,
                        onShowDetails = { showDetails = true },
                        onRefreshTokenLogin = onRefreshTokenLogin,
                    )
                }
            }
        }
    }

    OverlayDialog(
        show = showDetails,
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

    if (showTokenLogin) {
        RefreshTokenLoginBottomSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = onTokenLoginDismiss,
        )
    }
}

@Composable
private fun OnboardingActions(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onShowDetails: () -> Unit,
    onRefreshTokenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LoginActions(
            onWebLogin = viewModel::openWebLogin,
            onShowDetails = onShowDetails,
            onRefreshTokenLogin = onRefreshTokenLogin,
        )
        Spacer(Modifier.height(16.dp))
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
private fun BrandLockup(modifier: Modifier = Modifier) {
    val appName = stringResource(R.string.app_name)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = appName,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 48.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.4).sp,
        )
    }
}

@Composable
private fun LoginActions(
    onWebLogin: () -> Unit,
    onShowDetails: () -> Unit,
    onRefreshTokenLogin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onWebLogin,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            colors = ButtonDefaults.buttonColorsPrimary(),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = stringResource(R.string.login_web_button),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
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
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.dividerLine,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
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
                .height(68.dp)
                .miuixClickable(haptic = true, onClick = onClick)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
