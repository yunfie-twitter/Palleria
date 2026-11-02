package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.Restrict
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun BookmarkTagPickerBottomSheet(
    illust: Illust,
    state: IllustiaUiState,
    onRestrictChange: (Restrict) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Restrict, List<String>) -> Unit,
) {
    var selectedTags by remember(illust.id) { mutableStateOf(emptySet<String>()) }
    var newTag by remember(illust.id) { mutableStateOf("") }
    val isPrivate = state.bookmarkTagPickerRestrict == Restrict.Private

    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(if (isPrivate) R.string.bookmark_tag_picker_private else R.string.bookmark_tag_picker_public),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.bookmark_tag_picker_private_toggle),
                summary = stringResource(R.string.bookmark_tag_picker_visibility_summary),
                checked = isPrivate,
                onCheckedChange = { onRestrictChange(if (it) Restrict.Private else Restrict.Public) },
            )

            when {
                state.bookmarkTagPickerLoading -> {
                    LoadingIndicator(Modifier.fillMaxWidth())
                }

                state.bookmarkTagPickerError != null -> {
                    Text(
                        text = state.bookmarkTagPickerError,
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

                state.bookmarkTagPickerTags.isEmpty() && selectedTags.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.bookmark_tag_picker_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }

            state.bookmarkTagPickerTags.forEach { tag ->
                val checked = tag.name in selectedTags
                BookmarkTagPickerRow(
                    title = tag.name,
                    checked = checked,
                    onCheckedChange = {
                        if (checked) {
                            selectedTags = selectedTags - tag.name
                        } else if (selectedTags.size < MAX_BOOKMARK_TAGS) {
                            selectedTags = selectedTags + tag.name
                        }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                top.yukonga.miuix.kmp.basic.TextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = stringResource(R.string.bookmark_tag_picker_new_tag),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val tag = newTag.trim()
                        if (tag.isNotEmpty() && selectedTags.size < MAX_BOOKMARK_TAGS) {
                            selectedTags = selectedTags + tag
                            newTag = ""
                        }
                    },
                    enabled = newTag.isNotBlank() && selectedTags.size < MAX_BOOKMARK_TAGS,
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }

            Button(
                onClick = { onConfirm(state.bookmarkTagPickerRestrict, selectedTags.toList()) },
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.bookmark_tag_picker_confirm))
            }
        }
    }
}

@Composable
private fun BookmarkTagPickerRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CheckboxPreference(
        title = title,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val MAX_BOOKMARK_TAGS = 10
