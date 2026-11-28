package com.yunfie.illustia.ui.screens

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.discord.DiscordRpcManager
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.discord.DiscordRpcPlatform
import kotlinx.coroutines.flow.StateFlow
import com.yunfie.illustia.ui.screens.discord.DiscordSettingsScreen as SharedDiscordSettingsScreen

@Composable
private fun rememberDiscordRpcPlatform(): DiscordRpcPlatform =
    object : DiscordRpcPlatform {
        override val defaultApplicationId: String = DiscordRpcManager.DEFAULT_APP_ID

        override val diagnostics: StateFlow<List<String>> = DiscordRpcManager.diagnostics

        override fun isSupported(): Boolean = DiscordRpcManager.isSupported()

        override fun clearDiagnostics() = DiscordRpcManager.clearDiagnostics()
    }

@Composable
fun DiscordSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenDiscordLogin: () -> Unit,
) {
    val context = LocalContext.current
    SharedDiscordSettingsScreen(
        settings = settings,
        viewModel = viewModel,
        platform = rememberDiscordRpcPlatform(),
        onBack = onBack,
        onOpenDiscordLogin = onOpenDiscordLogin,
        onShowMessage = { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
    )
}
