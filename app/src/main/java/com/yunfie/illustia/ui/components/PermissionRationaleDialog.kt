package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.PermissionRationaleType
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Contextual rationale dialog displayed before requesting OS permissions.
 * Explains the specific user benefit (notifications or storage) in Palleria's design.
 */
@Composable
fun PermissionRationaleDialog(
    type: PermissionRationaleType,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title =
        when (type) {
            PermissionRationaleType.Notification -> stringResource(R.string.permission_notification_title)
            PermissionRationaleType.Storage -> stringResource(R.string.permission_storage_title)
        }
    val description =
        when (type) {
            PermissionRationaleType.Notification -> stringResource(R.string.permission_notification_desc)
            PermissionRationaleType.Storage -> stringResource(R.string.permission_storage_desc)
        }
    val icon =
        when (type) {
            PermissionRationaleType.Notification -> MiuixIcons.Messages
            PermissionRationaleType.Storage -> MiuixIcons.TopDownloads
        }

    OverlayDialog(
        show = true,
        title = title,
        summary = description,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.permission_later_button),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onGrant,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = stringResource(R.string.permission_grant_button),
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
