package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VerifyNumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
) {
    val rows =
        listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
        )
    val btnAlpha = if (enabled) 1f else 0.4f

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    VerifyPadButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .alpha(btnAlpha)
                        .clickable(enabled = enabled) { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body1,
                )
            }
            VerifyPadButton(label = "0", onClick = { onDigit('0') }, enabled = enabled)
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .alpha(btnAlpha)
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
internal fun VerifyPadButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
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
