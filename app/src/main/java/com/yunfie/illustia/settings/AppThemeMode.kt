package com.yunfie.illustia.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

enum class AppThemeMode(
    val value: String,
) {
    System("system"),
    Light("light"),
    Dark("dark");

    companion object {
        fun fromValue(value: String): AppThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

fun appThemeOptions(): List<String> = AppThemeMode.entries.map { it.value }

fun appThemeColorSchemeMode(value: String): ColorSchemeMode {
    return when (AppThemeMode.fromValue(value)) {
        AppThemeMode.System -> ColorSchemeMode.System
        AppThemeMode.Light -> ColorSchemeMode.Light
        AppThemeMode.Dark -> ColorSchemeMode.Dark
    }
}

@Composable
fun appThemeLabel(value: String): String {
    return when (AppThemeMode.fromValue(value)) {
        AppThemeMode.System -> stringResource(R.string.theme_system)
        AppThemeMode.Light -> stringResource(R.string.theme_light)
        AppThemeMode.Dark -> stringResource(R.string.theme_dark)
    }
}
