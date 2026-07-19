package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.TagPreview
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun TagPreviewBottomSheet(
    preview: TagPreview,
    isFavorite: Boolean,
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMute: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.tag_sheet_title),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TagPreviewHero(preview)
            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.tag_sheet_search))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        text = stringResource(
                            if (isFavorite) R.string.tag_sheet_remove_favorite
                            else R.string.tag_sheet_add_favorite,
                        ),
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        text = stringResource(
                            if (isMuted) R.string.tag_sheet_unmute
                            else R.string.tag_sheet_mute,
                        ),
                        color = if (isMuted) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagPreviewHero(preview: TagPreview) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = Color.White,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            preview.imageUrl?.let { imageUrl ->
                PixivImage(
                    url = imageUrl,
                    contentDescription = preview.tag,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (preview.imageUrl != null) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.04f),
                                    Color.Black.copy(alpha = 0.82f),
                                ),
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.primaryContainer,
                                ),
                            )
                        },
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "#${preview.tag}",
                    color = Color.White,
                    style = MiuixTheme.textStyles.title1,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                )
            }
        }
    }
}
