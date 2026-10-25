package com.yunfie.illustia.ui.screens.pallasync

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Clipboard lifetime for the copy action; the clear also dies with this dialog. */
private const val CLIPBOARD_CLEAR_DELAY_MILLIS = 60_000L

/** The display is transient and must disappear on its own even if the user walks away. */
private const val MNEMONIC_INACTIVITY_TIMEOUT_MILLIS = 600_000L

/** Positions checked by the backup confirmation; far apart so a shifted copy is noticed. */
private val CONFIRMATION_POSITIONS = listOf(2, 6)

private const val MNEMONIC_CLIP_LABEL = "Palleria recovery mnemonic"

/** Word chips are short labels, not controls: a small radius keeps the numbered list calm. */
private val WORD_CHIP_SHAPE = RoundedCornerShape(10.dp)

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
    var passphraseVisible by remember(phrase) { mutableStateOf(false) }
    var backupConfirmed by remember(phrase) { mutableStateOf(false) }
    var copyCountdown by remember(phrase) { mutableIntStateOf(0) }
    val words = remember(phrase) { visiblePhrase.split(' ').filter { it.isNotBlank() } }
    val context = LocalContext.current
    val activity = context.pallaSyncInputActivity()
    val performHaptic = rememberHapticFeedbackAction()
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
        delay(MNEMONIC_INACTIVITY_TIMEOUT_MILLIS)
        clearAndDismiss()
    }
    // The countdown only reports the pending clear; the clear itself dies with this dialog.
    LaunchedEffect(copyCountdown) {
        if (copyCountdown <= 0) return@LaunchedEffect
        delay(1_000L)
        copyCountdown--
        if (copyCountdown == 0) clearMnemonicClipboard(context)
    }
    PallaSyncProtectedDialog(
        title = stringResource(R.string.pallasync_v3_mnemonic),
        onDismissRequest = clearAndDismiss,
        subtitle = stringResource(R.string.pallasync_v3_mnemonic_explanation),
        actions = {
            PallaSyncPrimaryAction(
                text = stringResource(R.string.pallasync_v3_mnemonic_saved),
                enabled = visiblePhrase.isNotBlank() && backupConfirmed && !busy,
                onClick = {
                    performHaptic(AppHapticEffect.Success)
                    val input = passphrase
                    visiblePhrase = ""
                    passphrase = ""
                    onSaved(input)
                },
            )
            PallaSyncSecondaryAction(text = stringResource(R.string.action_cancel), onClick = clearAndDismiss)
        },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            when {
                activity == null -> Text(stringResource(R.string.pallasync_v3_secure_input_unavailable))
                busy -> PallaSyncWorkingState(text = stringResource(R.string.pallasync_v3_root_working))
                else -> {
                    MnemonicGrid(words = words)
                    MnemonicCopyAction(
                        enabled = words.isNotEmpty(),
                        countdown = copyCountdown,
                        onCopy = {
                            if (copyMnemonicSensitive(context, words)) {
                                performHaptic(AppHapticEffect.Click)
                                copyCountdown = (CLIPBOARD_CLEAR_DELAY_MILLIS / 1_000L).toInt()
                                interaction++
                            }
                        },
                    )
                    MnemonicBackupCheck(
                        words = words,
                        confirmed = backupConfirmed,
                        onConfirmed = {
                            backupConfirmed = true
                            performHaptic(AppHapticEffect.Success)
                        },
                    )
                    Text(
                        text = stringResource(R.string.pallasync_v3_genesis_passphrase_explanation),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    TextField(
                        value = passphrase,
                        onValueChange = {
                            passphrase = it
                            interaction++
                        },
                        label = stringResource(R.string.pallasync_v3_root_input_passphrase),
                        modifier = Modifier.fillMaxWidth().testTag("pallasync-genesis-passphrase"),
                        visualTransformation =
                            if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                passphraseVisible = !passphraseVisible
                                performHaptic(AppHapticEffect.Toggle)
                                interaction++
                            }) {
                                Icon(
                                    imageVector = if (passphraseVisible) MiuixIcons.Hide else MiuixIcons.Show,
                                    contentDescription =
                                        stringResource(
                                            if (passphraseVisible) {
                                                R.string.pallasync_v3_passphrase_hide
                                            } else {
                                                R.string.pallasync_v3_passphrase_show
                                            },
                                        ),
                                    tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Numbered words: position labels make order mistakes visible while copying by hand. */
@Composable
private fun MnemonicGrid(words: List<String>) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 116.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).testTag("pallasync-mnemonic-grid"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        itemsIndexed(words) { index, word ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh, WORD_CHIP_SHAPE)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "%02d".format(index + 1),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
                Text(
                    text = word,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                )
            }
        }
    }
}

/**
 * Clipboard copy is only offered where the clip can be marked sensitive (API 33+).
 * Older releases cannot stop other apps from reading the clipboard, so they get manual entry.
 */
@Composable
private fun MnemonicCopyAction(
    enabled: Boolean,
    countdown: Int,
    onCopy: () -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Text(
            text = stringResource(R.string.pallasync_v3_mnemonic_copy_unavailable),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    TextButton(
        text = stringResource(R.string.pallasync_v3_mnemonic_copy),
        onClick = onCopy,
        enabled = enabled,
        modifier = Modifier.testTag("pallasync-mnemonic-copy"),
    )
    if (countdown > 0) {
        Text(
            text = stringResource(R.string.pallasync_v3_mnemonic_copied, countdown),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.testTag("pallasync-mnemonic-copy-countdown"),
        )
    }
}

/**
 * Confirmation step: the user must pick the word at specific positions.
 *
 * A plain "I saved it" button is one accidental tap. Selecting the word at a given position can
 * only succeed by consulting the written copy, which is what this step is meant to prove.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MnemonicBackupCheck(
    words: List<String>,
    confirmed: Boolean,
    onConfirmed: () -> Unit,
) {
    if (words.size <= CONFIRMATION_POSITIONS.max()) return
    val picks = remember(words) { mutableStateListOf(-1, -1) }
    var wrongSlot by remember(words) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.pallasync_v3_mnemonic_verify_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
        if (confirmed) {
            Text(
                text = stringResource(R.string.pallasync_v3_mnemonic_verify_ok),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.testTag("pallasync-mnemonic-confirmed"),
            )
            return@Column
        }
        val nextSlot = picks.indexOfFirst { it < 0 }
        Text(
            text = stringResource(R.string.pallasync_v3_mnemonic_verify_prompt, CONFIRMATION_POSITIONS[nextSlot] + 1),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        if (wrongSlot >= 0) {
            Text(
                text = stringResource(R.string.pallasync_v3_mnemonic_verify_wrong),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.error,
                modifier = Modifier.testTag("pallasync-mnemonic-wrong"),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            words.indices.forEach { index ->
                TextButton(
                    text = "%02d".format(index + 1),
                    onClick = {
                        val slot = picks.indexOfFirst { it < 0 }
                        if (slot < 0) return@TextButton
                        if (index == CONFIRMATION_POSITIONS[slot]) {
                            picks[slot] = index
                            wrongSlot = -1
                            if (picks.all { it >= 0 }) onConfirmed()
                        } else {
                            wrongSlot = slot
                        }
                    },
                    enabled = !picks.contains(index),
                    modifier = Modifier.testTag("pallasync-mnemonic-pick-$index"),
                )
            }
        }
    }
}

/** Copies the phrase with the sensitive flag set so the system hides it from clipboard previews. */
private fun copyMnemonicSensitive(
    context: Context,
    words: List<String>,
): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val clip = ClipData.newPlainText(MNEMONIC_CLIP_LABEL, words.joinToString(" "))
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    return runCatching { manager.setPrimaryClip(clip) }.isSuccess
}

/** Clears the clipboard only if this app still owns the phrase it placed there. */
private fun clearMnemonicClipboard(context: Context) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    runCatching {
        if (manager.primaryClipDescription?.label?.toString() == MNEMONIC_CLIP_LABEL) manager.clearPrimaryClip()
    }
}
