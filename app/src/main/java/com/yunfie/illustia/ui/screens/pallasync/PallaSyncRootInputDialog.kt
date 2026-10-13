package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField

/** §5.5: foreground input, no saved state/clipboard/QR, clear on submit/stop/cancel/idle timeout. */
@Composable
internal fun PallaSyncRootInputDialog(
    title: String,
    explanation: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var mnemonic by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var interaction by remember { mutableLongStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)
    val activity = LocalContext.current.pallaSyncInputActivity()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, activity) {
        val protection = activity?.let(PallaSyncSecureInput::protect)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    mnemonic = ""
                    passphrase = ""
                    dismiss()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            mnemonic = ""
            passphrase = ""
            owner.lifecycle.removeObserver(observer)
            protection?.close()
        }
    }
    LaunchedEffect(interaction) {
        delay(600_000)
        mnemonic = ""
        passphrase = ""
        dismiss()
    }
    PallaSyncProtectedDialog(title = title, onDismissRequest = {
        mnemonic = ""
        passphrase = ""
        dismiss()
    }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(explanation)
            Text(stringResource(R.string.pallasync_v3_root_input_explanation))
            if (activity == null) {
                Text(stringResource(R.string.pallasync_v3_secure_input_unavailable))
            } else if (busy) {
                Text(stringResource(R.string.pallasync_v3_root_working))
            } else {
                TextField(
                    value = mnemonic,
                    onValueChange = {
                        mnemonic = it
                        interaction++
                    },
                    label = stringResource(R.string.pallasync_v3_root_input_words),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-root-mnemonic"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                )
                TextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        interaction++
                    },
                    label = stringResource(R.string.pallasync_v3_root_input_passphrase),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-root-passphrase"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                TextButton(text = stringResource(R.string.action_confirm), enabled = mnemonic.isNotBlank(), onClick = {
                    val words = mnemonic
                    val inputPassphrase = passphrase
                    mnemonic = ""
                    passphrase = ""
                    onConfirm(words, inputPassphrase)
                })
            }
            TextButton(text = stringResource(R.string.action_cancel), onClick = {
                mnemonic = ""
                passphrase = ""
                dismiss()
            })
        }
    }
}
