package com.yunfie.illustia

import android.app.LocaleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.appLanguageLocaleList
import com.yunfie.illustia.ui.IllustiaApp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<IllustiaViewModel> {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private var lastHandledClipboardText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !viewModel.uiState.value.settingsLoaded
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        applyAppLanguage(SettingsStore.readStoredAppLanguage(applicationContext))
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val controller = remember {
                ThemeController(
                    colorSchemeMode = ColorSchemeMode.System,
                )
            }

            LaunchedEffect(state.settings.secureWindow) {
                applySecureWindow(state.settings.secureWindow)
            }

            LaunchedEffect(state.settings.appLanguage) {
                applyAppLanguage(state.settings.appLanguage)
            }

            MiuixTheme(controller = controller) {
                IllustiaApp(viewModel)
            }
        }
        viewModel.handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        openPixivUrlFromClipboardIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }

    private fun openPixivUrlFromClipboardIfNeeded() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching {
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                ?.trim()
        }.getOrNull().orEmpty()
        if (text.isBlank() || text == lastHandledClipboardText) return
        if (NativeIntentRouter.parseText(text) == null) return

        lastHandledClipboardText = text
        viewModel.handleClipboardText(text)
    }

    private fun applySecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            // Android 13+ ではタスク切替画面のスクリーンショットも無効化
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(false)
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(true)
            }
        }
    }

    private fun applyAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java) ?: return
        localeManager.applicationLocales = appLanguageLocaleList(language)
    }
}
