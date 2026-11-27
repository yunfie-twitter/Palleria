package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.ui.components.SystemBarAppearanceEffect
import com.yunfie.illustia.ui.components.rememberLiquidGlassBackdrop
import com.yunfie.illustia.ui.screens.profile.UserProfilePlatform
import top.yukonga.miuix.kmp.blur.LayerBackdrop

@Composable
fun androidUserProfilePlatform(): UserProfilePlatform {
    val context = LocalContext.current
    return remember(context) {
        object : UserProfilePlatform {
            @Composable
            override fun SystemBarAppearance(
                lightStatusBars: Boolean,
                lightNavigationBars: Boolean,
                restoreLightStatusBars: Boolean,
                restoreLightNavigationBars: Boolean,
            ) {
                SystemBarAppearanceEffect(
                    lightStatusBars = lightStatusBars,
                    lightNavigationBars = lightNavigationBars,
                    restoreLightStatusBars = restoreLightStatusBars,
                    restoreLightNavigationBars = restoreLightNavigationBars,
                )
            }

            @Composable
            override fun rememberBackdrop(
                enabled: Boolean,
                backgroundColor: Color,
            ): LayerBackdrop? = rememberLiquidGlassBackdrop(enabled = enabled, backgroundColor = backgroundColor)
        }
    }
}
