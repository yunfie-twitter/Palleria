package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.account.LoginActions
import com.yunfie.illustia.ui.screens.OnboardingScreen as SharedOnboardingScreen

@Composable
fun OnboardingScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onRefreshTokenLogin: () -> Unit,
    showTokenLogin: Boolean = false,
    onTokenLoginDismiss: () -> Unit = {},
) {
    val actions =
        remember(viewModel) {
            object : OnboardingActions {
                override fun completeOnboardingSetup() = viewModel.completeOnboardingSetup()

                override fun updateAppLanguage(value: String) = viewModel.updateAppLanguage(value)

                override fun updatePixivImageProxyBaseUrl(url: String) = viewModel.updatePixivImageProxyBaseUrl(url)

                override fun updatePixivNetworkMode(mode: String) = viewModel.updatePixivNetworkMode(mode)
            }
        }
    val loginActions = viewModel.asLoginActions()
    SharedOnboardingScreen(
        state = state,
        actions = actions,
        loginActions = loginActions,
        appLogo = painterResource(R.drawable.splashscreen_logo),
        onRefreshTokenLogin = onRefreshTokenLogin,
        showTokenLogin = showTokenLogin,
        onTokenLoginDismiss = onTokenLoginDismiss,
    )
}

@Composable
internal fun IllustiaViewModel.asLoginActions(): LoginActions =
    remember(this) {
        object : LoginActions {
            override fun openAccountLoginMethod() = this@asLoginActions.openAccountLoginMethod()

            override fun openWebLogin() = this@asLoginActions.openWebLogin()

            override fun updateSendTelemetry(value: Boolean) = this@asLoginActions.updateSendTelemetry(value)

            override fun updateRefreshToken(value: String) = this@asLoginActions.updateRefreshToken(value)

            override fun login() = this@asLoginActions.login()
        }
    }
