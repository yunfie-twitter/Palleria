package com.yunfie.illustia

import android.app.ActivityManager
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.app.LocaleManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Display
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.LocalTextStyle
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.AppFont
import com.yunfie.illustia.settings.isAppDarkTheme
import com.yunfie.illustia.settings.rememberAppThemeColors
import com.yunfie.illustia.settings.appLanguageLocaleList
import com.yunfie.illustia.ui.IllustiaApp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.TextStyles

class MainActivity : FragmentActivity() {
    private companion object {
        const val MIN_HANOFF_API = 37
    }

    private val viewModel by viewModels<IllustiaViewModel> {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private var lastHandledClipboardText: String? = null
    private var appliedRefreshRateHint: Float? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // プライバシーモード ON 時はスプラッシュも電卓アプリ風にする
        if (SettingsStore.isPrivacyModeEnabledSync(applicationContext)) {
            setTheme(R.style.AppTheme_Splash_Calculator)
        }
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !viewModel.uiState.value.settingsLoaded
        }

        // スプラッシュ終了時のアニメーション (フル対応)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                // 全体のフェードアウト
                val alpha = android.animation.ObjectAnimator.ofFloat(
                    splashScreenView.view,
                    android.view.View.ALPHA,
                    1f,
                    0f
                )
                // アイコンは少しだけ縮小しながら抜けるほうが自然に見える
                val scaleX = android.animation.ObjectAnimator.ofFloat(
                    splashScreenView.iconView,
                    android.view.View.SCALE_X,
                    1f,
                    0.92f
                )
                val scaleY = android.animation.ObjectAnimator.ofFloat(
                    splashScreenView.iconView,
                    android.view.View.SCALE_Y,
                    1f,
                    0.92f
                )
                val translateY = android.animation.ObjectAnimator.ofFloat(
                    splashScreenView.iconView,
                    android.view.View.TRANSLATION_Y,
                    0f,
                    -12f
                )

                android.animation.AnimatorSet().apply {
                    duration = 360L
                    interpolator = AccelerateDecelerateInterpolator()
                    playTogether(alpha, scaleX, scaleY, translateY)
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            splashScreenView.view.alpha = 0f
                            splashScreenView.remove()
                        }
                    })
                    start()
                }
            }
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
            val systemDark = isSystemInDarkTheme()
            val themeColors = rememberAppThemeColors(state.settings)

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

            LaunchedEffect(state.settings.themeMode, systemDark) {
                val isDarkTheme = isAppDarkTheme(state.settings.themeMode, systemDark)
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }

            LaunchedEffect(state.settings.privacyModeEnabled, state.settings.hideRecents, state.settings.dummyAppName, state.settings.dummyIconVariant) {
                updateRecentsTaskDescription(state)
            }

            LaunchedEffect(state.settings.privacyModeEnabled, state.settings.dummyAppName, state.settings.dummyIconVariant) {
                viewModel.applyDummyIconSettings(this@MainActivity)
            }

            val fontFamily = remember(state.settings.appFont) {
                resolveAppFontFamily(state.settings.appFont)
            }
            val textStyles = remember(state.settings.appFont) {
                resolveAppTextStyles(fontFamily)
            }
            MiuixTheme(colors = themeColors, textStyles = textStyles) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = fontFamily)),
                ) {
                    IllustiaApp(viewModel)
                }
            }
        }
        viewModel.handleIncomingIntent(intent)
        enableHandoffIfSupported()
    }

    override fun onResume() {
        super.onResume()
        applyAdaptiveRefreshRateHint()
        openPixivUrlFromClipboardIfNeeded()
    }

    override fun onPause() {
        clearAdaptiveRefreshRateHint()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIncomingIntent(intent)
    }

    override fun onHandoffActivityDataRequested(handoffRequestInfo: HandoffActivityDataRequestInfo): HandoffActivityData {
        val state = viewModel.uiState.value
        val activityComponent = ComponentName(this, MainActivity::class.java)
        val handoffUri = currentHandoffUri(state)
        val fallbackUri = when {
            state.appLocked || state.privacyLocked -> Uri.parse("https://www.pixiv.net/")
            handoffUri?.host == "users" -> Uri.parse("https://www.pixiv.net/users/${handoffUri.lastPathSegment}")
            handoffUri?.host == "illusts" -> Uri.parse("https://www.pixiv.net/artworks/${handoffUri.lastPathSegment}")
            else -> Uri.parse("https://www.pixiv.net/")
        }
        val extras = PersistableBundle().apply {
            handoffUri?.let { putString(NativeIntentRouter.EXTRA_HANDOFF_URI, it.toString()) }
        }
        return HandoffActivityData.Builder(activityComponent)
            .setExtras(extras)
            .setFallbackUri(fallbackUri)
            .build()
    }

    private fun enableHandoffIfSupported() {
        if (Build.VERSION.SDK_INT < MIN_HANOFF_API) return

        val params = HandoffActivityParams.Builder()
            .setAllowHandoffWithoutPackageInstalled(true)
            .build()
        setHandoffEnabled(true, params)
    }

    private fun currentHandoffUri(state: IllustiaUiState): Uri? {
        if (state.appLocked || state.privacyLocked) return null
        return when {
            state.showUserPage && state.selectedUser != null -> Uri.parse("pixiv://users/${state.selectedUser.id}")
            state.imageViewerIllust != null -> Uri.parse("pixiv://illusts/${state.imageViewerIllust.id}?page=${state.imageViewerCurrentPage}")
            state.selectedIllust != null -> Uri.parse("pixiv://illusts/${state.selectedIllust.id}")
            else -> null
        }
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

    private fun applyAdaptiveRefreshRateHint() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val display = window.decorView.display ?: return
        val preferredRefreshRate = when {
            Build.VERSION.SDK_INT >= 36 && display.hasArrSupport() ->
                display.getSuggestedFrameRate(Display.FRAME_RATE_CATEGORY_NORMAL)
            else -> 60f
        }

        if (preferredRefreshRate <= 0f || appliedRefreshRateHint == preferredRefreshRate) return

        window.attributes = window.attributes.apply {
            this.preferredRefreshRate = preferredRefreshRate
        }
        appliedRefreshRateHint = preferredRefreshRate
    }

    private fun clearAdaptiveRefreshRateHint() {
        if (appliedRefreshRateHint == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        window.attributes = window.attributes.apply {
            preferredRefreshRate = 0f
        }
        appliedRefreshRateHint = null
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getSystemService(LocaleManager::class.java) ?: return
            localeManager.applicationLocales = appLanguageLocaleList(language)
            return
        }

        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(
                when (language) {
                    "ja" -> "ja-JP"
                    "en" -> "en-US"
                    else -> ""
                },
            ),
        )
    }

    private fun resolveAppFontFamily(value: String): FontFamily {
        return when (AppFont.fromValue(value)) {
            AppFont.System -> FontFamily.Default
            AppFont.MiSans -> FontFamily(
                Font(R.font.mi_sans_light, FontWeight.Light),
                Font(R.font.mi_sans_regular, FontWeight.Normal),
                Font(R.font.mi_sans_medium, FontWeight.Medium),
                Font(R.font.mi_sans_demibold, FontWeight.SemiBold),
                Font(R.font.mi_sans_bold, FontWeight.Bold),
                Font(R.font.mi_sans_heavy, FontWeight.Black),
                Font(R.font.mi_sans_extra_light, FontWeight.ExtraLight),
                Font(R.font.mi_sans_thin, FontWeight.Thin),
            )
        }
    }

    private fun resolveAppTextStyles(fontFamily: FontFamily): TextStyles {
        val base = defaultTextStyles()
        return base.copy(
            main = base.main.copy(fontFamily = fontFamily),
            paragraph = base.paragraph.copy(fontFamily = fontFamily),
            body1 = base.body1.copy(fontFamily = fontFamily),
            body2 = base.body2.copy(fontFamily = fontFamily),
            button = base.button.copy(fontFamily = fontFamily),
            footnote1 = base.footnote1.copy(fontFamily = fontFamily),
            footnote2 = base.footnote2.copy(fontFamily = fontFamily),
            headline1 = base.headline1.copy(fontFamily = fontFamily),
            headline2 = base.headline2.copy(fontFamily = fontFamily),
            subtitle = base.subtitle.copy(fontFamily = fontFamily),
            title1 = base.title1.copy(fontFamily = fontFamily),
            title2 = base.title2.copy(fontFamily = fontFamily),
            title3 = base.title3.copy(fontFamily = fontFamily),
            title4 = base.title4.copy(fontFamily = fontFamily),
        )
    }
}
