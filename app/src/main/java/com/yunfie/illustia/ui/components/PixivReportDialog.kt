package com.yunfie.illustia.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The caller owns authentication and displays the API result through the app message UI. */
@Composable
internal fun PixivReportDialog(
    target: String,
    targetUrl: String,
    onSubmit: (String?, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var message by rememberSaveable(targetUrl) { mutableStateOf("") }
    var submitting by remember(targetUrl) { mutableStateOf(false) }
    var failed by rememberSaveable(targetUrl) { mutableStateOf(false) }
    val context = LocalContext.current
    val browserFailedMessage = stringResource(R.string.error_browser_failed)
    OverlayDialog(
        show = true,
        title = stringResource(R.string.report_dialog_title),
        summary = stringResource(R.string.report_dialog_summary, target),
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        onDismissRequest = { if (!submitting) onDismiss() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = message,
                onValueChange = { if (!submitting) message = it },
                label = stringResource(R.string.report_message_hint),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (submitting) {
                Text(stringResource(R.string.report_submitting))
            }
            if (failed) {
                Text(stringResource(R.string.report_browser_fallback))
                Button(
                    onClick = {
                        runCatching {
                            // Resolve a browser, rather than reopening Palleria through its app links.
                            val intent =
                                Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                    selector = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
                                }
                            context.startActivity(intent)
                        }.onFailure { onMessage(browserFailedMessage) }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.detail_open_in_browser))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        if (!submitting) {
                            submitting = true
                            failed = false
                            onSubmit(message.trim().takeIf { it.isNotEmpty() }) { success ->
                                submitting = false
                                failed = !success
                                if (success) onDismiss()
                            }
                        }
                    },
                    enabled = !submitting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_submit_report))
                }
            }
        }
    }
}
