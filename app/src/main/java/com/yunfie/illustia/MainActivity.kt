package com.yunfie.illustia

import android.Manifest
import android.app.ActivityManager
import android.app.HandoffActivityData
import android.app.HandoffActivityDataRequestInfo
import android.app.HandoffActivityParams
import android.app.LocaleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.yunfie.illustia.data.NativeImageAnalysis
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.AppFont
import com.yunfie.illustia.settings.appLanguageLocaleList
import com.yunfie.illustia.settings.isAppDarkTheme
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.settings.rememberAppThemeColors
import com.yunfie.illustia.ui.IllustiaApp
import com.yunfie.illustia.ui.components.PixivImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

private val MiSansFontFamily by lazy {
    FontFamily(
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

@Suppress("LargeClass")
class MainActivity : FragmentActivity() {
    private companion object {
        const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 25
        const val STARTUP_POST_WORK_DELAY_MS = 400L
        const val SPLASH_EXIT_ANIMATION_DURATION_MS = 140L
        const val SPLASH_MIN_ANIMATION_DURATION_MS = 60L
        const val SPLASH_ICON_EXIT_TARGET_SCALE = 1.15f
        const val SPLASH_EASING_CONTROL_X1 = 0.4f
        const val SPLASH_EASING_CONTROL_X2 = 0.2f
        const val REFRESH_RATE_LOW_MIN = 30f
        const val REFRESH_RATE_NORMAL = 60f
        const val REFRESH_RATE_HIGH_MAX = 120f
        const val TASK_SNAPSHOT_BLUR_RADIUS = 60f
    }

    private val viewModel by viewModels<IllustiaViewModel> {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
    }
    private val dynamicHzController =
        com.yunfie.illustia.platform
            .DefaultDynamicHzController()
    private var lastHandledClipboardText: String? = null
    private var appliedRefreshRateHint: Float? = null
    private var processLifecycleObserver: DefaultLifecycleObserver? = null
    private var appliedAppLanguage: String? = null
    private var appliedDarkTheme: Boolean? = null
    private var isSnapshotBlurApplied = false
    private var clipboardDetectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // プライバシーモード ON 時はスプラッシュも電卓アプリ風にする
        if (DummyAppIconSwitcher.isPrivacyLauncherEnabled(applicationContext)) {
            setTheme(R.style.AppTheme_Splash_Calculator)
        }
        val splashScreen = installSplashScreen()

        // core-splashscreen の互換実装を使い、API 25 以降で同じフェードアウト＆ズームアウトにする。
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val splashView = splashScreenView.view
            val iconView = splashScreenView.iconView

            val animDurationScale =
                runCatching {
                    android.provider.Settings.Global.getFloat(
                        contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    )
                }.getOrDefault(1f)
            if (animDurationScale == 0f) {
                splashScreenView.remove()
                return@setOnExitAnimationListener
            }

            val iconScaleX =
                android.animation.ObjectAnimator.ofFloat(
                    iconView,
                    android.view.View.SCALE_X,
                    1f,
                    SPLASH_ICON_EXIT_TARGET_SCALE,
                )
            val iconScaleY =
                android.animation.ObjectAnimator.ofFloat(
                    iconView,
                    android.view.View.SCALE_Y,
                    1f,
                    SPLASH_ICON_EXIT_TARGET_SCALE,
                )
            val iconAlpha = android.animation.ObjectAnimator.ofFloat(iconView, android.view.View.ALPHA, 1f, 0f)
            val splashAlpha = android.animation.ObjectAnimator.ofFloat(splashView, android.view.View.ALPHA, 1f, 0f)

            android.animation.AnimatorSet().apply {
                duration =
                    (SPLASH_EXIT_ANIMATION_DURATION_MS * animDurationScale)
                        .toLong()
                        .coerceAtLeast(SPLASH_MIN_ANIMATION_DURATION_MS)
                interpolator =
                    android.view.animation.PathInterpolator(
                        SPLASH_EASING_CONTROL_X1,
                        0f,
                        SPLASH_EASING_CONTROL_X2,
                        1f,
                    )
                playTogether(splashAlpha, iconScaleX, iconScaleY, iconAlpha)
                addListener(
                    object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            splashScreenView.remove()
                        }
                    },
                )
                start()
            }
        }
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        appliedDarkTheme = isDark
        enableEdgeToEdge(
            statusBarStyle =
                if (isDark) {
                    SystemBarStyle.dark(
                        Color.TRANSPARENT,
                    )
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                },
            navigationBarStyle =
                if (isDark) {
                    SystemBarStyle.dark(
                        Color.TRANSPARENT,
                    )
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                },
        )
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        super.onCreate(savedInstanceState)
        // OSデフォルトのWindow背景を破棄し、最下層のオーバードローを消滅させる
        window.setBackgroundDrawable(null)
        // Keep the first frame protected until the async settings/lock state is known.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lastHandledClipboardText = null

        setContent {
            val presentation =
                remember(viewModel) {
                    viewModel.uiState.map { it.activityPresentation() }.distinctUntilChanged()
                }
            val uiState by presentation.collectAsStateWithLifecycle(initialValue = viewModel.uiState.value.activityPresentation())
            val settings = uiState.settings
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && resources.configuration.isScreenWideColorGamut) {
                LaunchedEffect(settings.wideColorGamutEnabled) {
                    window.colorMode =
                        if (settings.wideColorGamutEnabled) {
                            ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
                        } else {
                            ActivityInfo.COLOR_MODE_DEFAULT
                        }
                }
            }
            val appLocked = uiState.appLocked
            val settingsLoaded = uiState.settingsLoaded
            val systemDark = isSystemInDarkTheme()
            var artworkAccent by remember { mutableStateOf<Int?>(null) }
            val selectedArtwork = uiState.selectedIllust
            LaunchedEffect(
                settings.artworkThemeEnabled,
                selectedArtwork?.id,
                settings.pixivImageProxyBaseUrl,
            ) {
                artworkAccent = null
                if (!settings.artworkThemeEnabled || selectedArtwork == null) return@LaunchedEffect
                runCatching {
                    val url = selectedArtwork.previewUrl.ifBlank { selectedArtwork.imageUrl }
                    val request =
                        ImageRequest
                            .Builder(this@MainActivity)
                            .data(proxyPixivImageUrl(url, settings.pixivImageProxyBaseUrl))
                            .httpHeaders(PixivImageHeaders)
                            .size(160)
                            .build()
                    val result = SingletonImageLoader.get(this@MainActivity).execute(request)
                    if (result is SuccessResult) {
                        withContext(Dispatchers.Default) {
                            NativeImageAnalysis.dominantColor(result.image.toBitmap())
                        }
                    } else {
                        null
                    }
                }.getOrNull()?.let { artworkAccent = it }
            }
            val themeColors = rememberAppThemeColors(settings, artworkAccent)

            // Force FLAG_SECURE while locked so the app is obscured in recents
            // and screenshots are blocked, regardless of secureWindow setting.
            // Also clear the clipboard to prevent sensitive data leakage.
            LaunchedEffect(settingsLoaded, appLocked, settings.secureWindow, settings.appLockEnabled) {
                if (!settingsLoaded) return@LaunchedEffect
                if (appLocked && settings.appLockEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (PlatformCapabilities.supportsClipboardClear()) {
                        clipboard?.clearPrimaryClip()
                    } else {
                        @Suppress("DEPRECATION")
                        clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                } else {
                    applySecureWindow(settings.secureWindow)
                }
            }

            LaunchedEffect(settingsLoaded, settings.appLanguage) {
                if (!settingsLoaded) return@LaunchedEffect
                applyAppLanguage(settings.appLanguage)
            }

            LaunchedEffect(settingsLoaded, settings.notchOptimization) {
                if (!settingsLoaded) return@LaunchedEffect
                applyNotchOptimization(settings.notchOptimization)
            }

            LaunchedEffect(settingsLoaded, settings.themeMode, systemDark) {
                if (!settingsLoaded) return@LaunchedEffect
                val isDarkTheme = isAppDarkTheme(settings.themeMode, systemDark)
                if (appliedDarkTheme == isDarkTheme) return@LaunchedEffect
                appliedDarkTheme = isDarkTheme
                enableEdgeToEdge(
                    statusBarStyle =
                        if (isDarkTheme) {
                            SystemBarStyle.dark(
                                Color.TRANSPARENT,
                            )
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        },
                    navigationBarStyle =
                        if (isDarkTheme) {
                            SystemBarStyle.dark(
                                Color.TRANSPARENT,
                            )
                        } else {
                            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        },
                )
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            LaunchedEffect(
                settingsLoaded,
                settings.privacyModeEnabled,
                settings.hideRecents,
                settings.dummyAppName,
                settings.dummyIconVariant,
            ) {
                if (!settingsLoaded) return@LaunchedEffect
                updateRecentsTaskDescription(settings)
            }

            LaunchedEffect(
                settingsLoaded,
                settings.privacyModeEnabled,
                settings.appIconVariant,
                settings.dummyAppName,
                settings.dummyIconVariant,
            ) {
                if (!settingsLoaded) return@LaunchedEffect
                viewModel.applyDummyIconSettings(this@MainActivity)
            }

            LaunchedEffect(
                settingsLoaded,
                uiState.selectedIllust?.title,
                uiState.selectedUserName,
                uiState.activeSearchWord,
                uiState.showUserPage,
            ) {
                if (!settingsLoaded) return@LaunchedEffect
                val appName = getString(R.string.app_name)
                val pageTitle =
                    when {
                        uiState.selectedIllust != null -> {
                            val illustTitle = uiState.selectedIllust?.title.orEmpty()
                            val artist = uiState.selectedIllust?.artistName.orEmpty()
                            if (artist.isNotBlank()) "$illustTitle - $artist | $appName" else "$illustTitle | $appName"
                        }

                        uiState.showUserPage && uiState.selectedUserName != null -> {
                            "${uiState.selectedUserName} | $appName"
                        }

                        uiState.activeSearchWord.isNotBlank() -> {
                            "${uiState.activeSearchWord} | $appName"
                        }

                        else -> {
                            appName
                        }
                    }
                title = pageTitle
            }

            LaunchedEffect(settingsLoaded) {
                if (!settingsLoaded) return@LaunchedEffect
                androidx.compose.runtime.withFrameNanos { }
                this@MainActivity.lifecycleScope.launch {
                    reportFullyDrawn()
                    registerProcessLifecycleObserverIfNeeded()
                    enableHandoffIfSupported()
                    kotlinx.coroutines.delay(STARTUP_POST_WORK_DELAY_MS)
                    viewModel.loadDeferredStartupData()
                    (application as IllustiaApplication).startPostStartupWork()
                }
            }

            val fontFamily =
                remember(settings.appFont) {
                    resolveAppFontFamily(settings.appFont)
                }
            val textStyles =
                remember(settings.appFont) {
                    resolveAppTextStyles(fontFamily)
                }
            LaunchedEffect(dynamicHzController) {
                dynamicHzController.currentMode.collect { mode ->
                    applyAdaptiveRefreshRateHint(mode)
                }
            }

            MiuixTheme(colors = themeColors, textStyles = textStyles) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = fontFamily)),
                    com.yunfie.illustia.platform.LocalDynamicHzController provides dynamicHzController,
                ) {
                    if (settingsLoaded) {
                        IllustiaApp(viewModel)
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MiuixTheme.colorScheme.surface,
                        ) { }
                    }
                }
            }
        }
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            AppShortcutRouter.accept(intent)
            viewModel.handleIncomingIntent(intent)
            intent.data = null
            intent.removeExtra(NativeIntentRouter.EXTRA_HANDOFF_URI)
            intent.action = Intent.ACTION_MAIN
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        applyTaskSnapshotBlur(isEnteringBackground = true)
    }

    override fun onResume() {
        super.onResume()
        applyTaskSnapshotBlur(isEnteringBackground = false)
        applyAdaptiveRefreshRateHint(dynamicHzController.currentMode.value)
        openPixivUrlFromClipboardIfNeeded()
    }

    override fun onPause() {
        clipboardDetectionJob?.cancel()
        clipboardDetectionJob = null
        clearAdaptiveRefreshRateHint()
        applyTaskSnapshotBlur(isEnteringBackground = true)
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
            AppShortcutRouter.accept(intent)
            viewModel.handleIncomingIntent(intent)
            intent.data = null
            intent.removeExtra(NativeIntentRouter.EXTRA_HANDOFF_URI)
            intent.action = Intent.ACTION_MAIN
        }
    }

    @RequiresApi(PlatformCapabilities.HANDOFF_API)
    override fun onHandoffActivityDataRequested(handoffRequestInfo: HandoffActivityDataRequestInfo): HandoffActivityData {
        val state = viewModel.uiState.value
        val activityComponent = ComponentName(this, MainActivity::class.java)
        val handoffUri = currentHandoffUri(state)
        val fallbackUri =
            when {
                state.appLocked || state.privacyLocked -> Uri.parse("https://www.pixiv.net/")
                handoffUri?.host == "users" -> Uri.parse("https://www.pixiv.net/users/${handoffUri.lastPathSegment}")
                handoffUri?.host == "illusts" -> Uri.parse("https://www.pixiv.net/artworks/${handoffUri.lastPathSegment}")
                else -> Uri.parse("https://www.pixiv.net/")
            }
        val extras =
            PersistableBundle().apply {
                handoffUri?.let { putString(NativeIntentRouter.EXTRA_HANDOFF_URI, it.toString()) }
            }
        return HandoffActivityData
            .Builder(activityComponent)
            .setExtras(extras)
            .setFallbackUri(fallbackUri)
            .build()
    }

    private fun enableHandoffIfSupported() {
        if (!PlatformCapabilities.supportsActivityHandoff()) return

        val params =
            HandoffActivityParams
                .Builder()
                .setAllowHandoffWithoutPackageInstalled(true)
                .build()
        setHandoffEnabled(true, params)
    }

    private fun registerProcessLifecycleObserverIfNeeded() {
        if (processLifecycleObserver != null) return
        val lifecycleObserver =
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (viewModel.shouldLockOnReturn()) {
                        viewModel.lockApp()
                    }
                }
            }
        processLifecycleObserver = lifecycleObserver
        androidx.lifecycle.ProcessLifecycleOwner
            .get()
            .lifecycle
            .addObserver(lifecycleObserver)
    }

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (
            PlatformCapabilities.requiresLegacyStoragePermission() &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                LEGACY_STORAGE_PERMISSION_REQUEST_CODE,
            )
        }
    }

    private fun currentHandoffUri(state: IllustiaUiState): Uri? {
        if (state.appLocked || state.privacyLocked) return null
        return when {
            state.showUserPage && state.selectedUser != null -> {
                Uri.parse("pixiv://users/${state.selectedUser.id}")
            }

            state.imageViewerIllust != null -> {
                Uri.parse(
                    "pixiv://illusts/${state.imageViewerIllust.id}?page=${state.imageViewerCurrentPage}",
                )
            }

            state.selectedIllust != null -> {
                Uri.parse("pixiv://illusts/${state.selectedIllust.id}")
            }

            else -> {
                null
            }
        }
    }

    private fun openPixivUrlFromClipboardIfNeeded() {
        if (!viewModel.uiState.value.settings.autoDetectClipboard) return
        clipboardDetectionJob?.cancel()
        clipboardDetectionJob =
            lifecycleScope.launch {
                val text =
                    withContext(Dispatchers.IO) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        runCatching {
                            clipboard.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(this@MainActivity)
                                ?.toString()
                                ?.trim()
                        }.getOrNull().orEmpty()
                    }
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@launch
                if (text.isNotBlank() && text != lastHandledClipboardText && NativeIntentRouter.parseText(text) != null) {
                    lastHandledClipboardText = text
                    viewModel.handleClipboardText(text)
                }
            }
        }
    }

    private fun applySecureWindow(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            // Android 13+ ではタスク切替画面のスクリーンショットも無効化
            if (PlatformCapabilities.supportsRecentsScreenshotControl()) {
                setRecentsScreenshotEnabled(false)
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (PlatformCapabilities.supportsRecentsScreenshotControl()) {
                setRecentsScreenshotEnabled(true)
            }
        }
    }

    private fun applyTaskSnapshotBlur(isEnteringBackground: Boolean) {
        val settings = viewModel.uiState.value.settings
        if (settings.secureWindow) return

        val isBlurEnabled = settings.isFeatureEnabled(com.yunfie.illustia.settings.FeatureFlag.TaskSnapshotBlur)

        if (isEnteringBackground) {
            if (!isBlurEnabled) return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val blurEffect =
                    RenderEffect.createBlurEffect(
                        TASK_SNAPSHOT_BLUR_RADIUS,
                        TASK_SNAPSHOT_BLUR_RADIUS,
                        Shader.TileMode.CLAMP,
                    )
                window.decorView.setRenderEffect(blurEffect)
                isSnapshotBlurApplied = true
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                isSnapshotBlurApplied = true
            }
        } else {
            if (isSnapshotBlurApplied) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.decorView.setRenderEffect(null)
                } else {
                    val appLocked = viewModel.uiState.value.appLocked
                    if (!settings.secureWindow && !(appLocked && settings.appLockEnabled)) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
                isSnapshotBlurApplied = false
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                window.decorView.setRenderEffect(null)
            }
        }
    }

    private fun applyNotchOptimization(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val desiredMode =
                if (enabled) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            if (window.attributes.layoutInDisplayCutoutMode != desiredMode) {
                window.attributes =
                    window.attributes.apply {
                        layoutInDisplayCutoutMode = desiredMode
                    }
            }
        }
    }

    private fun hasDisplayArrSupport(display: Display): Boolean =
        runCatching {
            val method = Display::class.java.getMethod("hasArrSupport")
            (method.invoke(display) as? Boolean) == true
        }.getOrDefault(false)

    private fun resolveArrRefreshRate(
        display: Display,
        mode: com.yunfie.illustia.platform.DynamicHzMode,
    ): Float =
        runCatching {
            val category =
                when (mode) {
                    com.yunfie.illustia.platform.DynamicHzMode.PowerSaving -> 1
                    com.yunfie.illustia.platform.DynamicHzMode.Normal -> 2
                    com.yunfie.illustia.platform.DynamicHzMode.Boost -> 3
                }
            val method = Display::class.java.getMethod("getSuggestedFrameRate", Int::class.javaPrimitiveType)
            (method.invoke(display, category) as? Float) ?: REFRESH_RATE_NORMAL
        }.getOrDefault(REFRESH_RATE_NORMAL)

    private fun resolveLegacyRefreshRate(
        display: Display,
        mode: com.yunfie.illustia.platform.DynamicHzMode,
    ): Float {
        val rates = display.supportedModes.map { it.refreshRate }
        return when (mode) {
            com.yunfie.illustia.platform.DynamicHzMode.PowerSaving -> {
                rates.filter { it >= REFRESH_RATE_LOW_MIN }.minOrNull() ?: REFRESH_RATE_NORMAL
            }

            com.yunfie.illustia.platform.DynamicHzMode.Normal -> {
                REFRESH_RATE_NORMAL
            }

            com.yunfie.illustia.platform.DynamicHzMode.Boost -> {
                rates.maxOrNull() ?: REFRESH_RATE_HIGH_MAX
            }
        }
    }

    private fun applyAdaptiveRefreshRateHint(mode: com.yunfie.illustia.platform.DynamicHzMode = dynamicHzController.currentMode.value) {
        if (!PlatformCapabilities.supportsRefreshRateHint()) return

        val display = window.decorView.display ?: return
        val preferredRefreshRate =
            if (PlatformCapabilities.supportsAdaptiveRefreshRate() && hasDisplayArrSupport(display)) {
                resolveArrRefreshRate(display, mode)
            } else {
                resolveLegacyRefreshRate(display, mode)
            }

        if (preferredRefreshRate <= 0f || appliedRefreshRateHint == preferredRefreshRate) return

        window.attributes =
            window.attributes.apply {
                this.preferredRefreshRate = preferredRefreshRate
            }
        appliedRefreshRateHint = preferredRefreshRate
    }

    private fun clearAdaptiveRefreshRateHint() {
        if (appliedRefreshRateHint == null || !PlatformCapabilities.supportsRefreshRateHint()) return

        window.attributes =
            window.attributes.apply {
                preferredRefreshRate = 0f
            }
        appliedRefreshRateHint = null
    }

    private suspend fun updateRecentsTaskDescription(settings: com.yunfie.illustia.settings.AppSettings) {
        if (!settings.privacyModeEnabled) return

        val title =
            if (settings.hideRecents) {
                settings.dummyAppName.ifBlank { getString(R.string.app_name_dummy) }
            } else {
                getString(R.string.app_name)
            }

        val iconRes =
            if (settings.hideRecents) {
                resources.getIdentifier(settings.dummyIconVariant, "mipmap", packageName)
            } else {
                R.mipmap.ic_launcher
            }

        val iconBitmap =
            withContext(Dispatchers.IO) {
                if (iconRes != 0) {
                    BitmapFactory.decodeResource(resources, iconRes)
                } else {
                    BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
                }
            }

        val taskDesc = ActivityManager.TaskDescription(title, iconBitmap)
        setTaskDescription(taskDesc)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isDark = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (appliedDarkTheme != isDark) {
            appliedDarkTheme = isDark
        }
    }

    private fun applyAppLanguage(language: String) {
        if (appliedAppLanguage == language) return
        appliedAppLanguage = language
        val isSystemAlreadyConfigured =
            language == "system" &&
                if (PlatformCapabilities.supportsPlatformLocaleManager()) {
                    getSystemService(LocaleManager::class.java)?.applicationLocales?.isEmpty != false
                } else {
                    AppCompatDelegate.getApplicationLocales().isEmpty
                }
        if (isSystemAlreadyConfigured) return

        if (PlatformCapabilities.supportsPlatformLocaleManager()) {
            val localeManager = getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                localeManager.applicationLocales = appLanguageLocaleList(language)
            }
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(
                    when (language) {
                        "ja" -> "ja-JP"
                        "en" -> "en-US"
                        "ko" -> "ko-KR"
                        "zh-Hans" -> "zh-Hans"
                        "zh-Hant" -> "zh-Hant"
                        else -> ""
                    },
                ),
            )
        }
    }

    private fun resolveAppFontFamily(value: String): FontFamily =
        when (AppFont.fromValue(value)) {
            AppFont.System -> {
                FontFamily.Default
            }

            AppFont.MiSans -> {
                MiSansFontFamily
            }
        }

    private fun resolveAppTextStyles(fontFamily: FontFamily): TextStyles {
        val base = defaultTextStyles()
        if (fontFamily == FontFamily.Default) return base
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

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.metaState and KeyEvent.META_CTRL_ON == 0 &&
            (event.source and InputDevice.SOURCE_CLASS_POINTER != 0)
        ) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            if (vScroll != 0f || hScroll != 0f) {
                val pointerCount = event.pointerCount
                val pointerProperties =
                    Array(pointerCount) { i ->
                        MotionEvent.PointerProperties().also { event.getPointerProperties(i, it) }
                    }
                val pointerCoords =
                    Array(pointerCount) { i ->
                        MotionEvent.PointerCoords().also {
                            event.getPointerCoords(i, it)
                            it.setAxisValue(MotionEvent.AXIS_VSCROLL, it.getAxisValue(MotionEvent.AXIS_VSCROLL) * 2.2f)
                            it.setAxisValue(MotionEvent.AXIS_HSCROLL, it.getAxisValue(MotionEvent.AXIS_HSCROLL) * 2.2f)
                        }
                    }
                val modifiedEvent =
                    MotionEvent.obtain(
                        event.downTime,
                        event.eventTime,
                        event.action,
                        pointerCount,
                        pointerProperties,
                        pointerCoords,
                        event.metaState,
                        event.buttonState,
                        event.xPrecision,
                        event.yPrecision,
                        event.deviceId,
                        event.edgeFlags,
                        event.source,
                        event.flags,
                    )
                val handled = super.dispatchGenericMotionEvent(modifiedEvent)
                modifiedEvent.recycle()
                if (handled) return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    var desktopShortcutHandler: ((com.yunfie.illustia.platform.DesktopCommand) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && handleKeyDown(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleKeyDown(event: KeyEvent): Boolean {
        com.yunfie.illustia.platform.desktopCommand(event)?.let { command ->
            return event.repeatCount > 0 || desktopShortcutHandler?.invoke(command) == true
        }
        val isAlt = event.isAltPressed
        return when {
            event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                (isAlt && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -> {
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                    true
                } else {
                    false
                }
            }

            else -> {
                false
            }
        }
    }

    override fun onDestroy() {
        processLifecycleObserver?.let { observer ->
            androidx.lifecycle.ProcessLifecycleOwner
                .get()
                .lifecycle
                .removeObserver(observer)
            processLifecycleObserver = null
        }
        super.onDestroy()
    }
}
