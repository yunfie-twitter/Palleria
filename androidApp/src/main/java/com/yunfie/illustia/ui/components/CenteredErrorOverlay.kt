package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_close
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CenteredErrorOverlay(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    if (message == null) return
    val view = LocalView.current
    val containerSize = LocalWindowInfo.current.containerSize
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Match the dialog surface to the full display so "centre" means the real screen centre,
        // not the space between the status and navigation bars.
        (view.parent as? DialogWindowProvider)?.window?.setLayout(
            containerSize.width,
            containerSize.height,
        )
        val dismissState = remember { MutableInteractionSource() }
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = dismissState,
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .clip(shape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                        // Swallow taps on the card itself so they don't reach the dismissing scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ).padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RequestErrorState(message = message, onRetry = onRetry)
                Button(onClick = onDismiss, colors = overlayActionButtonColors()) {
                    Text(stringResource(Res.string.action_close))
                }
            }
        }
    }
}
