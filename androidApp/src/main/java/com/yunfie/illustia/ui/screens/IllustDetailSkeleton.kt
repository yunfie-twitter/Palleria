package com.yunfie.illustia.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.screens.profile.UserProfileErrorScreen
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SHIMMER_DURATION_MILLIS = 1250
private const val SKELETON_SHIMMER_GRADIENT_WIDTH = 0.52f
private const val SKELETON_SHIMMER_START_X_INITIAL = -1f
private const val SKELETON_SHIMMER_START_X_TARGET = 2f

/**
 * 詳細画面のローディング用スケルトン。
 *
 * 実際の詳細画面と同じ骨格にしている。以前は TopAppBar + 16dp パディング + 角丸カードだったが、
 * 実画面はエッジ・ツー・エッジのヒーロー画像にステータスバー上へ浮く円形アイコンで、解決時に
 * パディングが消えて戻るボタンがアプリバーから円形ボタンへ飛ぶレイアウトジャンプがあった。
 * ヒーローの比率、アイコンの位置とサイズ、情報ブロックの余白を実画面に合わせる。
 */
@Composable
fun IllustDetailSkeletonScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    if (errorMessage != null) {
        UserProfileErrorScreen(message = errorMessage, onBack = onBack, onRetry = onRetry, modifier = modifier)
        return
    }

    PredictiveBackGestureHandler(onBack = onBack)

    val transition = rememberInfiniteTransition(label = "illustDetailSkeletonTransition")
    val shimmer =
        transition.animateFloat(
            initialValue = SKELETON_SHIMMER_START_X_INITIAL,
            targetValue = SKELETON_SHIMMER_START_X_TARGET,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = SHIMMER_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "illustDetailSkeletonShimmer",
        )
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerColors = remember(base, highlight) { listOf(base, highlight, base) }
    val shimmerModifier =
        Modifier.drawWithCache {
            onDrawBehind {
                val startX = shimmer.value * size.width
                drawRect(
                    brush =
                        Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(startX, 0f),
                            end = Offset(startX + size.width * SKELETON_SHIMMER_GRADIENT_WIDTH, size.height),
                        ),
                )
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ヒーロー画像の箱。実画面は画像の比率で伸びるが、スケルトンではカードの 0.75 比率を
            // 最低限の高さとして使う。全幅・角丸なしで、実画面と同じくエッジ・ツー・エッジ。
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .background(base)
                        .then(shimmerModifier),
            )

            // 情報ブロック。実画面と同じく上 16dp / 下 16dp、各要素は 16dp の横余白。
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surface)
                        .padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // タイトル
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(shimmerModifier),
                    )
                    // ID / 種別の行
                    Box(
                        modifier =
                            Modifier
                                .size(width = 96.dp, height = 13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(shimmerModifier),
                    )
                }
                // 作者行（アバター + 名前 + フォローピル）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .then(shimmerModifier),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 132.dp, height = 16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 72.dp, height = 12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(width = 72.dp, height = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(shimmerModifier),
                    )
                }
                // タグ行
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 70.dp, height = 26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(shimmerModifier),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // ステータスバー上に浮く戻るボタン。実画面と同じ 38dp の円形、同じ位置。
        // ロード中でも戻れるよう、ここは実際に押せるボタンにする。
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderOverlayIcon(
                icon = MiuixIcons.Back,
                onClick = onBack,
                contentDescription = null,
            )
            Box(
                modifier =
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
            )
        }
    }
}
