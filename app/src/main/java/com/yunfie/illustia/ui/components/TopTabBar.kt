package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val TopTabBarHeight = 32.dp
private val TopTabBarInnerPadding = 2.dp
private val TopTabBarCornerRadius = 14.dp

/**
 * 上部タブ（タイトル直下）の共通実装。
 *
 * 以前は各画面が `TabRowWithContour` と背景色・枠線の組み合わせを毎回書き写しており、
 * 見た目が揃わず、スワイプ追従の修正も画面ごとに散らばっていた。
 *
 * miuix の `TabRowWithContour` は選択位置を Int でしか受け取れないため、ドラッグ中に
 * 選択表示が指から 1 ページ分遅れる。ここでは選択位置を Float で受け取り、指の移動に
 * 合わせてハイライトが連続的に滑るようにしている（配色は miuix のタブ列に合わせている）。
 *
 * 端ではハイライトが 0 と lastIndex に飽和し、循環しない。
 *
 * @param selectedTabIndex 指の位置に追従した連続値（例: 1.35 = タブ1とタブ2の間）。
 */
@Composable
internal fun TopTabBar(
    tabs: List<String>,
    selectedTabIndex: Float,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isBlurEnabled: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor =
        if (isBlurEnabled) {
            if (isDark) {
                MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
            } else {
                MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
            }
        } else {
            MiuixTheme.colorScheme.surfaceContainer
        }
    val selectedColor =
        if (isDark) {
            Color.White.copy(alpha = if (isBlurEnabled) 0.18f else 0.16f)
        } else {
            Color.White.copy(alpha = if (isBlurEnabled) 0.95f else 0.92f)
        }
    val contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val selectedContentColor = MiuixTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(TopTabBarCornerRadius)
    val borderModifier =
        if (isBlurEnabled) {
            Modifier.squircleBorder(
                width = 0.75.dp,
                color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                cornerRadius = TopTabBarCornerRadius,
            )
        } else {
            Modifier
        }
    val fraction = selectedTabIndex.coerceIn(0f, (tabs.size - 1).toFloat())
    val selectedIndex = fraction.roundToInt()

    BoxWithConstraints(
        modifier =
            modifier
                .then(borderModifier)
                .pointerHoverIcon(PointerIcon.Hand)
                .fillMaxWidth()
                .height(TopTabBarHeight)
                .clip(shape)
                .background(containerColor)
                .padding(TopTabBarInnerPadding),
    ) {
        val segmentWidth = maxWidth / tabs.size
        // 選択ハイライトは背面レイヤー。文字より先に描き、指の位置 (fraction) に追従させる。
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .offset(x = segmentWidth * fraction)
                    .clip(shape)
                    .background(selectedColor),
        )
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) selectedContentColor else contentColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        style = MiuixTheme.textStyles.body2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Pager の「指の位置」を連続値で返す。
 *
 * `currentPage` だけを見るとドラッグ中はタブの選択表示が指から 1 ページ分遅れる。
 * `currentPageOffsetFraction` を足すことで遷移中もタブ表示が指に追従する。
 * 端では 0 と lastIndex に飽和する（循環しない）。
 */
@Composable
internal fun rememberPagerTabIndex(pagerState: PagerState): Float =
    (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, (pagerState.pageCount - 1).coerceAtLeast(0).toFloat())

/**
 * 左スワイプで右隣、右スワイプで左隣。端では停止し、循環しない。
 *
 * Pager 自体は pageCount を超えて移動しないが、タブのタップ移動が範囲外インデックスを
 * 要求しても壊れないようにここで丸める。
 */
internal fun clampTabIndex(
    index: Int,
    pageCount: Int,
): Int = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
