package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.pixiv.BookmarkTag
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
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
    onRetry: () -> Unit,
    onConfirm: (Restrict, List<String>) -> Unit,
) {
    var selectedByRestrict by remember(illust.id) {
        mutableStateOf<Map<Restrict, Set<String>>>(
            mapOf(Restrict.Public to emptySet(), Restrict.Private to emptySet()),
        )
    }
    var tagQuery by remember(illust.id) { mutableStateOf("") }
    var summaryVisible by remember(illust.id) { mutableStateOf(true) }
    val restrict = state.bookmarkTagPickerRestrict
    val selectedTags = selectedByRestrict[restrict].orEmpty()
    val isPrivate = restrict == Restrict.Private
    val availableTags =
        remember(state.bookmarkTagPickerTags, illust.tags) {
            val bookmarkTags = state.bookmarkTagPickerTags.associateBy { it.name }.toMutableMap()
            illust.tags.forEach { tag ->
                if (tag.isNotBlank() && bookmarkTags.keys.none { it.equals(tag, ignoreCase = true) }) {
                    bookmarkTags[tag] = BookmarkTag(tag, 0)
                }
            }
            bookmarkTags.values.map { tag ->
                BookmarkPickerTag(
                    tag.name,
                    tag.count,
                    illust.tags.any { it.equals(tag.name, ignoreCase = true) },
                )
            }
        }
    val visibleTags = availableTags.filter { it.name.contains(tagQuery.trim(), ignoreCase = true) }

    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(if (isPrivate) R.string.bookmark_tag_picker_private else R.string.bookmark_tag_picker_public),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier = Modifier.imePadding().fillMaxWidth().heightIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TabRowWithContour(
                tabs =
                    listOf(
                        stringResource(R.string.bookmark_tag_picker_public_tab),
                        stringResource(R.string.bookmark_tag_picker_private_tab),
                    ),
                selectedTabIndex = if (isPrivate) 1 else 0,
                onTabSelected = { index -> onRestrictChange(if (index == 1) Restrict.Private else Restrict.Public) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (summaryVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (isPrivate) {
                                    R.string.bookmark_tag_picker_private_summary
                                } else {
                                    R.string.bookmark_tag_picker_public_summary
                                },
                            ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    IconButton(onClick = { summaryVisible = false }) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.bookmark_tag_picker_selected_count, selectedTags.size, MAX_BOOKMARK_TAGS),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (availableTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.bookmark_tag_picker_tag_count, availableTags.size),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }

            if (selectedTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedTags.toList(), key = { it }) { tag ->
                        SelectedTagChip(tag = tag) {
                            selectedByRestrict = selectedByRestrict + (restrict to (selectedTags - tag))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                when {
                    state.bookmarkTagPickerLoading -> {
                        item(key = "loading") {
                            LoadingIndicator(Modifier.fillMaxWidth())
                        }
                    }

                    state.bookmarkTagPickerError != null -> {
                        item(key = "error") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = state.bookmarkTagPickerError,
                                    color = MiuixTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                                Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                            }
                        }
                    }

                    availableTags.isEmpty() && tagQuery.isBlank() -> {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.bookmark_tag_picker_empty),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    visibleTags.isEmpty() -> {
                        item(key = "no_matches") {
                            Text(
                                text = stringResource(R.string.bookmark_tag_picker_no_matches),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }

                    else -> {
                        items(visibleTags, key = { it.name }) { tag ->
                            BookmarkTagPickerRow(
                                tag = tag,
                                checked = tag.name in selectedTags,
                                onCheckedChange = { checked ->
                                    selectedByRestrict =
                                        selectedByRestrict +
                                        (restrict to if (checked) selectedTags + tag.name else selectedTags - tag.name)
                                },
                                enabled = tag.name in selectedTags || selectedTags.size < MAX_BOOKMARK_TAGS,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onConfirm(restrict, selectedTags.toList()) },
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(
                    stringResource(
                        if (isPrivate) R.string.bookmark_tag_picker_confirm_private else R.string.bookmark_tag_picker_confirm_public,
                        selectedTags.size,
                    ),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextField(
                    value = tagQuery,
                    onValueChange = { tagQuery = it },
                    label = stringResource(R.string.bookmark_tag_picker_search_or_add),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val name = tagQuery.trim()
                        if (name.isNotEmpty() && selectedTags.size < MAX_BOOKMARK_TAGS) {
                            val existingTag = availableTags.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            val selectedName = existingTag?.name ?: name
                            selectedByRestrict = selectedByRestrict + (restrict to (selectedTags + selectedName))
                            tagQuery = ""
                        }
                    },
                    enabled = tagQuery.isNotBlank() && selectedTags.size < MAX_BOOKMARK_TAGS,
                ) {
                    Text(stringResource(R.string.bookmark_tag_picker_select_or_add))
                }
            }
        }
    }
}

@Composable
private fun SelectedTagChip(
    tag: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .clip(
                    RoundedCornerShape(16.dp),
                ).background(MiuixTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onRemove)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(tag, color = MiuixTheme.colorScheme.onSurface)
        Text("×", color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun BookmarkTagPickerRow(
    tag: BookmarkPickerTag,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    CheckboxPreference(
        title = tag.name,
        summary =
            if (tag.isArtworkTag) {
                if (tag.count > 0) {
                    stringResource(R.string.bookmark_tag_picker_work_tag_with_count, tag.count)
                } else {
                    stringResource(R.string.bookmark_tag_picker_work_tag)
                }
            } else {
                stringResource(R.string.bookmark_tag_picker_tag_count, tag.count)
            },
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class BookmarkPickerTag(
    val name: String,
    val count: Int,
    val isArtworkTag: Boolean,
)

private const val MAX_BOOKMARK_TAGS = 10
