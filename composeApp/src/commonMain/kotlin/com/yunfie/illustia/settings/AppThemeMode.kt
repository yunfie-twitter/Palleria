package com.yunfie.illustia.settings

import androidx.compose.runtime.Composable
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.theme_dark
import com.yunfie.illustia.resources.theme_light
import com.yunfie.illustia.resources.theme_system
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

enum class AppThemeMode(
    val value: String,
) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromValue(value: String): AppThemeMode = entries.firstOrNull { it.value == value } ?: System
    }
}

fun appThemeOptions(): List<String> = AppThemeMode.entries.map { it.value }

fun appThemeColorSchemeMode(value: String): ColorSchemeMode =
    when (AppThemeMode.fromValue(value)) {
        AppThemeMode.System -> ColorSchemeMode.System
        AppThemeMode.Light -> ColorSchemeMode.Light
        AppThemeMode.Dark -> ColorSchemeMode.Dark
    }

@Composable
fun appThemeLabel(value: String): String =
    when (AppThemeMode.fromValue(value)) {
        AppThemeMode.System -> stringResource(Res.string.theme_system)
        AppThemeMode.Light -> stringResource(Res.string.theme_light)
        AppThemeMode.Dark -> stringResource(Res.string.theme_dark)
    }
