package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncPolicyGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/** Three independent affirmative actions; raw policy text never executes HTML or follows links. */
@Composable
internal fun PallaSyncTrustDialog(
    origin: String,
    sourceKind: String,
    sourceLabel: String,
    onDismiss: () -> Unit,
    onAccepted: () -> Unit,
) {
    val context = LocalContext.current
    val gate = remember { PallaSyncPolicyGate(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var offer by remember { mutableStateOf<PallaSyncPolicyGate.Offer?>(null) }
    var review by remember { mutableStateOf<PallaSyncPolicyGate.Review?>(null) }
    val title =
        when (step) {
            0 -> R.string.pallasync_v3_source
            1 -> R.string.pallasync_v3_trust_key
            else -> R.string.pallasync_v3_privacy
        }

    OverlayDialog(show = true, title = stringResource(title), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(origin)
                when (step) {
                    0 -> {
                        Text(sourceLabel)
                        if (sourceKind != "new_chain_local_state") {
                            Text(stringResource(R.string.pallasync_v3_source_completeness_limit))
                        }
                    }

                    1 -> {
                        Text(stringResource(R.string.pallasync_v3_key_explanation))
                        Text(offer?.keyId.orEmpty())
                        Text(offer?.publicKey.orEmpty())
                    }

                    else -> {
                        review?.let { current ->
                            Text(stringResource(R.string.pallasync_v3_policy_revision, current.validated.revision))
                            Text(current.markdown)
                            Text(current.disclosure)
                        }
                    }
                }
            }
            failure?.let { Text(it) }
            TextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
            TextButton(
                text =
                    stringResource(
                        if (step ==
                            2
                        ) {
                            R.string.pallasync_v3_accept_policy
                        } else {
                            R.string.action_confirm
                        },
                    ),
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        failure = null
                        try {
                            when (step) {
                                0 -> {
                                    offer =
                                        withContext(Dispatchers.IO) {
                                            gate.chooseSource(origin, sourceKind, explicitlyConfirmed = true)
                                            gate.inspectRelay(origin)
                                        }
                                    step = 1
                                }

                                1 -> {
                                    review =
                                        withContext(Dispatchers.IO) {
                                            gate.confirmKey(checkNotNull(offer), explicitlyConfirmed = true)
                                            gate.currentReview(origin)
                                        }
                                    step = 2
                                }

                                else -> {
                                    withContext(Dispatchers.IO) { gate.accept(checkNotNull(review), explicitlyConfirmed = true) }
                                    onAccepted()
                                }
                            }
                        } catch (error: IllegalStateException) {
                            failure = error.message?.takeIf { it.startsWith("C_") } ?: "C_RELAY_POLICY_UNAVAILABLE"
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun PallaSyncAcceptedPolicyDialog(
    origin: String,
    onDismiss: () -> Unit,
    onWithdrawn: () -> Unit,
) {
    val context = LocalContext.current
    val gate = remember { PallaSyncPolicyGate(context.applicationContext) }
    val accepted = remember { gate.acceptedReview(origin) }
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    OverlayDialog(show = true, title = stringResource(R.string.pallasync_v3_privacy), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(origin)
                Text(accepted?.markdown ?: stringResource(R.string.pallasync_v3_no_consent))
                Text(accepted?.disclosure.orEmpty())
            }
            failure?.let { Text(it) }
            TextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
            if (accepted != null) {
                TextButton(text = stringResource(R.string.pallasync_v3_withdraw), onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { gate.withdraw(origin) }
                            onWithdrawn()
                        } catch (error: IllegalStateException) {
                            failure = error.message?.takeIf { it.startsWith("C_") } ?: "C_LOCAL_STORAGE_FULL"
                        }
                    }
                })
            }
        }
    }
}
