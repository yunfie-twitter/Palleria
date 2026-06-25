package com.yunfie.illustia.ui.components

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.SingletonImageLoader
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface


const val MotionFast = 180
const val MotionMedium = 260
const val MotionSlow = 340

val MainNavigationContentPadding = 152.dp

val PixivImageHeaders = NetworkHeaders.Builder()
    .set("Referer", "https://www.pixiv.net/")
    .set("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
    .build()

val LocalPixivImageProxyBaseUrl = compositionLocalOf { "" }
val LocalPreferLowDataImages = compositionLocalOf { false }

// BottomSheet 用の背景色。AMOLED モードでも BottomSheet は純黒にしない
val LocalBottomSheetBackgroundColor = compositionLocalOf { Color.Unspecified }

private const val ThumbnailDecodeSizePx = 512
private const val PrefetchDecodeSizePx = 512

fun Context.isActiveNetworkMetered(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    return runCatching {
        connectivityManager?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)
}

@Composable
fun NonAmoledDarkTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.Dark,
            darkColors = darkColorScheme(),
        )
    }
    MiuixTheme(controller = controller) {
        content()
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
fun PredictiveBackGestureHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        try {
            progress.collect()
            onBack()
        } catch (e: CancellationException) {
            throw e
        }
    }
}

@Composable
fun PixivImage(
    url: String,
    contentDescription: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false,
    thumbnail: Boolean = false,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val effectiveUrl = remember(url, proxyBaseUrl) {
        proxyPixivImageUrl(url, proxyBaseUrl)
    }
    val imageRequest = remember(effectiveUrl, thumbnail) {
        ImageRequest.Builder(context)
            .data(effectiveUrl)
            .httpHeaders(PixivImageHeaders)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(!thumbnail && crossfade)
            .apply {
                if (thumbnail) {
                    size(ThumbnailDecodeSizePx)
                    scale(Scale.FILL)
                    precision(Precision.INEXACT)
                    allowRgb565(true)
                }
            }
            .build()
    }
    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun PrefetchPixivImages(
    urls: List<String>,
    enabled: Boolean,
    limit: Int = 16,
) {
    val context = LocalPlatformContext.current
    val proxyBaseUrl = LocalPixivImageProxyBaseUrl.current
    val prefetchUrls = remember(urls, proxyBaseUrl, limit) {
        urls.asSequence()
            .filter { it.isNotBlank() }
            .map { proxyPixivImageUrl(it, proxyBaseUrl) }
            .distinct()
            .take(limit)
            .toList()
    }

    var previousUrls by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(enabled, prefetchUrls) {
        if (!enabled || prefetchUrls.isEmpty()) {
            previousUrls = emptySet()
            return@LaunchedEffect
        }

        val newUrls = prefetchUrls.toSet()
        val urlsToPrefetch = newUrls - previousUrls

        if (urlsToPrefetch.isNotEmpty()) {
            val imageLoader = SingletonImageLoader.get(context)
            urlsToPrefetch.forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(PixivImageHeaders)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .size(PrefetchDecodeSizePx)
                    .scale(Scale.FILL)
                    .precision(Precision.INEXACT)
                    .allowRgb565(true)
                    .build()
                imageLoader.enqueue(request)
            }
            previousUrls = newUrls
        }
    }
}

@Composable
fun adaptiveIllustColumns(settings: AppSettings): Int {
    val configuration = LocalConfiguration.current
    val columns by remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        settings.horizontalColumnCount,
        settings.verticalColumnCount,
    ) {
        derivedStateOf {
            val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
            if (isLandscape) {
                settings.horizontalColumnCount.coerceIn(3, 6)
            } else {
                settings.verticalColumnCount.coerceIn(2, 4)
            }
        }
    }
    return columns
}

fun Modifier.horizontalPadding(padding: Dp): Modifier = layout { measurable, constraints ->
    val paddingPx = padding.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth - paddingPx * 2))
    layout(placeable.width, placeable.height) {
        placeable.place(paddingPx, 0)
    }
}

@Composable
fun Modifier.miuixClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 520f),
        label = "miuix-click-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {
            if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
    )
}

@Composable
fun AvatarImage(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val commonModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(MiuixTheme.colorScheme.surfaceContainerHigh)

    if (url != null) {
        PixivImage(
            url = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = commonModifier,
        )
    } else {
        Box(
            modifier = commonModifier,
            contentAlignment = Alignment.Center,
        ) {
            Text("no image", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2, fontWeight = FontWeight.Black)
        }
    }
}

// フォローボタンの内部状態
private enum class FollowPillStage { UNFOLLOWED, CHECK, FOLLOWED, UNFOLLOWING }
private enum class BookmarkButtonStage { UNBOOKMARKED, CHECK, BOOKMARKED, REMOVING }

/**
 * アニメーション付きフォローボタン。
 * フォロー時: 「フォロー」→ チェックマーク → 「フォロー中」
 * フォロー解除時: 「フォロー中」→ フェードアウト → 「フォロー」
 */
@Composable
fun FollowPill(isFollowed: Boolean, modifier: Modifier = Modifier) {
    // isFollowed の前の値を追跡してアニメーション方向を決定する
    var prevFollowed by remember { mutableStateOf(isFollowed) }
    var stage by remember(isFollowed) {
        val initial = when {
            isFollowed && !prevFollowed -> FollowPillStage.CHECK
            !isFollowed && prevFollowed -> FollowPillStage.UNFOLLOWING
            isFollowed -> FollowPillStage.FOLLOWED
            else -> FollowPillStage.UNFOLLOWED
        }
        prevFollowed = isFollowed
        mutableStateOf(initial)
    }

    val haptic = LocalHapticFeedback.current

    // チェックマーク → フォロー中 の自動遷移
    LaunchedEffect(stage) {
        when (stage) {
            FollowPillStage.CHECK -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(600)
                stage = FollowPillStage.FOLLOWED
            }
            FollowPillStage.UNFOLLOWING -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(300)
                stage = FollowPillStage.UNFOLLOWED
            }
            else -> Unit
        }
    }

    val isActiveFollow = remember(stage) { stage == FollowPillStage.FOLLOWED || stage == FollowPillStage.CHECK }
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .squircleSurface(
                color = if (isActiveFollow) scheme.primary else scheme.surfaceContainerHigh,
                cornerRadius = 24.dp,
            )
            .then(
                if (!isActiveFollow) {
                    Modifier.squircleBorder(
                        width = 1.dp,
                        color = scheme.onSurface.copy(alpha = 0.15f),
                        cornerRadius = 24.dp,
                    )
                } else Modifier
            )
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                when (targetState) {
                    FollowPillStage.CHECK ->
                        (scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.3f) + fadeIn(tween(160))) togetherWith
                                (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
                    FollowPillStage.FOLLOWED ->
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)) togetherWith
                                (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.1f))
                    FollowPillStage.UNFOLLOWING, FollowPillStage.UNFOLLOWED ->
                        (fadeIn(tween(200))) togetherWith (fadeOut(tween(200)))
                }
            },
            label = "follow-pill-stage",
        ) { s ->
            when (s) {
                FollowPillStage.CHECK -> Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
                FollowPillStage.FOLLOWED -> Text(
                    text = stringResource(R.string.action_following),
                    color = scheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.subtitle,
                )
                FollowPillStage.UNFOLLOWING -> Text(
                    text = stringResource(R.string.action_following),
                    color = scheme.onPrimary.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.subtitle,
                )
                FollowPillStage.UNFOLLOWED -> Text(
                    text = stringResource(R.string.action_follow),
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
        }
    }
}

@Composable
fun BookmarkHeartButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 26.dp,
    cornerRadius: Dp = size / 2,
    activeBackground: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    inactiveBackground: Color = Color.Transparent,
) {
    val haptic = LocalHapticFeedback.current
    var previousBookmarked by remember { mutableStateOf(isBookmarked) }
    var stage by remember(isBookmarked) {
        val initial = when {
            isBookmarked && !previousBookmarked -> BookmarkButtonStage.CHECK
            !isBookmarked && previousBookmarked -> BookmarkButtonStage.REMOVING
            isBookmarked -> BookmarkButtonStage.BOOKMARKED
            else -> BookmarkButtonStage.UNBOOKMARKED
        }
        previousBookmarked = isBookmarked
        mutableStateOf(initial)
    }

    LaunchedEffect(stage) {
        when (stage) {
            BookmarkButtonStage.CHECK -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(420)
                stage = BookmarkButtonStage.BOOKMARKED
            }
            BookmarkButtonStage.REMOVING -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(220)
                stage = BookmarkButtonStage.UNBOOKMARKED
            }
            else -> Unit
        }
    }

    val active = remember(stage) { stage == BookmarkButtonStage.BOOKMARKED || stage == BookmarkButtonStage.CHECK }
    val scheme = MiuixTheme.colorScheme
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size),
        minWidth = size,
        minHeight = size,
        cornerRadius = cornerRadius,
        backgroundColor = if (active) scheme.surfaceContainerHigh else inactiveBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            HeartBurst(
                visible = stage == BookmarkButtonStage.CHECK,
                modifier = Modifier.size(size * 1.5f),
                color = scheme.error
            )

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    when (targetState) {
                        BookmarkButtonStage.CHECK ->
                            (scaleIn(spring(dampingRatio = 0.35f, stiffness = 300f), initialScale = 0.1f) + fadeIn(tween(100))) togetherWith
                                    (scaleOut(tween(100), targetScale = 0.5f) + fadeOut(tween(100)))
                        BookmarkButtonStage.BOOKMARKED ->
                            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f)) togetherWith
                                    (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 1.1f))
                        BookmarkButtonStage.REMOVING, BookmarkButtonStage.UNBOOKMARKED ->
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    }
                },
                label = "bookmark-heart-stage",
            ) { s ->
                Icon(
                    imageVector = when (s) {
                        BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> MiuixIcons.FavoritesFill
                        BookmarkButtonStage.REMOVING, BookmarkButtonStage.UNBOOKMARKED -> MiuixIcons.Favorites
                    },
                    contentDescription = null,
                    tint = when (s) {
                        BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> scheme.error
                        BookmarkButtonStage.REMOVING -> scheme.onSurface.copy(alpha = 0.42f)
                        BookmarkButtonStage.UNBOOKMARKED -> scheme.onSurface
                    },
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun HeartBurst(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.error
) {
    val burstProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) tween(450) else snap(),
        label = "burstProgress"
    )

    if (burstProgress > 0f && burstProgress < 1f) {
        Box(modifier = modifier) {
            repeat(8) { i ->
                val angle = i * 45f
                val distance = 16.dp + (14.dp * burstProgress)
                val size = 4.dp * (1f - burstProgress)

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            val rad = Math.toRadians(angle.toDouble())
                            translationX = (distance.toPx() * kotlin.math.cos(rad)).toFloat()
                            translationY = (distance.toPx() * kotlin.math.sin(rad)).toFloat()
                            alpha = (1f - burstProgress) * 1.5f
                            scaleX = 1f - burstProgress
                            scaleY = 1f - burstProgress
                        }
                        .size(size)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
fun StateBanner(loadState: LoadState) {
    when (loadState) {
        LoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }
        is LoadState.Error -> {
            Text(
                text = loadState.message,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        LoadState.Idle,
        LoadState.Loaded -> Unit
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun HeaderIcon(icon: ImageVector, onClick: (() -> Unit)? = null) {
    IconButton(
        onClick = onClick ?: {},
        enabled = onClick != null,
        minWidth = 44.dp,
        minHeight = 44.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun HeaderOverlayIcon(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp),
        backgroundColor = Color.Black.copy(alpha = 0.35f),
        cornerRadius = 19.dp,
        minWidth = 38.dp,
        minHeight = 38.dp,
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
    }
}

@Composable
fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    InfiniteProgressIndicator(
        modifier = modifier,
        color = MiuixTheme.colorScheme.onBackground,
        size = 36.dp,
        strokeWidth = 3.dp,
        orbitingDotSize = 4.dp,
    )
}

@Composable
fun CenteredLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
