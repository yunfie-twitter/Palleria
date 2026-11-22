package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.TagPreview
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.VolumeOff
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 刷新したタグメニュー。
 *
 * シートの背景全面に作品画像を敷き、下部を暗いグラデーションで覆うことで、タグの
 * ビジュアルを主役にしつつ操作ボタンの可読性を保つ。以前の [TagPreviewBottomSheet] が
 * 上部にヒーローカードを置いていたのに対し、ここでは余白を減らして1画面で操作できる。
 */
@Composable
fun TagMenuBottomSheet(
    preview: TagPreview,
    isFavorite: Boolean,
    isMuted: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMute: () -> Unit,
) {
    AppBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = null,
        onDismissRequest = onDismiss,
        backgroundColor = Color.Transparent,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(26.dp)),
        ) {
            TagMenuBackdrop(preview)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = "#${preview.tag}",
                        color = Color.White,
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tagMenuActionButtonColors(),
                ) {
                    TagMenuActionContent(
                        icon = { Icon(MiuixIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = stringResource(R.string.tag_sheet_search),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                Button(
                    onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tagMenuActionButtonColors(),
                ) {
                    TagMenuActionContent(
                        icon = {
                            Icon(
                                if (isFavorite) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        label =
                            stringResource(
                                if (isFavorite) {
                                    R.string.tag_sheet_remove_favorite
                                } else {
                                    R.string.tag_sheet_add_favorite
                                },
                            ),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                Button(
                    onClick = onToggleMute,
                    modifier = Modifier.fillMaxWidth(),
                    colors = tagMenuActionButtonColors(),
                ) {
                    TagMenuActionContent(
                        icon = {
                            Icon(
                                if (isMuted) MiuixIcons.VolumeUp else MiuixIcons.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        label =
                            stringResource(
                                if (isMuted) {
                                    R.string.tag_sheet_unmute
                                } else {
                                    R.string.tag_sheet_mute
                                },
                            ),
                        tint = if (isMuted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagMenuBackdrop(preview: TagPreview) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (preview.imageUrl != null) {
                            Modifier.cachedVerticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.72f),
                                ),
                            )
                        } else {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        MiuixTheme.colorScheme.primary,
                                        MiuixTheme.colorScheme.primaryContainer,
                                    ),
                                ),
                            )
                        },
                    ),
        )
    }
}

@Composable
private fun TagMenuActionContent(
    icon: @Composable () -> Unit,
    label: String,
    tint: Color,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) { icon() }
        Text(
            text = label,
            color = tint,
            style = MiuixTheme.textStyles.button,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
