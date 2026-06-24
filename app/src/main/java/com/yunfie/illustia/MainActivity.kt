package com.yunfie.illustia

import android.app.ActivityManager
import android.app.LocaleManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.appLanguageLocaleList
import com.yunfie.illustia.ui.IllustiaApp
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : FragmentActivity() {
    private val viewModel by viewModels<IllustiaViewModel> {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private var lastHandledClipboardText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // プライバシーモード ON 時はスプラッシュも電卓アプリ風にする
        if (SettingsStore.isPrivacyModeEnabledSync(applicationContext)) {
            setTheme(R.style.AppTheme_Splash_Calculator)
        }
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

        // Observe app lifecycle for lock-on-return
        val lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (viewModel.shouldLockOnReturn()) {
                    viewModel.lockApp()
                }
            }
        }
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

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

            // Force FLAG_SECURE while locked so the app is obscured in recents
            // and screenshots are blocked, regardless of secureWindow setting.
            // Also clear the clipboard to prevent sensitive data leakage.
            LaunchedEffect(state.appLocked, state.settings.secureWindow) {
                if (state.appLocked && state.settings.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.clearPrimaryClip()
                } else {
                    applySecureWindow(state.settings.secureWindow)
                }
            }

                LaunchedEffect(state.settings.appLanguage) {
                applyAppLanguage(state.settings.appLanguage)
            }

            LaunchedEffect(state.settings.privacyModeEnabled, state.settings.hideRecents, state.settings.dummyAppName, state.settings.dummyIconVariant) {
                updateRecentsTaskDescription(state)
            }

            LaunchedEffect(state.settings.privacyModeEnabled, state.settings.dummyAppName, state.settings.dummyIconVariant) {
                viewModel.applyDummyIconSettings(this@MainActivity)
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

    private fun updateRecentsTaskDescription(state: com.yunfie.illustia.IllustiaUiState) {
        if (!state.settings.privacyModeEnabled) return

        val title = if (state.settings.hideRecents) {
            state.settings.dummyAppName.ifBlank { getString(R.string.app_name) }
        } else {
            getString(R.string.app_name)
        }

        val iconRes = if (state.settings.hideRecents) {
            resources.getIdentifier(state.settings.dummyIconVariant, "mipmap", packageName)
        } else {
            R.mipmap.ic_launcher
        }

        val iconBitmap = if (iconRes != 0) {
            BitmapFactory.decodeResource(resources, iconRes)
        } else {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        }

        val taskDesc = ActivityManager.TaskDescription(title, iconBitmap)
        setTaskDescription(taskDesc)
    }

    private fun applyAppLanguage(language: String) {
        val localeManager = getSystemService(LocaleManager::class.java) ?: return
        localeManager.applicationLocales = appLanguageLocaleList(language)
    }
}
