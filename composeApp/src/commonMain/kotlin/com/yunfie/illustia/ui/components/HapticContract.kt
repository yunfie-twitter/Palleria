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

/**
 * 端末がハプティクスを持っているか。実装はプラットフォーム側が注入する。
 * 未注入の環境では false として扱い、設定項目を隠す。
 */
val LocalHapticsSupported = compositionLocalOf { false }

@Composable
fun isHapticsSupported(): Boolean = LocalHapticsSupported.current

/**
 * 端末が動的カラー (Monet 等) をサポートしているか。
 * 実装はプラットフォーム側が注入し、未注入の環境では false。
 */
val LocalDynamicColorAvailable = compositionLocalOf { false }

@Composable
fun isDynamicColorSupported(): Boolean = LocalDynamicColorAvailable.current

/**
 * 端末の画面が広色域 (Wide Color Gamut) を表示できるか。実装はプラットフォーム側が注入する。
 */
val LocalDisplayWideColorGamut = compositionLocalOf { false }

/** デスクトップ環境で動作しているか。実装はプラットフォーム側が注入する。 */
val LocalIsDesktopEnvironment = compositionLocalOf { false }

@Composable
fun isWideColorGamutDisplaySupported(): Boolean = LocalDisplayWideColorGamut.current

@Composable
fun isRunningOnDesktop(): Boolean = LocalIsDesktopEnvironment.current

/**
 * テキストを共有シートへ渡す入口。
 *
 * Android の Intent は専用 API のため、共有 UI からは [LocalTextSharing] 経由で呼ぶ。
 * 実装は androidApp が注入し、未注入の環境では何もしない。
 */
val LocalTextSharing = compositionLocalOf<(text: String) -> Unit> { { } }

@Composable
fun rememberTextSharing(): (String) -> Unit = LocalTextSharing.current

/**
 * 共有 UI からハプティクスを発火するための入口。
 *
 * 実装 (Vibrator) は androidApp が [LocalAppHapticEffect] に注入する。
 * 未注入の環境では何もしない。
 */
@Composable
fun rememberHapticFeedbackAction(): (AppHapticEffect) -> Unit = LocalAppHapticEffect.current

@Composable
fun ProvideAppHaptics(
    mode: com.yunfie.illustia.settings.AppHapticMode,
    hapticsSupported: Boolean,
    effect: (AppHapticEffect) -> Unit,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAppHapticMode provides mode,
        LocalAppHapticEffect provides effect,
        LocalHapticsSupported provides hapticsSupported,
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

/**
 * 戻る操作を [onBack] に繋ぐ。予測型戻るジェスチャの可否は実装側が判断する。
 */
@Composable
fun BackNavigationHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    LocalBackHandler.current?.Register(enabled = enabled, onBack = onBack)
}
