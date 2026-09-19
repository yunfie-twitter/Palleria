package com.yunfie.illustia.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
fun ReportProblemDialog(
    show: Boolean,
    targetTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (problemType: String, message: String) -> Unit,
) {
    if (!show) return

    val performHaptic = rememberHapticFeedbackAction()
    var selectedReason by remember { mutableStateOf(ReportReason.Inappropriate) }
    var messageText by remember { mutableStateOf("") }
    val sheetBackground = LocalBottomSheetBackgroundColor.current

    WindowBottomSheet(
        show = show,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.report_dialog_title),
        backgroundColor = sheetBackground,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Close,
                    contentDescription = stringResource(R.string.action_close),
                )
            }
        },
        onDismissRequest = onDismiss,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (targetTitle.isNotBlank()) {
                Text(
                    text = targetTitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ReportReason.entries.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        performHaptic(AppHapticEffect.Toggle)
                                        selectedReason = reason
                                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(reason.labelRes),
                                style = MiuixTheme.textStyles.body1,
                                color =
                                    if (isSelected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    performHaptic(AppHapticEffect.Toggle)
                                    selectedReason = reason
                                },
                            )
                        }
                    }
                }
            }

            TextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = stringResource(R.string.report_message_hint),
                maxLines = 3,
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onSubmit(selectedReason.apiKey, messageText.trim())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = overlayActionButtonColors(),
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.action_submit_report),
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

enum class ReportReason(
    val apiKey: String,
    val labelRes: Int,
) {
    Commercial("commercial", R.string.report_reason_commercial),
    Infringement("infringement", R.string.report_reason_infringement),
    Inappropriate("illegal", R.string.report_reason_inappropriate),
    Other("other", R.string.report_reason_other),
}
