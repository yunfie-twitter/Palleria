package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.StateBanner
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onRefreshTokenLogin: () -> Unit,
    showTokenLogin: Boolean = false,
    onTokenLoginDismiss: () -> Unit = {},
) {
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        ) {
            Text(
                text = stringResource(R.string.login_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                style = MiuixTheme.textStyles.title1,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = scaffoldPadding.calculateTopPadding() + 28.dp,
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StateBanner(state.loadState)
                Button(onClick = viewModel::openWebLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_web_button))
                }
                Button(onClick = onRefreshTokenLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_token_title))
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
