package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * ハプティクス実行の抽象。実装はプラットフォーム側 (androidApp) が提供する。
 *
 * Vibrator API は Android 専用のため、共有 UI からはこの CompositionLocal 経由で呼ぶ。
 */
enum class AppHapticEffect {
    Click,
    Toggle,
    Success,
    Error,
    BookmarkBurst,
    BoundaryLimit,
    ThresholdSnap,
    WheelTick,
    Peek,
    Dismiss,
}

val LocalAppHapticEffect = compositionLocalOf<(AppHapticEffect) -> Unit> { { } }

val LocalAppHapticMode = compositionLocalOf { com.yunfie.illustia.settings.AppHapticMode.Rich }

@Composable
fun ProvideAppHaptics(
    mode: com.yunfie.illustia.settings.AppHapticMode,
    effect: (AppHapticEffect) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppHapticMode provides mode,
        LocalAppHapticEffect provides effect,
        content = content,
    )
}

/**
 * 戻るジェスチャを画面側から抑止・通知するための抽象。
 *
 * androidx.activity の `BackHandler` は Android 専用のため、共有 UI からはこの
 * CompositionLocal 経由で登録する。実装は androidApp 側が提供する。
 */
fun interface BackHandlerRegistrar {
    @Composable
    fun Register(
        enabled: Boolean,
        onBack: () -> Unit,
    )
}

val LocalBackHandler = compositionLocalOf<BackHandlerRegistrar?> { null }

/** ロック画面のように「戻る操作を握り潰す」だけの用途。 */
@Composable
fun BlockBackNavigation() {
    LocalBackHandler.current?.Register(enabled = true, onBack = {})
}
