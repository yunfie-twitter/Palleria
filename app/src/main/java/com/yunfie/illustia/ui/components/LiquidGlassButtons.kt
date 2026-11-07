package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

private const val LIQUID_GLASS_EXPANSION_DP = 4f
private const val LIQUID_GLASS_DRAG_SCALE_DP = 4f
private const val LIQUID_GLASS_OFFSET_DERIVATIVE = 0.05f

private val liquidGlassPressSpring =
    spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
private val liquidGlassPointerSpring =
    spring<Offset>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = Offset.VisibilityThreshold)

private class LiquidGlassPressState(
    private val scope: CoroutineScope,
) {
    val pressProgress = Animatable(0f, 0.001f)
    val pointerPosition =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val offset: Offset
        get() = pointerPosition.value - startPosition

    fun press(start: Offset) {
        startPosition = start
        scope.launch {
            launch { pressProgress.animateTo(1f, liquidGlassPressSpring) }
            launch { pointerPosition.snapTo(start) }
        }
    }

    fun move(position: Offset) {
        scope.launch { pointerPosition.snapTo(position) }
    }

    fun release() {
        scope.launch {
            launch { pressProgress.animateTo(0f, liquidGlassPressSpring) }
            launch { pointerPosition.animateTo(startPosition, liquidGlassPointerSpring) }
        }
    }
}

@Composable
fun rememberLiquidGlassBackdrop(
    enabled: Boolean,
    backgroundColor: Color,
): LayerBackdrop? =
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val onDraw: ContentDrawScope.() -> Unit =
            remember(backgroundColor) {
                {
                    drawRect(backgroundColor)
                    drawContent()
                }
            }
        rememberLayerBackdrop(onDraw = onDraw)
    } else {
        null
    }

@Composable
fun LiquidGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    surfaceColor: Color = Color.Unspecified,
    performHaptic: Boolean = true,
) {
    val haptic = rememberHapticFeedbackAction()
    val scope = rememberCoroutineScope()
    val pressState = remember(scope) { LiquidGlassPressState(scope) }
    Box(
        modifier =
            modifier
                .size(38.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val width = size.width
                        val height = size.height
                        val progress = pressState.pressProgress.value
                        val scale = lerp(1f, 1f + LIQUID_GLASS_EXPANSION_DP.dp.toPx() / height, progress)

                        val maxOffset = size.minDimension
                        val offset = pressState.offset
                        translationX =
                            maxOffset * tanh(LIQUID_GLASS_OFFSET_DERIVATIVE * offset.x / maxOffset)
                        translationY =
                            maxOffset * tanh(LIQUID_GLASS_OFFSET_DERIVATIVE * offset.y / maxOffset)

                        val maxDragScale = LIQUID_GLASS_DRAG_SCALE_DP.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        val horizontalDrag =
                            abs(cos(offsetAngle) * offset.x / size.maxDimension)
                        val verticalDrag =
                            abs(sin(offsetAngle) * offset.y / size.maxDimension)
                        scaleX = scale + maxDragScale * horizontalDrag * (width / height).coerceAtMost(1f)
                        scaleY = scale + maxDragScale * verticalDrag * (height / width).coerceAtMost(1f)
                    },
                    onDrawSurface = {
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    },
                ).pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (performHaptic) haptic(AppHapticEffect.Click)
                    onClick()
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressState.press(down.position)
                        var pointerId = down.id
                        var tracking = true
                        while (tracking) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            tracking =
                                change != null &&
                                !change.isConsumed &&
                                !change.changedToUpIgnoreConsumed()
                            if (change != null && tracking) {
                                if (change.position != change.previousPosition) {
                                    pressState.move(change.position)
                                }
                                pointerId = change.id
                            }
                        }
                        pressState.release()
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        MiuixIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun LiquidGlassOverflowMenu(
    entries: List<DropdownEntry>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    iconTint: Color = Color.White,
    surfaceColor: Color = Color.Unspecified,
    performHaptic: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        LiquidGlassIconButton(
            icon = MiuixIcons.More,
            contentDescription = contentDescription,
            onClick = { expanded = true },
            backdrop = backdrop,
            iconTint = iconTint,
            surfaceColor = surfaceColor,
            performHaptic = performHaptic,
        )
        OverlayCascadingListPopup(
            show = expanded,
            entries = entries,
            onDismissRequest = { expanded = false },
        )
    }
}
