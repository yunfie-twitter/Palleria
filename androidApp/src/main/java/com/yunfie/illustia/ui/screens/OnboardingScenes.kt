package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class OnboardingSlide(
    val title: Int,
    val body: Int,
    val accent: Color,
    val icon: ImageVector,
    val scene: OnboardingScene,
)

internal enum class OnboardingScene {
    Discovery,
    Bookmarks,
    Sync,
}

@Composable
internal fun OnboardingPage(slide: OnboardingSlide) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surface,
                    cornerRadius = 28.dp,
                ).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(slide.title),
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
            style = MiuixTheme.textStyles.title1,
            lineHeight = 30.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        top.yukonga.miuix.kmp.basic.Text(
            text = stringResource(slide.body),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (slide.scene) {
                OnboardingScene.Discovery -> DiscoveryScene(slide)
                OnboardingScene.Bookmarks -> BookmarkScene(slide)
                OnboardingScene.Sync -> SyncScene(slide)
            }
        }
    }
}

@Composable
private fun DiscoveryScene(slide: OnboardingSlide) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircleGlow(
            color = slide.accent.copy(alpha = 0.14f),
            size = 250.dp,
            modifier = Modifier.align(Alignment.Center),
        )
        DeviceFrame(
            accent = slide.accent,
            modifier =
                Modifier
                    .size(width = 260.dp, height = 300.dp)
                    .align(Alignment.Center),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RepeatRow(3, slide.accent)
                Spacer(Modifier.height(6.dp))
                FeedCard(widthFactor = 0.78f, accent = slide.accent, tint = MiuixTheme.colorScheme.primary)
                FeedCard(widthFactor = 0.94f, accent = slide.accent.copy(alpha = 0.85f), tint = MiuixTheme.colorScheme.onSurface)
                FeedCard(
                    widthFactor = 0.72f,
                    accent = slide.accent.copy(alpha = 0.75f),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        FloatingBadge(
            icon = slide.icon,
            accent = slide.accent,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 12.dp),
        )
        FloatingBadge(
            icon = MiuixIcons.Ok,
            accent = MiuixTheme.colorScheme.surfaceContainerHighest,
            tint = MiuixTheme.colorScheme.primary,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 18.dp, bottom = 26.dp),
        )
    }
}

@Composable
private fun BookmarkScene(slide: OnboardingSlide) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircleGlow(
            color = slide.accent.copy(alpha = 0.12f),
            size = 230.dp,
        )
        DeviceFrame(
            accent = slide.accent,
            modifier = Modifier.size(width = 260.dp, height = 320.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CardHeader(slide.accent)
                BookStack(slide.accent)
            }
        }
        BookmarkRibbon(
            accent = slide.accent,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 26.dp, end = 16.dp),
        )
        FloatingBadge(
            icon = MiuixIcons.FavoritesFill,
            accent = MiuixTheme.colorScheme.surfaceContainerHighest,
            tint = slide.accent,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 30.dp, end = 18.dp),
        )
    }
}

@Composable
private fun SyncScene(slide: OnboardingSlide) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircleGlow(
            color = slide.accent.copy(alpha = 0.12f),
            size = 250.dp,
        )
        DeviceFrame(
            accent = slide.accent,
            modifier = Modifier.size(width = 250.dp, height = 320.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RepeatRow(2, slide.accent)
                SyncPanel(slide.accent)
                SyncPanel(slide.accent.copy(alpha = 0.82f))
                SyncPanel(slide.accent.copy(alpha = 0.68f))
            }
        }
        FloatingBadge(
            icon = slide.icon,
            accent = slide.accent,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 34.dp, start = 16.dp),
        )
        FloatingBadge(
            icon = MiuixIcons.Ok,
            accent = MiuixTheme.colorScheme.surfaceContainerHighest,
            tint = slide.accent,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 22.dp, end = 18.dp),
        )
    }
}
