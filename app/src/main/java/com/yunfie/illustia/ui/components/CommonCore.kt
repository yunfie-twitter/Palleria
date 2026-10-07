@file:Suppress("TooManyFunctions")

package com.yunfie.illustia.ui.components

import android.content.Context
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.AppSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

const val MotionFast = 180
const val MotionMedium = 260
const val MotionSlow = 340

val MainNavigationContentPadding = 152.dp
val BottomSheetInsideMargin = DpSize(width = 24.dp, height = 12.dp)

@Composable
fun adaptiveMainNavigationContentPadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else MainNavigationContentPadding

val LocalPixivImageProxyBaseUrl = compositionLocalOf { "" }
val LocalPreferLowDataImages = compositionLocalOf { false }
val LocalBottomSheetBackgroundColor = compositionLocalOf { Color.Unspecified }

@Composable
fun overlayActionButtonColors() =
    ButtonDefaults.buttonColors(
        color = MiuixTheme.colorScheme.surfaceContainer,
        contentColor = MiuixTheme.colorScheme.onSurface,
    )

fun Context.isActiveNetworkMetered(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    return runCatching {
        connectivityManager?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)
}

@Composable
fun NonAmoledDarkTheme(content: @Composable () -> Unit) {
    val controller =
        remember {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.Dark,
                darkColors = darkColorScheme(),
            )
        }
    MiuixTheme(controller = controller) {
        content()
    }
}

@Composable
fun PredictiveBackGestureHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    if (PlatformCapabilities.supportsPredictiveBack()) {
        return
    }

    BackHandler(enabled = enabled) {
        onBack()
    }
}

@Composable
fun adaptiveIllustColumns(settings: AppSettings): Int {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        settings.horizontalColumnCount,
        settings.verticalColumnCount,
    ) {
        val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
        if (isLandscape) {
            settings.horizontalColumnCount.coerceIn(3, 6)
        } else {
            settings.verticalColumnCount.coerceIn(1, 4)
        }
    }
}

@Composable
fun Modifier.pinchToChangeColumns(
    enabled: Boolean,
    currentColumns: Int,
    onColumnsChange: (Int) -> Unit,
): Modifier =
    pinchToChangeColumns(
        enabled = enabled,
        currentColumns = currentColumns,
        onPinchGestureSuccess = null,
        onColumnsChange = onColumnsChange,
    )

@Composable
fun Modifier.pinchToChangeColumns(
    enabled: Boolean,
    currentColumns: Int,
    onPinchGestureSuccess: (() -> Unit)? = null,
    onColumnsChange: (Int) -> Unit,
): Modifier {
    if (!enabled) return this
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current

    val currentColumnsState = rememberUpdatedState(currentColumns)
    val onColumnsChangeState = rememberUpdatedState(onColumnsChange)
    val onPinchGestureSuccessState = rememberUpdatedState(onPinchGestureSuccess)

    return this
        .pointerInput(enabled) {
            awaitPointerEventScope {
                var scrollAccumulator = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll && event.keyboardModifiers.isCtrlPressed) {
                        scrollAccumulator += event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
                        if (kotlin.math.abs(scrollAccumulator) >= 1f) {
                            val direction = if (scrollAccumulator > 0) 1 else -1
                            val next = (currentColumnsState.value + direction).coerceIn(1, 4)
                            if (next != currentColumnsState.value) {
                                performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Click)
                                onColumnsChangeState.value(next)
                                onPinchGestureSuccessState.value?.invoke()
                            } else {
                                performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.BoundaryLimit)
                            }
                            scrollAccumulator = 0f
                        }
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }.pointerInput(enabled) {
            awaitEachGesture {
                val gesture = GridPinchGesture()
                do {
                    // Claim the second finger before card long-press detectors see it.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val active = event.changes.filter { it.pressed }
                    val zoom =
                        if (active.size >= 2 && active[0].previousPressed && active[1].previousPressed) {
                            val previous = (active[0].previousPosition - active[1].previousPosition).getDistance()
                            if (previous > 0f) (active[0].position - active[1].position).getDistance() / previous else 1f
                        } else {
                            1f
                        }
                    val update = gesture.update(active.size, zoom)
                    if (update.consume) event.changes.forEach { it.consume() }
                    if (update.columnDelta != 0) {
                        val next = (currentColumnsState.value + update.columnDelta).coerceIn(1, 4)
                        if (next != currentColumnsState.value) {
                            performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Click)
                            onColumnsChangeState.value(next)
                            onPinchGestureSuccessState.value?.invoke()
                        } else {
                            performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.BoundaryLimit)
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
}

fun Modifier.horizontalPadding(padding: Dp): Modifier =
    layout { measurable, constraints ->
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
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    return pointerHoverIcon(PointerIcon.Hand)
        .pressable(
            interactionSource = null,
            indication = SinkFeedback(sinkAmount = pressedScale),
        ).clickable(
            interactionSource = null,
            indication = null,
            onClick = {
                if (haptic) performAppHapticFeedback(context, hapticFeedback, hapticMode)
                onClick()
            },
        )
}

@Composable
fun AvatarImage(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    maxDecodeDimensionPx: Int = 192,
) {
    val commonModifier =
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)

    if (url != null) {
        PixivImage(
            url = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = commonModifier,
            thumbnail = true,
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

const val FAST_SCROLL_THRESHOLD = 20

suspend fun LazyGridState.smoothScrollToTop(scrollBehavior: ScrollBehavior? = null) {
    if (firstVisibleItemIndex > FAST_SCROLL_THRESHOLD) {
        scrollToItem(FAST_SCROLL_THRESHOLD)
    }
    animateScrollToItem(0, 0)
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        scrollToItem(0, 0)
    }
    scrollBehavior?.state?.heightOffset = 0f
    scrollBehavior?.state?.contentOffset = 0f
}

suspend fun LazyListState.smoothScrollToTop(scrollBehavior: ScrollBehavior? = null) {
    if (firstVisibleItemIndex > FAST_SCROLL_THRESHOLD) {
        scrollToItem(FAST_SCROLL_THRESHOLD)
    }
    animateScrollToItem(0, 0)
    if (firstVisibleItemIndex != 0 || firstVisibleItemScrollOffset != 0) {
        scrollToItem(0, 0)
    }
    scrollBehavior?.state?.heightOffset = 0f
    scrollBehavior?.state?.contentOffset = 0f
}

private const val TOP_BAR_TAP_MAX_DURATION_MS = 500L

/**
 * Intercepts tap gestures on the app bar header during [PointerEventPass.Initial]
 * before Miuix TopAppBar's internal detectTapGestures consumes them.
 */
@Composable
fun Modifier.onTopBarTap(
    navIconWidth: Dp = 64.dp,
    actionsWidth: Dp = 120.dp,
    hasBottomContent: Boolean = false,
    bottomContentHeight: Dp = 56.dp,
    onTap: () -> Unit,
): Modifier {
    val currentOnTap = rememberUpdatedState(onTap)
    return pointerInput(navIconWidth, actionsWidth, hasBottomContent, bottomContentHeight) {
        awaitEachGesture {
            val down =
                awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed }
                    ?: return@awaitEachGesture
            val downPos = down.position
            val width = size.width
            val height = size.height

            val navIconPx = navIconWidth.toPx()
            val actionsPx = actionsWidth.toPx()
            val bottomContentPx = if (hasBottomContent) bottomContentHeight.toPx() else 0f

            val isInActionSlot =
                downPos.x < navIconPx ||
                    downPos.x > (width - actionsPx) ||
                    (hasBottomContent && downPos.y > (height - bottomContentPx))

            if (isInActionSlot) {
                return@awaitEachGesture
            }

            var isTap = true
            val downTime = System.currentTimeMillis()

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    val upTime = System.currentTimeMillis()
                    if (isTap && (upTime - downTime) < TOP_BAR_TAP_MAX_DURATION_MS) {
                        currentOnTap.value()
                    }
                    break
                } else {
                    val distance = (change.position - downPos).getDistance()
                    if (distance > viewConfiguration.touchSlop) {
                        isTap = false
                    }
                }
            }
        }
    }
}
