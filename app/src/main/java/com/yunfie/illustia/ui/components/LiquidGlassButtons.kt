package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon

private val LiquidGlassButtonSize = 38.dp

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
    GlassIconButton(
        onClick = {
            if (performHaptic) haptic(AppHapticEffect.Click)
            onClick()
        },
        modifier = modifier,
        backdrop = backdrop,
        size = LiquidGlassButtonSize,
        fill = if (surfaceColor.isSpecified) surfaceColor else GlassTopAppBarDefaults.buttonFill(),
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
