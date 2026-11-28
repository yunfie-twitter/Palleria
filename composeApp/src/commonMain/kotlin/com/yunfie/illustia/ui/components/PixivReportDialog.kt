package com.yunfie.illustia.ui.components

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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_cancel
import com.yunfie.illustia.resources.action_submit_report
import com.yunfie.illustia.resources.detail_open_in_browser
import com.yunfie.illustia.resources.error_browser_failed
import com.yunfie.illustia.resources.report_browser_fallback
import com.yunfie.illustia.resources.report_dialog_summary
import com.yunfie.illustia.resources.report_dialog_title
import com.yunfie.illustia.resources.report_message_hint
import com.yunfie.illustia.resources.report_submitting
import com.yunfie.illustia.ui.components.AppDialog
import com.yunfie.illustia.ui.components.rememberUrlOpener
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.jetbrains.compose.resources.stringResource as resString

/** The caller owns authentication and displays the API result through the app message UI. */
@Composable
fun PixivReportDialog(
    target: String,
    targetUrl: String,
    onSubmit: (String?, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var message by rememberSaveable(targetUrl) { mutableStateOf("") }
    var submitting by remember(targetUrl) { mutableStateOf(false) }
    var failed by rememberSaveable(targetUrl) { mutableStateOf(false) }
    val openUrl = rememberUrlOpener()
    val browserFailedMessage = stringResource(Res.string.error_browser_failed)
    AppDialog(
        show = true,
        title = stringResource(Res.string.report_dialog_title),
        summary = stringResource(Res.string.report_dialog_summary, target),
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
                label = stringResource(Res.string.report_message_hint),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (submitting) {
                Text(stringResource(Res.string.report_submitting))
            }
            if (failed) {
                Text(stringResource(Res.string.report_browser_fallback))
                Button(
                    onClick = {
                        runCatching {
                            openUrl(targetUrl)
                        }.onFailure { onMessage(browserFailedMessage) }
                    },
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(resString(Res.string.detail_open_in_browser))
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
                    Text(stringResource(Res.string.action_cancel))
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
                    Text(stringResource(Res.string.action_submit_report))
                }
            }
        }
    }
}
