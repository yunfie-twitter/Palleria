package com.yunfie.illustia.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal val IllustCardCornerShape = RoundedCornerShape(14.dp)

/**
 * カード内のブックマークボタン実装。
 * ハートのアニメーションと触覚は Android 実装に依存するため、共有 UI は枠だけを持つ。
 */
fun interface IllustCardBookmarkButtonRenderer {
    @Composable
    fun Render(
        isBookmarked: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        onLongClick: (() -> Unit)?,
        size: androidx.compose.ui.unit.Dp,
        iconSize: androidx.compose.ui.unit.Dp,
    )
}

val LocalIllustCardBookmarkButtonRenderer =
    compositionLocalOf<IllustCardBookmarkButtonRenderer?> { null }
