package com.yunfie.illustia.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.Illust
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val IllustRowCornerShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustListRow(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onBookmarkLongClick: (() -> Unit)? = null,
) {
    val performHaptic = rememberAppHapticPerformer()
    val cardPreferences = LocalArtworkCardPreferences.current
    val pageBadgeText =
        remember(illust.id, illust.pageCount) {
            if (illust.pageCount > 1) "${illust.pageCount}P" else null
        }
    val ageRestrictionBadgeText =
        remember(illust.id, illust.isR18, illust.isR18G, cardPreferences.showR18Badge) {
            if (cardPreferences.showR18Badge) illust.ageRestrictionBadgeText else null
        }

    val baseModifier = Modifier.fillMaxWidth().artworkCardDecoration(illust)
    val finalModifier =
        if (cardPreferences.doubleTapToBookmark) {
            baseModifier
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = {
                        performHaptic(AppHapticEffect.Click)
                        onBookmark()
                    },
                    onLongClick =
                        if (onLongClick != null) {
                            {
                                performHaptic(AppHapticEffect.Click)
                                onLongClick()
                            }
                        } else {
                            null
                        },
                )
        } else {
            baseModifier
        }

    Card(
        modifier = finalModifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = if (cardPreferences.doubleTapToBookmark) null else onClick,
        onLongPress =
            if (!cardPreferences.doubleTapToBookmark && onLongClick != null) {
                {
                    performHaptic(AppHapticEffect.Click)
                    onLongClick()
                }
            } else {
                null
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(68.dp)
                        .clip(IllustRowCornerShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                PixivImage(
                    url = illust.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
                if (ageRestrictionBadgeText != null) {
                    Text(
                        text = ageRestrictionBadgeText,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .squircleBackground(Color(0xFFFA383E), 4.dp)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                if (pageBadgeText != null) {
                    Text(
                        text = pageBadgeText,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .squircleBackground(Color.Black.copy(alpha = 0.4f), 4.dp)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = illust.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.headline2,
                )
                Text(
                    text = illust.artistName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }

            if (cardPreferences.showBookmarkButton) {
                LocalIllustCardBookmarkButtonRenderer.current?.Render(
                    isBookmarked = illust.isBookmarked,
                    onClick = onBookmark,
                    modifier = Modifier,
                    onLongClick = onBookmarkLongClick,
                    size = 40.dp,
                    iconSize = 26.dp,
                )
            }
        }
    }
}

@Composable
fun HighlightCard(
    illust: Illust,
    onClick: () -> Unit,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
    Card(
        modifier = Modifier.width(232.dp).height(128.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PixivImage(
                url = illust.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
            val badgeText = illust.ageRestrictionBadgeText
            if (cardPreferences.showR18Badge && badgeText != null) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Black,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .squircleBackground(Color(0xFFFA383E), 6.dp)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .cachedVerticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
            )
            Text(
                text = illust.title,
                color = Color.White,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
    }
}
