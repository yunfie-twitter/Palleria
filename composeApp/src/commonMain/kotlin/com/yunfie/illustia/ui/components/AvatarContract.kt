package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * アバター画像の描画実装。Coil の Bitmap デコードは Android 依存のため、
 * 共有 UI はこの CompositionLocal 経由でプラットフォーム実装に委ねる。
 */
fun interface AvatarImageRenderer {
    @Composable
    fun Render(
        url: String,
        contentDescription: String?,
        modifier: Modifier,
        maxDecodeDimensionPx: Int,
    )
}

val LocalAvatarImageRenderer = compositionLocalOf<AvatarImageRenderer?> { null }

const val AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX = 256

/**
 * 丸いアバター。描画実装が未提供の場合はプレースホルダを出す。
 */
@Composable
fun AvatarImage(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    maxDecodeDimensionPx: Int = AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX,
) {
    val commonModifier =
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)

    val renderer = LocalAvatarImageRenderer.current
    if (url != null && renderer != null) {
        renderer.Render(
            url = url,
            contentDescription = name,
            modifier = commonModifier,
            maxDecodeDimensionPx = maxDecodeDimensionPx,
        )
    } else {
        Box(
            modifier = commonModifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "no image",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
