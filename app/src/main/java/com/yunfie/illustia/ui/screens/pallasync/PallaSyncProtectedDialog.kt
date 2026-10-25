package com.yunfie.illustia.ui.screens.pallasync

import android.os.Build
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.LoadingIndicator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** §5.5: the sensitive modal has its own secure window until its surface is removed. */
@Composable
internal fun PallaSyncProtectedDialog(
    title: String,
    onDismissRequest: () -> Unit,
    icon: ImageVector? = MiuixIcons.Lock,
    subtitle: String? = null,
    // Actions are kept apart from content so the commit action always sits under a rule.
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
        val view = LocalView.current
        DisposableEffect(view) {
            // Compose initializes its owner as autofill-enabled; exclude the dialog's ancestor instead.
            val protectedView = (view.parent as? DialogWindowProvider)?.window?.decorView ?: view.rootView
            val original = if (Build.VERSION.SDK_INT >= 26) protectedView.importantForAutofill else null
            if (Build.VERSION.SDK_INT >= 26) protectedView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            onDispose { if (Build.VERSION.SDK_INT >= 26 && original != null) protectedView.importantForAutofill = original }
        }
        ElevatedPanel(contentPadding = PaddingValues(24.dp)) {
            DialogHeader(title = title, subtitle = subtitle, icon = icon)
            content()
            if (actions != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
                actions()
            }
        }
    }
}

/** Badge, title and optional context line: identifies the ceremony before any input is shown. */
@Composable
private fun DialogHeader(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            Box(
                modifier = Modifier.size(56.dp).background(MiuixTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Commit action: the only highlighted button in a protected modal. */
@Composable
internal fun PallaSyncPrimaryAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColorsPrimary(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text)
    }
}

/** Cancel/back action: never competes with the commit action. */
@Composable
internal fun PallaSyncSecondaryAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(text = text, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth())
}

/** Busy feedback: a spinner plus the current step, so a long native call never looks frozen. */
@Composable
internal fun PallaSyncWorkingState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Text(text = text, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceContainer)
    }
}

/**
 * Protocol failures are shown with actionable guidance instead of a bare code.
 * The raw code stays visible so a support report still identifies the exact response.
 */
@Composable
internal fun PallaSyncErrorHint(
    code: String?,
    modifier: Modifier = Modifier,
) {
    if (code.isNullOrBlank()) return
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = pallaSyncErrorGuidance(code),
            color = MiuixTheme.colorScheme.error,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.pallasync_error_code_prefix),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
    }
}
