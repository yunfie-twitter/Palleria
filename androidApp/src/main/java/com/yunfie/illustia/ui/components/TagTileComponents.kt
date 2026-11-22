package com.yunfie.illustia.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val RECOMMENDED_TAGS_SKELETON_ITEM_COUNT = 12

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagTile(
    tag: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val performHaptic = rememberHapticFeedbackAction()
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .pointerHoverIcon(PointerIcon.Hand)
                .combinedClickable(
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onClick()
                    },
                    role = Role.Button,
                    onLongClick =
                        onLongClick?.let { longClick ->
                            {
                                performHaptic(AppHapticEffect.Click)
                                longClick()
                            }
                        },
                ),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (imageUrl != null) {
                PixivImage(
                    url = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
            }
            Text(
                text = tag,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
fun TagTileSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: State<Float>? = null,
) {
    val shimmerModifier = rememberSkeletonShimmer(shimmerValue)

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .then(shimmerModifier),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .fillMaxWidth(0.68f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(shimmerModifier),
        )
    }
}

@Composable
fun RecommendedTagsSkeleton(
    tagColumns: Int,
    modifier: Modifier = Modifier,
    itemCount: Int = RECOMMENDED_TAGS_SKELETON_ITEM_COUNT,
    shimmerValue: State<Float>? = null,
) {
    val shimmer = shimmerValue ?: rememberIllustSkeletonShimmer()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val rows = (itemCount + tagColumns - 1) / tagColumns
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                repeat(tagColumns) {
                    TagTileSkeleton(
                        shimmerValue = shimmer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
