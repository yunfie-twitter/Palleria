package com.yunfie.illustia.ui.screens.pallasync

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.SecureFlagPolicy
import com.yunfie.illustia.ui.components.ElevatedPanel
import top.yukonga.miuix.kmp.basic.Text

/** §5.5: the sensitive modal has its own secure window until its surface is removed. */
@Composable
internal fun PallaSyncProtectedDialog(
    title: String,
    onDismissRequest: () -> Unit,
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
            Text(title)
            content()
        }
    }
}
