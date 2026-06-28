package com.yunfie.illustia.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.os.Build
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun rememberAppThemeColors(settings: AppSettings): Colors {
    val effectiveDark = when (settings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val useDynamicColor = settings.useDynamicColor && isDynamicColorAvailable()
    val controller = remember(settings.themeMode, useDynamicColor, settings.seedColor) {
        ThemeController(
            colorSchemeMode = settings.toColorSchemeMode(),
            keyColor = if (useDynamicColor) null else Color(settings.seedColor.toInt()),
        )
    }
    val colors = controller.currentColors()
    return if (settings.amoledMode && effectiveDark) {
        colors.toAmoledColors()
    } else {
        colors
    }
}

fun isDynamicColorAvailable(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

private fun AppSettings.toColorSchemeMode(): ColorSchemeMode {
    return when (themeMode) {
        "light" -> ColorSchemeMode.MonetLight
        "dark" -> ColorSchemeMode.MonetDark
        else -> ColorSchemeMode.MonetSystem
    }
}

private fun Colors.toAmoledColors(): Colors {
    val white = Color.White
    val black = Color.Black
    val panel = Color(0xFF121212)
    val panelHigh = Color(0xFF1A1A1A)
    val panelHighest = Color(0xFF202020)
    val panelVariant = Color(0xFF181818)
    return copy(
        background = black,
        onBackground = white,
        onBackgroundVariant = white.copy(alpha = 0.76f),
        surface = panel,
        onSurface = white,
        surfaceVariant = panelVariant,
        onSurfaceSecondary = white.copy(alpha = 0.8f),
        onSurfaceVariantSummary = white.copy(alpha = 0.74f),
        onSurfaceVariantActions = white.copy(alpha = 0.9f),
        disabledOnSurface = white.copy(alpha = 0.28f),
        surfaceContainer = panel,
        onSurfaceContainer = white,
        onSurfaceContainerVariant = white.copy(alpha = 0.82f),
        surfaceContainerHigh = panelHigh,
        onSurfaceContainerHigh = white,
        surfaceContainerHighest = panelHighest,
        onSurfaceContainerHighest = white,
        outline = white.copy(alpha = 0.2f),
        dividerLine = white.copy(alpha = 0.12f),
        windowDimming = black.copy(alpha = 0.6f),
        sliderBackground = white.copy(alpha = 0.12f),
        sliderKeyPoint = white,
        sliderKeyPointForeground = black,
    )
}
