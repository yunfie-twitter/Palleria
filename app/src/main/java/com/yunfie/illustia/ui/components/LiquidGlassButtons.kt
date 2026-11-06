package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        label = "liquid-glass-button-scale",
    )
    Box(
        modifier =
            modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(3.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx())
                    },
                    onDrawSurface = {
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    },
                ).pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                ) {
                    if (performHaptic) haptic(AppHapticEffect.Click)
                    onClick()
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
