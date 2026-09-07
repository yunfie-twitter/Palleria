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
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
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
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.appLanguageLocaleList
import com.yunfie.illustia.settings.isAppDarkTheme
import com.yunfie.illustia.settings.rememberAppThemeColors
import com.yunfie.illustia.ui.IllustiaApp
import com.yunfie.illustia.ui.components.PixivImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

class MainActivity : FragmentActivity() {
    private companion object {
        const val LEGACY_STORAGE_PERMISSION_REQUEST_CODE = 25
    }

    private val viewModel by viewModels<IllustiaViewModel> {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
            .getInstance(application)
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

        // core-splashscreen の互換実装を使い、API 25 以降で同じフェードアウトにする。
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            android.animation.ObjectAnimator
                .ofFloat(
                    splashScreenView.view,
                    android.view.View.ALPHA,
                    1f,
                    0f,
                ).apply {
                    duration = 220L
                    interpolator = AccelerateDecelerateInterpolator()
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
        super.onCreate(savedInstanceState)
        requestLegacyStoragePermissionIfNeeded()
        applyAppLanguage(SettingsStore.readStoredAppLanguage(applicationContext))
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        lastHandledClipboardText =
            runCatching {
                clipboard
                    ?.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(this)
                    ?.toString()
                    ?.trim()
            }.getOrNull()

        // Observe app lifecycle for lock-on-return
        val lifecycleObserver =
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    if (viewModel.shouldLockOnReturn()) {
                        viewModel.lockApp()
                    }
                }
            }
        androidx.lifecycle.ProcessLifecycleOwner
            .get()
            .lifecycle
            .addObserver(lifecycleObserver)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val settings = uiState.settings
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

            LaunchedEffect(settingsLoaded, settings.secureWindow) {
                if (!settingsLoaded) return@LaunchedEffect
                applySecureWindow(settings.secureWindow)
            }

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

            LaunchedEffect(settingsLoaded, settings.themeMode, systemDark) {
                if (!settingsLoaded) return@LaunchedEffect
                val isDarkTheme = isAppDarkTheme(settings.themeMode, systemDark)
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

            LaunchedEffect(settingsLoaded, settings.privacyModeEnabled, settings.dummyAppName, settings.dummyIconVariant) {
                if (!settingsLoaded) return@LaunchedEffect
                viewModel.applyDummyIconSettings(this@MainActivity)
            }

            LaunchedEffect(
                settingsLoaded,
                uiState.selectedIllust?.title,
                uiState.selectedUser?.name,
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

                        uiState.showUserPage && uiState.selectedUser != null -> {
                            "${uiState.selectedUser?.name} | $appName"
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
                window.decorView.post {
                    viewModel.loadDeferredStartupData()
                    (application as IllustiaApplication).startPostStartupWork()
                    reportFullyDrawn()
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
            MiuixTheme(colors = themeColors, textStyles = textStyles) {
                CompositionLocalProvider(
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = fontFamily)),
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
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text =
            runCatching {
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

    private fun applyAdaptiveRefreshRateHint() {
        if (!PlatformCapabilities.supportsRefreshRateHint()) return

        val display = window.decorView.display ?: return
        val preferredRefreshRate =
            when {
                PlatformCapabilities.supportsAdaptiveRefreshRate() && display.hasArrSupport() -> {
                    display.getSuggestedFrameRate(Display.FRAME_RATE_CATEGORY_NORMAL)
                }

                else -> {
                    60f
                }
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

    private fun updateRecentsTaskDescription(settings: com.yunfie.illustia.settings.AppSettings) {
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
            if (iconRes != 0) {
                BitmapFactory.decodeResource(resources, iconRes)
            } else {
                BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            }

        val taskDesc = ActivityManager.TaskDescription(title, iconBitmap)
        setTaskDescription(taskDesc)
    }

    private fun applyAppLanguage(language: String) {
        if (PlatformCapabilities.supportsPlatformLocaleManager()) {
            val localeManager = getSystemService(LocaleManager::class.java) ?: return
            localeManager.applicationLocales = appLanguageLocaleList(language)
            return
        }

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

    private fun resolveAppFontFamily(value: String): FontFamily =
        when (AppFont.fromValue(value)) {
            AppFont.System -> {
                FontFamily.Default
            }

            AppFont.MiSans -> {
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

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isCtrl = event.isCtrlPressed
            val isAlt = event.isAltPressed
            when {
                isCtrl && event.keyCode == KeyEvent.KEYCODE_R -> {
                    val state = viewModel.uiState.value
                    when {
                        state.selectedIllust != null -> viewModel.refreshIllustDetail(state.selectedIllust.id)
                        state.showUserPage && state.selectedUser != null -> viewModel.openUserPage(state.selectedUser.id)
                        else -> viewModel.refreshHome()
                    }
                    return true
                }

                isCtrl && event.keyCode == KeyEvent.KEYCODE_F -> {
                    AppShortcutRouter.trigger(AppShortcutDestination.Search)
                    return true
                }

                event.keyCode == KeyEvent.KEYCODE_ESCAPE ||
                    (isAlt && event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -> {
                    if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                        onBackPressedDispatcher.onBackPressed()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
