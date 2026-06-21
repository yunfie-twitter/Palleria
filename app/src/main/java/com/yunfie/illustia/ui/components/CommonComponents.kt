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

@Composable
fun Modifier.miuixClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
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
        onClick = onClick,
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

    // チェックマーク → フォロー中 の自動遷移
    LaunchedEffect(stage) {
        if (stage == FollowPillStage.CHECK) {
            delay(600)
            stage = FollowPillStage.FOLLOWED
        } else if (stage == FollowPillStage.UNFOLLOWING) {
            delay(300)
            stage = FollowPillStage.UNFOLLOWED
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
        Icon(imageVector = icon, contentDescription = null, tint = MiuixTheme.colorScheme.onBackground)
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

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallTitle(text = title)
        content()
    }
}

@Composable
fun SettingRow(title: String, summary: String? = null, action: @Composable () -> Unit) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        endActions = {
            action()
        },
    )
}

@Composable
fun SettingLinkRow(title: String, onClick: () -> Unit) {
    ArrowPreference(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    )
}

@Composable
fun ElevatedPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HeroPanel(title: String, body: String) {
    Card(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(text = title, fontWeight = FontWeight.Black, color = MiuixTheme.colorScheme.onPrimaryContainer)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = body, color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f))
    }
}

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(confirmText, color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun <T> SettingDropdownRow(
    title: String,
    selected: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    summary: String? = null,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    OverlayDropdownPreference(
        title = title,
        summary = summary,
        items = values.map { label(it) },
        selectedIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        onSelectedIndexChange = { index ->
            values.getOrNull(index)?.let(onSelect)
        },
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { value ->
                    val isSelected = value == selected
                    Button(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        colors = if (isSelected) {
                            ButtonDefaults.buttonColorsPrimary()
                        } else {
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.surfaceContainer,
                            )
                        },
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(
                            label(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun <T> FlowButtons(values: List<T>, label: (T) -> String, onClick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { value ->
                    Button(
                        onClick = { onClick(value) },
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = title,
            modifier = Modifier.weight(1f),
            insideMargin = PaddingValues(0.dp),
        )
        if (action != null && onAction != null) {
            TextButton(
                text = action,
                onClick = onAction,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────
//  CollapsingHeader
//  スクロール量に応じて「大ヘッダー → ミニヘッダー」を
//  スムーズにアニメーション切り替えするコンポーネント。
// ─────────────────────────────────────────────

/**
 * スクロールに連動してヘッダーを折り畳む / 展開するコンポーネント。
 *
 * @param scrollOffset  LazyGridState / LazyListState の firstVisibleItemScrollOffset (px)
 * @param firstIndex    LazyGridState / LazyListState の firstVisibleItemIndex
 * @param collapseThresholdPx  このピクセルを超えると折り畳みが始まる（デフォルト 90px）
 * @param title         大ヘッダー時のタイトル文字列
 * @param subtitle      大ヘッダー時のサブタイトル（省略可）
 * @param actions       右端に並ぶアクションボタン群
 * @param onTitleClick  折り畳みヘッダーのタイトルをタップしたときのコールバック（主にトップへスクロール）
 * @param miniContent   折り畳みヘッダー内中央に追加で表示するコンテンツ（省略可）
 */
@Composable
fun CollapsingHeader(
    scrollOffset: Int,
    firstIndex: Int,
    title: String,
    subtitle: String? = null,
    collapseThresholdPx: Int = 90,
    actions: @Composable RowScope.() -> Unit = {},
    onTitleClick: (() -> Unit)? = null,
    miniContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 折り畳み判定: index > 0 か、index == 0 でも閾値超えたら折り畳み
    val isCollapsed = remember(firstIndex, scrollOffset) {
        firstIndex > 0 || scrollOffset > collapseThresholdPx
    }

    // 大ヘッダーの透明度 (スクロールに応じて 1f → 0f)
    val expandAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 0f
        else (1f - scrollOffset.toFloat() / collapseThresholdPx.toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 160),
        label = "collapsingHeader-expandAlpha",
    )

    // ミニヘッダーの透明度 (逆方向)
    val miniAlpha by animateFloatAsState(
        targetValue = if (isCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "collapsingHeader-miniAlpha",
    )

    val scheme = MiuixTheme.colorScheme
    Box(modifier = modifier.fillMaxWidth()) {
        // ── 大ヘッダー ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = expandAlpha }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    color = scheme.onBackground,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = scheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(content = actions)
        }

        // ── ミニヘッダー (sticky) ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = miniAlpha }
                .background(scheme.background.copy(alpha = 0.9f))
                .then(
                    if (onTitleClick != null) Modifier.miuixClickable(onClick = onTitleClick) else Modifier,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = scheme.onBackground,
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Bold,
            )
            if (miniContent != null) {
                Box(modifier = Modifier.align(Alignment.Center)) {
                    miniContent()
                }
            }
            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                Row(content = actions)
            }
        }
    }
}

// RowScope の型エイリアスで @Composable RowScope.() -> Unit をシンプルに使えるようにする
private typealias RowContent = @Composable RowScope.() -> Unit
