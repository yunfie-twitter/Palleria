package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.yunfie.illustia.models.Illust

/**
 * 作品カードにプラットフォーム固有の修飾を足すための契約。
 * 例: デスクトップ環境での右クリックメニュー。
 */
fun interface ArtworkCardDecorator {
    @Composable
    fun decorate(
        illust: Illust,
        modifier: Modifier,
    ): Modifier
}

val LocalArtworkCardDecorator = compositionLocalOf<ArtworkCardDecorator?> { null }

@Composable
fun Modifier.artworkCardDecoration(illust: Illust): Modifier {
    val decorator = LocalArtworkCardDecorator.current ?: return this
    return decorator.decorate(illust, this)
}
