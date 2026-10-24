package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.strictRootUtf8
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/** §7.11.4: separate credential, no saved state/autofill/clipboard/preview; caller owns and wipes bytes. */
@Composable
internal fun PallaSyncRecoveryCredentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current
    val activity = LocalContext.current.pallaSyncInputActivity()
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(lifecycle, activity) {
        val protection = activity?.let(PallaSyncSecureInput::protect)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    input = ""
                    dismiss()
                }
            }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            input = ""
            lifecycle.lifecycle.removeObserver(observer)
            protection?.close()
        }
    }
    LaunchedEffect(Unit) {
        delay(300_000)
        input = ""
        dismiss()
    }
    PallaSyncProtectedDialog(stringResource(R.string.pallasync_recovery_offline_credential), {
        input = ""
        onDismiss()
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pallasync_recovery_credential_guidance))
            TextField(
                input,
                { input = it },
                label = stringResource(R.string.pallasync_recovery_offline_credential),
                modifier = Modifier.testTag("pallasync-offline-credential"),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            )
            TextButton(stringResource(R.string.action_confirm), enabled = activity != null && input.isNotEmpty(), onClick = {
                val credential = strictRootUtf8(input)
                input = ""
                var delivered = false
                try {
                    onConfirm(credential)
                    delivered = true
                } finally {
                    if (!delivered) credential.fill(0)
                }
            })
            TextButton(stringResource(R.string.action_cancel), onClick = {
                input = ""
                onDismiss()
            })
        }
    }
}
