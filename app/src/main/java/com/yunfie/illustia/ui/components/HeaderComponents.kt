package com.yunfie.illustia.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─────────────────────────────────────────────
//  CollapsingHeader
//  スクロール量に応じて「大ヘッダー → ミニヘッダー」を
//  スムーズにアニメーション切り替えするコンポーネント。
// ─────────────────────────────────────────────

/**
 * スクロールに連動してヘッダーを折り畳む / 展開するコンポーネント。
 *
 * @param scrollOffset  LazyGridState / LazyListState の firstVisibleItemScrollOffset (px)
 * @param firstIndex    LazyGridState / LazyListState の firstVisibleItemIndex
 * @param collapseThresholdPx  このピクセルを超えると折り畳みが始まる（デフォルト 90px）
 * @param title         大ヘッダー時のタイトル文字列
 * @param subtitle      大ヘッダー時のサブタイトル（省略可）
 * @param actions       右端に並ぶアクションボタン群
 * @param onTitleClick  折り畳みヘッダーのタイトルをタップしたときのコールバック（主にトップへスクロール）
 * @param miniContent   折り畳みヘッダー内中央に追加で表示するコンテンツ（省略可）
 */
@Composable
fun CollapsingHeader(
    scrollOffset: Int,
    firstIndex: Int,
    title: String,
    subtitle: String? = null,
    collapseThresholdPx: Int = 90,
    actions: @Composable RowScope.() -> Unit = {},
    onTitleClick: (() -> Unit)? = null,
    miniContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 折り畳み判定: index > 0 か、index == 0 でも閾値超えたら折り畳み
    val isCollapsed = remember(firstIndex, scrollOffset) {
        firstIndex > 0 || scrollOffset > collapseThresholdPx
    }

    // 大ヘッダーの透明度 (スクロールに応じて 1f → 0f)
    val expandAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f
        else (1f - scrollOffset.toFloat() / collapseThresholdPx.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160),
        label = "collapsingHeader-expandAlpha",
    )

    // ミニヘッダーの透明度 (逆方向)
    val miniAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "collapsingHeader-miniAlpha",
    )

    val scheme = MiuixTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth()) {
        // ── 大ヘッダー ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = expandAlpha }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = scheme.onBackground,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = scheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(content = actions)
        }

        // ── ミニヘッダー (sticky) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = miniAlpha }
                .background(scheme.background.copy(alpha = 0.9f))
                .then(
                    if (onTitleClick != null) Modifier.miuixClickable(onClick = onTitleClick) else Modifier,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = scheme.onBackground,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
            )
            if (miniContent != null) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    miniContent()
                }
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Row(content = actions)
            }
        }
    }
}
