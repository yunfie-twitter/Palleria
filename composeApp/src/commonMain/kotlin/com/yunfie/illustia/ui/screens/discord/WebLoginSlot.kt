package com.yunfie.illustia.ui.screens.discord

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * ログイン画面の WebView 表示領域。
 *
 * ブラウザ本体とトークン抽出は Android 側が担い、バーとレイアウトだけを共有する。
 */
val LocalWebLoginContent =
    compositionLocalOf<(@Composable (Modifier, PaddingValues) -> Unit)?> { null }
