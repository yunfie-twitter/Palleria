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
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncPolicyGate
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
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
    val appContext = LocalContext.current.applicationContext
    // Keystore のマスターキーが無効化されていると生成に失敗する。composition 中に例外を投げると
    // アプリ全体が落ちるため、失敗は状態として受け取り、この画面の中だけでエラー表示に留める。
    val gate = remember { PallaSyncPolicyGate.createOrNull(appContext) }
    if (gate == null) {
        PallaSyncStorageUnavailableDialog(onDismiss = onDismiss)
        return
    }
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var offer by remember { mutableStateOf<PallaSyncPolicyGate.Offer?>(null) }
    var review by remember { mutableStateOf<PallaSyncPolicyGate.Review?>(null) }
    val currentStep = TrustStep.entries[step]
    val performHaptic = rememberHapticFeedbackAction()
    val title =
        when (currentStep) {
            TrustStep.SOURCE -> R.string.pallasync_v3_source
            TrustStep.KEY -> R.string.pallasync_v3_trust_key
            TrustStep.POLICY -> R.string.pallasync_v3_privacy
        }

    OverlayDialog(show = true, title = stringResource(title), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Position in the ceremony, so the policy step is not mistaken for a one-tap consent.
            TrustStepIndicator(current = currentStep)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(origin)
                when (currentStep) {
                    TrustStep.SOURCE -> {
                        Text(sourceLabel)
                        if (sourceKind != "new_chain_local_state") {
                            // Stated as a limit of this choice, not as a caveat the user can skip.
                            TrustNote(stringResource(R.string.pallasync_v3_source_completeness_limit))
                        }
                    }

                    TrustStep.KEY -> {
                        Text(stringResource(R.string.pallasync_v3_key_explanation))
                        offer?.let { current ->
                            TrustFingerprint(
                                value = current.publicKey,
                                label = stringResource(R.string.pallasync_trust_fingerprint_label),
                            )
                            Text(
                                text = stringResource(R.string.pallasync_trust_key_id, current.keyId),
                                fontSize = 12.sp,
                            )
                        }
                    }

                    TrustStep.POLICY -> {
                        review?.let { current ->
                            Text(stringResource(R.string.pallasync_v3_policy_revision, current.validated.revision))
                            Text(current.markdown)
                            Text(current.disclosure)
                        }
                    }
                }
            }
            if (busy) {
                PallaSyncWorkingState(text = stringResource(R.string.pallasync_trust_working))
            }
            PallaSyncErrorHint(code = failure)
            // The commit action is highlighted and separated; cancel and back never compete with it.
            when (currentStep) {
                TrustStep.POLICY -> {
                    PallaSyncPrimaryAction(
                        text = stringResource(R.string.pallasync_v3_accept_policy),
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                failure = null
                                try {
                                    withContext(Dispatchers.IO) {
                                        gate.accept(checkNotNull(review), explicitlyConfirmed = true)
                                    }
                                    performHaptic(AppHapticEffect.Success)
                                    onAccepted()
                                } catch (error: IllegalStateException) {
                                    failure = error.message?.takeIf { it.startsWith("C_") } ?: "C_RELAY_POLICY_UNAVAILABLE"
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }

                else -> {
                    PallaSyncPrimaryAction(
                        text =
                            stringResource(
                                if (currentStep == TrustStep.SOURCE) {
                                    R.string.action_next
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
                                    when (currentStep) {
                                        TrustStep.SOURCE -> {
                                            offer =
                                                withContext(Dispatchers.IO) {
                                                    gate.chooseSource(origin, sourceKind, explicitlyConfirmed = true)
                                                    gate.inspectRelay(origin)
                                                }
                                            step = TrustStep.KEY.ordinal
                                        }

                                        else -> {
                                            review =
                                                withContext(Dispatchers.IO) {
                                                    gate.confirmKey(checkNotNull(offer), explicitlyConfirmed = true)
                                                    gate.currentReview(origin)
                                                }
                                            step = TrustStep.POLICY.ordinal
                                        }
                                    }
                                    performHaptic(AppHapticEffect.Click)
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
            // Going back only moves the view; a confirmed key or source stays recorded by the gate.
            if (currentStep != TrustStep.SOURCE) {
                PallaSyncSecondaryAction(
                    text = stringResource(R.string.action_back),
                    enabled = !busy,
                    onClick = {
                        step = (currentStep.ordinal - 1).coerceAtLeast(0)
                        failure = null
                    },
                )
            }
            PallaSyncSecondaryAction(text = stringResource(R.string.action_cancel), onClick = onDismiss)
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
    val gate = remember { PallaSyncPolicyGate.createOrNull(context.applicationContext) }
    if (gate == null) {
        PallaSyncStorageUnavailableDialog(onDismiss = onDismiss)
        return
    }
    val accepted = remember { gate.acceptedReview(origin) }
    val scope = rememberCoroutineScope()
    var failure by remember { mutableStateOf<String?>(null) }
    OverlayDialog(show = true, title = stringResource(R.string.pallasync_v3_privacy), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(origin)
                Text(accepted?.markdown ?: stringResource(R.string.pallasync_v3_no_consent))
                Text(accepted?.disclosure.orEmpty())
            }
            // A raw C_* code is only useful as a diagnostic, so it is paired with an action.
            PallaSyncErrorHint(code = failure)
            if (accepted != null) {
                // Withdrawing is the deliberate, irreversible choice here, so it is the highlighted
                // action rather than a quiet text button next to cancel.
                PallaSyncPrimaryAction(
                    text = stringResource(R.string.pallasync_v3_withdraw),
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { gate.withdraw(origin) }
                                onWithdrawn()
                            } catch (error: IllegalStateException) {
                                failure = error.message?.takeIf { it.startsWith("C_") } ?: "C_LOCAL_STORAGE_FULL"
                            }
                        }
                    },
                )
            }
            PallaSyncSecondaryAction(text = stringResource(R.string.action_cancel), onClick = onDismiss)
        }
    }
}

/**
 * 暗号化ストレージを開けなかったときの代替表示。信頼の確認はこれ以上進めないが、アプリ自体は
 * 使い続けられるようにする（従来はここで KeyStoreException が飛んで起動時にクラッシュしていた）。
 */
@Composable
internal fun PallaSyncStorageUnavailableDialog(onDismiss: () -> Unit) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.pallasync_storage_unavailable_title),
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.pallasync_storage_unavailable_body))
            }
            // ここで C_* コードは出さない。既存のヒントは「保存領域がいっぱい」や汎用文言に
            // 対応づいており、鍵の無効化という実際の原因を誤って伝えるため。
            PallaSyncSecondaryAction(text = stringResource(R.string.action_close), onClick = onDismiss)
        }
    }
}
