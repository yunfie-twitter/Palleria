package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchToolbar(
    value: String,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
    historyCount: Int = 0,
    onRemoveHistoryItem: ((String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    val performHaptic = rememberHapticFeedbackAction()
    val focusRequester =
        androidx.compose.runtime.remember {
            androidx.compose.ui.focus
                .FocusRequester()
        }
    androidx.compose.runtime.LaunchedEffect(focusRequest, expanded) {
        if (focusRequest > 0 && expanded) {
            focusRequester.requestFocus()
            onFocusRequestHandled()
        }
    }
    SearchBar(
        inputField = {
            InputField(
                modifier = Modifier.focusRequester(focusRequester),
                query = value,
                onQueryChange = onValueChange,
                onSearch = { onSearch() },
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                label = stringResource(R.string.search_placeholder),
            )
        },
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        outsideEndAction = {
            Text(
                modifier =
                    Modifier
                        .padding(start = 12.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                        ) {
                            performHaptic(AppHapticEffect.Click)
                            onExpandedChange(false)
                            onValueChange("")
                            onCancel?.invoke()
                        },
                text = stringResource(R.string.action_cancel),
                color = MiuixTheme.colorScheme.primary,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val query =
                value
                    .trim()
                    .removePrefix("#")
                    .removePrefix("＃")
                    .trim()
            val showLiveSuggestions = query.length >= 2
            val shownHistoryCount = historyCount.coerceAtMost(6)
            val historyItems = if (showLiveSuggestions) emptyList() else suggestions.take(shownHistoryCount)
            val suggestedItems =
                if (showLiveSuggestions) {
                    val nonHistory = suggestions.drop(shownHistoryCount)
                    val matching = nonHistory.filter { it.contains(query, ignoreCase = true) }
                    val combined = (matching + nonHistory).distinct()
                    combined.ifEmpty { listOf(query) }.take(8)
                } else {
                    emptyList()
                }
            if (historyItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_history),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                )
            }
            historyItems.forEach { suggestion ->
                BasicComponent(
                    title = suggestion,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onSuggestionClick(suggestion)
                    },
                    endActions =
                        if (onRemoveHistoryItem != null) {
                            {
                                IconButton(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    onClick = {
                                        performHaptic(AppHapticEffect.Click)
                                        onRemoveHistoryItem(suggestion)
                                    },
                                    minWidth = 36.dp,
                                    minHeight = 36.dp,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Close,
                                        contentDescription = stringResource(R.string.search_delete_history_item),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                )
            }
            if (suggestedItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.search_suggest),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (historyItems.isEmpty()) 0.dp else 8.dp),
                )
            }
            suggestedItems.forEach { suggestion ->
                BasicComponent(
                    title = suggestion,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onSuggestionClick(suggestion)
                    },
                )
            }
        }
    }
}
