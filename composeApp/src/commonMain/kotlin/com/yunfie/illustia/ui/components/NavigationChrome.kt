package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Consume the system navigation region once, including side buttons in landscape. */
@Composable
fun Modifier.appNavigationSafeArea(insets: WindowInsets = WindowInsets.navigationBars): Modifier = windowInsetsPadding(insets)

/**
 * システムナビゲーション領域を「確保しつつ、その帯をアプリの色で塗る」モディファイア。
 *
 * ルートで insets を消費すると、確保した帯には Compose のコンテンツが一切描かれない。
 * その状態で window のナビゲーションバーを透過にすると、帯は「最後に合成された古い
 * フレーム」や背後ウィンドウの内容をそのまま見せてしまい、画面遷移のたびに残像（ゴースト）
 * として現れる。window 側の色を複数箇所（MainActivity / 全画面復帰 / 各画面）で書き換えると
 * 透過と不透明が交互に適用されて点滅する。
 *
 * そこで帯の色は window ではなく Compose 側で確定させる。`drawBehind` は
 * `windowInsetsPadding` より外側に置き、ノードの領域（= insets を含む元サイズ）の
 * 下端 insets 分を塗る。内側の子は従来どおり insets 分だけ寄る。
 *
 * 塗る色は [LocalAppNavigationScrimColor] から取る。初期値は [MiuixTheme] の
 * `surfaceContainer`。画面側で [ProvideAppNavigationScrimColor] を使って
 * `Color.Transparent` に差し替えると、帯は確保したまま背後のコンテンツが透ける。
 */
@Composable
fun Modifier.appNavigationScrim(insets: WindowInsets = WindowInsets.navigationBars): Modifier {
    val color = LocalAppNavigationScrimColor.current ?: MiuixTheme.colorScheme.surfaceContainer
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottomPx = with(density) { insets.getBottom(this).toFloat() }
    return this
        .drawBehind {
            if (bottomPx > 0f) {
                drawRect(
                    color = color,
                    topLeft = Offset(0f, size.height - bottomPx),
                    size = Size(size.width, bottomPx),
                )
            }
        }.windowInsetsPadding(insets)
}

/**
 * [appNavigationScrim] が塗るナビ帯の色。
 *
 * 既定は `surfaceContainer`。`Color.Transparent` を提供すると帯は塗られず、
 * 背後のコンテンツがそのまま見える（作品ページ・プロフィールページなど）。
 */
val LocalAppNavigationScrimColor = compositionLocalOf<Color?> { null }

/** [LocalAppNavigationScrimColor] を上書きする。null を渡すとテーマ既定色に戻る。 */
@Composable
fun ProvideAppNavigationScrimColor(
    color: Color?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppNavigationScrimColor provides color, content = content)
}

@Composable
fun AppNavigationBar(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        mode =
            if (FeatureFlag.NavigationIconsOnly.resolve(settings)) {
                NavigationBarDisplayMode.IconOnly
            } else {
                NavigationBarDisplayMode.IconAndText
            },
        defaultWindowInsetsPadding = false,
        color = color,
        showDivider = showDivider,
        content = content,
    )
}
