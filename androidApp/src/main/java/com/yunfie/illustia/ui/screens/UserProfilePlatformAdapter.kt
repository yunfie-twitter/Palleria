package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.components.SystemBarAppearanceEffect
import com.yunfie.illustia.ui.components.TransparentNavigationBarColor
import com.yunfie.illustia.ui.components.TransparentStatusBarColor
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
                transparentBarColors: Boolean,
            ) {
                if (transparentBarColors) {
                    SystemBarAppearanceEffect(
                        lightStatusBars = lightStatusBars,
                        lightNavigationBars = lightNavigationBars,
                        restoreLightStatusBars = restoreLightStatusBars,
                        restoreLightNavigationBars = restoreLightNavigationBars,
                        statusBarColor = TransparentStatusBarColor,
                        navigationBarColor = TransparentNavigationBarColor,
                        restoreStatusBarColor = TransparentStatusBarColor,
                        restoreNavigationBarColor = TransparentNavigationBarColor,
                    )
                } else {
                    SystemBarAppearanceEffect(
                        lightStatusBars = lightStatusBars,
                        lightNavigationBars = lightNavigationBars,
                        restoreLightStatusBars = restoreLightStatusBars,
                        restoreLightNavigationBars = restoreLightNavigationBars,
                    )
                }
            }

            @Composable
            override fun rememberBackdrop(
                enabled: Boolean,
                backgroundColor: Color,
            ): LayerBackdrop? = rememberLiquidGlassBackdrop(enabled = enabled, backgroundColor = backgroundColor)

            override suspend fun resolveDarkHeaderIcons(
                thumbnailUrl: String,
                proxyBaseUrl: String,
            ): Boolean =
                com.yunfie.illustia.ui.components.resolveDarkHeaderIcons(
                    context = context,
                    thumbnailUrl = thumbnailUrl,
                    proxyBaseUrl = proxyBaseUrl,
                )

            @Composable
            override fun UgoiraPlayer(
                previewUrl: String,
                contentDescription: String?,
                loadPlayback: suspend () -> UgoiraPlayback,
                powerSaveEnabled: Boolean,
                isActive: Boolean,
                onTap: () -> Unit,
                modifier: Modifier,
            ) {
                UgoiraArtwork(
                    previewUrl = previewUrl,
                    contentDescription = contentDescription.orEmpty(),
                    loadPlayback = loadPlayback,
                    powerSaveEnabled = powerSaveEnabled,
                    isActive = isActive,
                    onTap = onTap,
                    modifier = modifier,
                )
            }
        }
    }
}
