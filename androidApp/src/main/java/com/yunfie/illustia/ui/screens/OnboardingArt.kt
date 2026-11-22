package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DeviceFrame(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surfaceContainerHighest,
                    cornerRadius = 28.dp,
                ).padding(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .squircleSurface(
                        color = Color.White,
                        cornerRadius = 22.dp,
                    ).padding(14.dp),
        ) {
            content()
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .size(width = 54.dp, height = 8.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.85f),
                        cornerRadius = 99.dp,
                    ),
        )
    }
}

@Composable
internal fun FeedCard(
    widthFactor: Float,
    accent: Color,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFactor)
                .height(58.dp)
                .squircleSurface(
                    color = accent.copy(alpha = 0.08f),
                    cornerRadius = 18.dp,
                ).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .squircleSurface(
                            color = accent.copy(alpha = 0.25f),
                            cornerRadius = 10.dp,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Ok,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(10.dp)
                            .squircleSurface(
                                color = tint.copy(alpha = 0.82f),
                                cornerRadius = 99.dp,
                            ),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.46f)
                            .height(8.dp)
                            .squircleSurface(
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.24f),
                                cornerRadius = 99.dp,
                            ),
                )
            }
        }
    }
}

@Composable
internal fun CardHeader(accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.16f),
                        cornerRadius = 18.dp,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.FavoritesFill,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier =
                Modifier
                    .size(width = 52.dp, height = 12.dp)
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.18f),
                        cornerRadius = 99.dp,
                    ),
        )
    }
}

@Composable
internal fun BookStack(accent: Color) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 110.dp, height = 160.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.16f),
                        cornerRadius = 18.dp,
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(width = 126.dp, height = 178.dp)
                    .squircleSurface(
                        color = Color.White,
                        cornerRadius = 18.dp,
                    ).padding(8.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.08f),
                        cornerRadius = 14.dp,
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 92.dp, height = 140.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.24f),
                        cornerRadius = 18.dp,
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 84.dp, height = 18.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.55f),
                        cornerRadius = 99.dp,
                    ),
        )
    }
}

@Composable
internal fun SyncPanel(accent: Color) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .squircleSurface(
                    color = accent.copy(alpha = 0.09f),
                    cornerRadius = 18.dp,
                ).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .squircleSurface(
                        color = accent.copy(alpha = 0.22f),
                        cornerRadius = 8.dp,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.66f)
                        .height(10.dp)
                        .squircleSurface(
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                            cornerRadius = 99.dp,
                        ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(8.dp)
                        .squircleSurface(
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.22f),
                            cornerRadius = 99.dp,
                        ),
            )
        }
    }
}

@Composable
internal fun RepeatRow(
    count: Int,
    accent: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .squircleSurface(
                            color = accent.copy(alpha = 0.3f),
                            cornerRadius = 99.dp,
                        ),
            )
        }
    }
}

@Composable
internal fun FloatingBadge(
    icon: ImageVector,
    accent: Color,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(56.dp)
                .squircleSurface(
                    color = accent,
                    cornerRadius = 18.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
internal fun BookmarkRibbon(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(width = 72.dp, height = 28.dp)
                .squircleSurface(
                    color = accent.copy(alpha = 0.92f),
                    cornerRadius = 14.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MiuixIcons.Contacts,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun CircleGlow(
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(color, CircleShape),
    )
}

@Composable
internal fun PagerDots(
    pageCount: Int,
    currentPage: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier =
                    Modifier
                        .height(8.dp)
                        .width(if (index == currentPage) 22.dp else 8.dp)
                        .squircleSurface(
                            color =
                                if (index == currentPage) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHighest
                                },
                            cornerRadius = 99.dp,
                        ),
            )
        }
    }
}
