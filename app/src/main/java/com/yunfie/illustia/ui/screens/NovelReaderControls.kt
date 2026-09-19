package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val MIN_FONT_SIZE = 13f
private const val MAX_FONT_SIZE = 26f
private const val FONT_SIZE_STEPS = 12
private const val SEPIA_PANEL_COLOR = 0xFFF4ECD8
private const val DARK_PANEL_COLOR = 0xFF2A2A2A
private const val BLACK_PANEL_COLOR = 0xFF121212
private const val SEPIA_TEXT_COLOR = 0xFF5F4B32

@Composable
internal fun NovelBottomControlBar(
    currentPage: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val performHaptic = rememberHapticFeedbackAction()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${currentPage + 1}",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (pageCount > 1) {
                        Slider(
                            value = (currentPage + 1).toFloat().coerceIn(1f, pageCount.toFloat()),
                            onValueChange = {
                                val target = (it.toInt() - 1).coerceIn(0, pageCount - 1)
                                if (target != currentPage) {
                                    performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
                                    onPageChange(target)
                                }
                            },
                            valueRange = 1f..pageCount.toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        text = "$pageCount",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
                            onPageChange((currentPage - 1).coerceAtLeast(0))
                        },
                        enabled = currentPage > 0,
                    ) {
                        Icon(MiuixIcons.Back, contentDescription = null)
                    }

                    Button(
                        onClick = {
                            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Click)
                            onOpenToc()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MiuixTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MiuixIcons.Filter, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.novel_toc))
                        }
                    }

                    Button(
                        onClick = {
                            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Click)
                            onOpenSettings()
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MiuixTheme.colorScheme.onSurface,
                            ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(MiuixIcons.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.novel_display_settings))
                        }
                    }

                    IconButton(
                        onClick = {
                            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
                            onPageChange((currentPage + 1).coerceAtMost(pageCount - 1))
                        },
                        enabled = currentPage < pageCount - 1,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelTocBottomSheet(
    show: Boolean,
    currentPage: Int,
    pageCount: Int,
    chapters: List<NovelChapterInfo>,
    seriesPrevId: Long? = null,
    seriesPrevTitle: String? = null,
    seriesNextId: Long? = null,
    seriesNextTitle: String? = null,
    onJumpPage: (Int) -> Unit,
    onOpenSeriesEpisode: ((Long, String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    if (!show) return
    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.novel_toc),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (seriesPrevId != null || seriesNextId != null) {
                item {
                    Text(
                        text = stringResource(R.string.novel_series_section),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                item {
                    ElevatedPanel(contentPadding = PaddingValues(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (seriesPrevId != null) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .miuixClickable(
                                                pressedScale = 0.98f,
                                                haptic = true,
                                                onClick = {
                                                    onOpenSeriesEpisode?.invoke(seriesPrevId, seriesPrevTitle.orEmpty())
                                                    onDismiss()
                                                },
                                            ).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(MiuixIcons.Back, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = stringResource(R.string.novel_series_prev, seriesPrevTitle.orEmpty()),
                                        style = MiuixTheme.textStyles.body1,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            if (seriesPrevId != null && seriesNextId != null) {
                                DividerLine()
                            }
                            if (seriesNextId != null) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .miuixClickable(
                                                pressedScale = 0.98f,
                                                haptic = true,
                                                onClick = {
                                                    onOpenSeriesEpisode?.invoke(seriesNextId, seriesNextTitle.orEmpty())
                                                    onDismiss()
                                                },
                                            ).padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.novel_series_next, seriesNextTitle.orEmpty()),
                                        style = MiuixTheme.textStyles.body1,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (chapters.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.novel_toc),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                item {
                    ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                        chapters.forEachIndexed { idx, chapter ->
                            if (idx > 0) DividerLine()
                            val isCurrent = chapter.pageIndex == currentPage
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .miuixClickable(
                                            pressedScale = 0.98f,
                                            haptic = true,
                                            onClick = {
                                                onJumpPage(chapter.pageIndex)
                                                onDismiss()
                                            },
                                        ).padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = chapter.title,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                NovelMetaPill(text = stringResource(R.string.novel_go_to_page, chapter.pageIndex + 1))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.novel_page_count, pageCount),
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                ElevatedPanel(contentPadding = PaddingValues(12.dp)) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(pageCount, key = { it }) { pageIdx ->
                            val isSelected = pageIdx == currentPage
                            Box(
                                modifier =
                                    Modifier
                                        .squircleSurface(
                                            color =
                                                if (isSelected) {
                                                    MiuixTheme.colorScheme.primary
                                                } else {
                                                    MiuixTheme.colorScheme.surfaceContainerHighest
                                                },
                                            cornerRadius = 12.dp,
                                        ).miuixClickable(
                                            pressedScale = 0.94f,
                                            haptic = true,
                                            onClick = {
                                                onJumpPage(pageIdx)
                                                onDismiss()
                                            },
                                        ).padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "${pageIdx + 1}",
                                    color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelSettingsBottomSheet(
    show: Boolean,
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    lineSpacing: NovelLineSpacing,
    onLineSpacingChange: (NovelLineSpacing) -> Unit,
    theme: NovelTheme,
    onThemeChange: (NovelTheme) -> Unit,
    layoutMode: NovelLayoutMode,
    onLayoutModeChange: (NovelLayoutMode) -> Unit,
    fontFamily: NovelFontFamily,
    onFontFamilyChange: (NovelFontFamily) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    val performHaptic = rememberHapticFeedbackAction()
    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.novel_display_settings),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ElevatedPanel {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.novel_font_size),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Slider(
                    value = fontSize,
                    onValueChange = {
                        if (it.toInt() != fontSize.toInt()) {
                            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
                        }
                        onFontSizeChange(it)
                    },
                    valueRange = MIN_FONT_SIZE..MAX_FONT_SIZE,
                    steps = FONT_SIZE_STEPS,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ElevatedPanel {
                Text(
                    text = stringResource(R.string.novel_font_family),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NovelFontFamily.entries.forEach { font ->
                        val isSelected = font == fontFamily
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .squircleSurface(
                                        color =
                                            if (isSelected) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.surfaceContainerHighest
                                            },
                                        cornerRadius = 14.dp,
                                    ).miuixClickable(
                                        pressedScale = 0.95f,
                                        haptic = true,
                                        onClick = { onFontFamilyChange(font) },
                                    ).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(font.labelRes),
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }

            ElevatedPanel {
                Text(
                    text = stringResource(R.string.novel_line_height),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NovelLineSpacing.entries.forEach { spacing ->
                        val isSelected = spacing == lineSpacing
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .squircleSurface(
                                        color =
                                            if (isSelected) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.surfaceContainerHighest
                                            },
                                        cornerRadius = 14.dp,
                                    ).miuixClickable(
                                        pressedScale = 0.95f,
                                        haptic = true,
                                        onClick = { onLineSpacingChange(spacing) },
                                    ).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(spacing.labelRes),
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }

            ElevatedPanel {
                Text(
                    text = stringResource(R.string.novel_theme),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NovelTheme.entries.forEach { t ->
                        val isSelected = t == theme
                        val panelColor =
                            when (t) {
                                NovelTheme.System -> MiuixTheme.colorScheme.surfaceContainerHighest
                                NovelTheme.Sepia -> Color(SEPIA_PANEL_COLOR)
                                NovelTheme.Dark -> Color(DARK_PANEL_COLOR)
                                NovelTheme.Black -> Color(BLACK_PANEL_COLOR)
                            }
                        val labelColor =
                            when (t) {
                                NovelTheme.System -> MiuixTheme.colorScheme.onSurface
                                NovelTheme.Sepia -> Color(SEPIA_TEXT_COLOR)
                                NovelTheme.Dark, NovelTheme.Black -> Color.White
                            }
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .squircleSurface(
                                        color = panelColor,
                                        cornerRadius = 14.dp,
                                    ).then(
                                        if (isSelected) {
                                            Modifier.squircleBorder(
                                                width = 2.dp,
                                                color = MiuixTheme.colorScheme.primary,
                                                cornerRadius = 14.dp,
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ).miuixClickable(pressedScale = 0.95f, haptic = true, onClick = { onThemeChange(t) })
                                    .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(t.displayNameRes),
                                color = labelColor,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
                }
            }

            ElevatedPanel {
                Text(
                    text = stringResource(R.string.novel_layout_mode),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NovelLayoutMode.entries.forEach { mode ->
                        val isSelected = mode == layoutMode
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .squircleSurface(
                                        color =
                                            if (isSelected) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.surfaceContainerHighest
                                            },
                                        cornerRadius = 14.dp,
                                    ).miuixClickable(
                                        pressedScale = 0.95f,
                                        haptic = true,
                                        onClick = { onLayoutModeChange(mode) },
                                    ).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(mode.labelRes),
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                    }
                }
            }
        }
    }
}
