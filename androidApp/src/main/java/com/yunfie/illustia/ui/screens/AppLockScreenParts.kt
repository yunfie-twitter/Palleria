package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?,
    enabled: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rows =
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )

    Column(
        modifier = modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
                row.forEach { digit ->
                    PadButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                        size = if (compact) 56.dp else 72.dp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp)) {
            Box(
                modifier = Modifier.size(if (compact) 56.dp else 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (onBiometric != null) {
                    Icon(
                        imageVector = MiuixIcons.Unlock,
                        contentDescription = stringResource(R.string.app_lock_biometric_title),
                        modifier =
                            Modifier
                                .size(if (compact) 32.dp else 40.dp)
                                .alpha(if (enabled) 1f else 0.4f)
                                .clickable(enabled = enabled) { onBiometric() },
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
            PadButton(
                label = "0",
                onClick = { onDigit('0') },
                enabled = enabled,
                size = if (compact) 56.dp else 72.dp,
            )
            Box(
                modifier =
                    Modifier
                        .size(if (compact) 56.dp else 72.dp)
                        .alpha(if (enabled) 1f else 0.4f)
                        .clickable(enabled = enabled) { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Remove,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun PadButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MiuixTheme.colorScheme.surfaceContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                    },
                ).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
            style = MiuixTheme.textStyles.title1,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}
