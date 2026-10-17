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

/** Root input display is transient, screenshot-protected and cleared on stop/cancel/timeout. */
@Composable
internal fun PallaSyncMnemonicDialog(
    phrase: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
    busy: Boolean = false,
) {
    var visiblePhrase by remember(phrase) { mutableStateOf(phrase) }
    var passphrase by remember(phrase) { mutableStateOf("") }
    var interaction by remember(phrase) { mutableLongStateOf(0) }
    val activity = LocalContext.current.pallaSyncInputActivity()
    val dismiss by rememberUpdatedState(onDismiss)
    val clearAndDismiss = {
        visiblePhrase = ""
        passphrase = ""
        dismiss()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, activity, phrase) {
        val protection = activity?.let(PallaSyncSecureInput::protect)
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) clearAndDismiss() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            visiblePhrase = ""
            passphrase = ""
            owner.lifecycle.removeObserver(observer)
            protection?.close()
        }
    }
    LaunchedEffect(phrase, interaction) {
        delay(600_000)
        clearAndDismiss()
    }
    PallaSyncProtectedDialog(title = stringResource(R.string.pallasync_v3_mnemonic), onDismissRequest = clearAndDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pallasync_v3_mnemonic_explanation))
            if (activity == null) {
                Text(stringResource(R.string.pallasync_v3_secure_input_unavailable))
            } else if (busy) {
                Text(stringResource(R.string.pallasync_v3_root_working))
            } else {
                Text(visiblePhrase)
                Text(stringResource(R.string.pallasync_v3_genesis_passphrase_explanation))
                TextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        interaction++
                    },
                    label = stringResource(R.string.pallasync_v3_root_input_passphrase),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-genesis-passphrase"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                TextButton(text = stringResource(R.string.pallasync_v3_mnemonic_saved), enabled = visiblePhrase.isNotBlank(), onClick = {
                    val input = passphrase
                    visiblePhrase = ""
                    passphrase = ""
                    onSaved(input)
                })
            }
            TextButton(text = stringResource(R.string.action_cancel), onClick = clearAndDismiss)
        }
    }
}
