package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.screens.PinSetupScreen as SharedPinSetupScreen

@Composable
fun PinSetupScreen(
    isChange: Boolean,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val actions =
        remember(viewModel) {
            object : PinSetupActions {
                override suspend fun verifyPin(pin: String): Boolean = viewModel.verifyPin(pin)

                override suspend fun setupPin(pin: String) = viewModel.setupPin(pin)

                override suspend fun changePin(newPin: String) = viewModel.changePin(newPin)
            }
        }
    SharedPinSetupScreen(
        isChange = isChange,
        actions = actions,
        onBack = onBack,
    )
}
