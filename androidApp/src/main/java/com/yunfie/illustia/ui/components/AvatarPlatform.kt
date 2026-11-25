package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale

/**
 * 共有 UI の [AvatarImage] に対する Android 実装。
 * Coil + Bitmap のデコードを伴うため androidApp 側に置く。
 */
@Composable
fun ProvideAvatarImageRenderer(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAvatarImageRenderer provides
            AvatarImageRenderer { url, contentDescription, modifier, maxDecodeDimensionPx ->
                PixivImage(
                    url = url,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = modifier,
                    thumbnail = true,
                    maxDecodeDimensionPx = maxDecodeDimensionPx,
                )
            },
        content = content,
    )
}
