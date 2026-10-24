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
import top.yukonga.miuix.kmp.preference.CheckboxPreference

internal data class PallaSyncLegacyMigrationInput(
    val sourceProtocol: String,
    val sourceUrl: String,
    val sourceChainId: String,
    val sourceDeviceId: String,
    val sourceDevicePublicKey: String,
    val sourceDeviceSeed: String,
    val mnemonic: String,
    val passphrase: String,
    val writersStopped: Boolean,
)

/** §9.1/§9.4: explicit, foreground-only source credentials and freeze confirmation. */
@Composable
internal fun PallaSyncLegacyMigrationSourceDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onInspect: (PallaSyncLegacyMigrationInput) -> Unit,
) {
    var sourceProtocol by remember { mutableStateOf("2.1") }
    var sourceUrl by remember { mutableStateOf("") }
    var chainId by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var deviceSeed by remember { mutableStateOf("") }
    var mnemonic by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var writersStopped by remember { mutableStateOf(false) }
    var interaction by remember { mutableLongStateOf(0) }
    val dismiss by rememberUpdatedState(onDismiss)
    val activity = LocalContext.current.pallaSyncInputActivity()
    val owner = LocalLifecycleOwner.current

    fun clearInput() {
        sourceUrl = ""
        chainId = ""
        deviceId = ""
        publicKey = ""
        deviceSeed = ""
        mnemonic = ""
        passphrase = ""
        writersStopped = false
    }

    DisposableEffect(owner, activity) {
        val protection = activity?.let(PallaSyncSecureInput::protect)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    clearInput()
                    dismiss()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            clearInput()
            owner.lifecycle.removeObserver(observer)
            protection?.close()
        }
    }
    LaunchedEffect(interaction) {
        delay(600_000)
        clearInput()
        dismiss()
    }

    PallaSyncProtectedDialog(
        title = stringResource(R.string.pallasync_legacy_source_title),
        onDismissRequest = {
            clearInput()
            dismiss()
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.pallasync_legacy_source_explanation))
            if (activity == null) {
                Text(stringResource(R.string.pallasync_v3_secure_input_unavailable))
            } else if (busy) {
                Text(stringResource(R.string.pallasync_v3_root_working))
            } else {
                fun changed() {
                    interaction++
                }
                Column(Modifier.fillMaxWidth()) {
                    TextButton(text = "v2.0${if (sourceProtocol == "2.0") " ✓" else ""}", onClick = {
                        sourceProtocol = "2.0"
                        changed()
                    })
                    TextButton(text = "v2.1${if (sourceProtocol == "2.1") " ✓" else ""}", onClick = {
                        sourceProtocol = "2.1"
                        changed()
                    })
                }
                TextField(
                    sourceUrl,
                    {
                        sourceUrl = it
                        changed()
                    },
                    label =
                        stringResource(
                            R.string.pallasync_legacy_source_url,
                        ),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-url"),
                )
                TextField(
                    chainId,
                    {
                        chainId = it
                        changed()
                    },
                    label =
                        stringResource(
                            R.string.pallasync_legacy_source_chain,
                        ),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-chain"),
                )
                if (sourceProtocol == "2.1") {
                    TextField(
                        deviceId,
                        {
                            deviceId = it
                            changed()
                        },
                        label = stringResource(R.string.pallasync_legacy_source_device),
                        modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-device"),
                    )
                    TextField(
                        publicKey,
                        {
                            publicKey = it
                            changed()
                        },
                        label = stringResource(R.string.pallasync_legacy_source_public_key),
                        modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-public-key"),
                    )
                    TextField(
                        deviceSeed,
                        {
                            deviceSeed = it
                            changed()
                        },
                        label = stringResource(R.string.pallasync_legacy_source_device_seed),
                        modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-seed"),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    )
                }
                TextField(
                    mnemonic,
                    {
                        mnemonic = it
                        changed()
                    },
                    label = stringResource(R.string.pallasync_v3_root_input_words),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-mnemonic"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                )
                TextField(
                    passphrase,
                    {
                        passphrase = it
                        changed()
                    },
                    label = stringResource(R.string.pallasync_v3_root_input_passphrase),
                    modifier = Modifier.fillMaxWidth().testTag("pallasync-legacy-source-passphrase"),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                CheckboxPreference(
                    title = stringResource(R.string.pallasync_legacy_source_freeze_title),
                    summary = stringResource(R.string.pallasync_legacy_source_freeze_summary),
                    checked = writersStopped,
                    onCheckedChange = {
                        writersStopped = it
                        changed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.pallasync_legacy_source_inspect),
                    enabled =
                        sourceUrl.isNotBlank() && chainId.isNotBlank() && mnemonic.isNotBlank() && writersStopped &&
                            (sourceProtocol == "2.0" || (deviceId.isNotBlank() && publicKey.isNotBlank() && deviceSeed.isNotBlank())),
                    onClick = {
                        val input =
                            PallaSyncLegacyMigrationInput(
                                sourceProtocol,
                                sourceUrl.trim(),
                                chainId.trim(),
                                deviceId.trim(),
                                publicKey.trim(),
                                deviceSeed.trim(),
                                mnemonic.trim(),
                                passphrase,
                                writersStopped,
                            )
                        clearInput()
                        onInspect(input)
                    },
                )
            }
            TextButton(
                text = stringResource(R.string.action_cancel),
                onClick = {
                    clearInput()
                    dismiss()
                },
            )
        }
    }
}
