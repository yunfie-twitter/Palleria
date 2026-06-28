package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ThemeSwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String,
    enabled: Boolean = true,
) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ColorSettingRow(
    title: String,
    summary: String,
    color: Color,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        startAction = {
            ColorSwatch(color = color)
        },
        onClick = onClick,
    )
}

@Composable
fun SeedColorPickerDialog(
    show: Boolean,
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    if (!show) return

    var draftColor by remember(initialColor) { mutableStateOf(initialColor) }
    val presets = remember {
        listOf(
            Color(0xFF00BCD4),
            Color(0xFFE91E63),
            Color(0xFF4CAF50),
            Color(0xFF795548),
            Color(0xFF9C27B0),
            Color(0xFF2196F3),
            Color(0xFFFB7299),
        )
    }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.general_seed_color_dialog_title),
        summary = stringResource(R.string.general_seed_color_dialog_desc),
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorSwatch(color = draftColor, size = 32.dp)
                Text(
                    text = colorSummary(draftColor),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1,
                )
            }

            ColorPicker(
                draftColor,
                { draftColor = it },
                modifier = Modifier.fillMaxWidth(),
                colorSpace = ColorSpace.OKHSV,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.general_seed_color_presets),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
                presets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .background(preset, RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = MiuixTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                    .padding(0.dp)
                                    .miuixClickable {
                                        draftColor = preset
                                    },
                            )
                        }
                        repeat(4 - row.size) {
                            Box(modifier = Modifier.weight(1f).height(36.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onConfirm(draftColor) },
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MiuixTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
            ),
    )
}

fun colorSummary(color: Color): String {
    return String.format("#%06X", color.toArgb() and 0xFFFFFF)
}
