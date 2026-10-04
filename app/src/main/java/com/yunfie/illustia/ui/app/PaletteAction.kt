package com.yunfie.illustia.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class PaletteAction(
    val title: Int,
) {
    Home(R.string.command_palette_home),
    Search(R.string.command_palette_search),
    Ranking(R.string.nav_ranking),
    Bookmarks(R.string.nav_bookmarks_full),
    Novel(R.string.nav_novel),
    Settings(R.string.command_palette_settings),
    Flags(R.string.command_palette_flags),
    Light(R.string.command_palette_light),
    Dark(R.string.command_palette_dark),
    System(R.string.command_palette_system),
}

internal fun matchingPaletteActions(
    query: String,
    labels: Map<PaletteAction, String>,
): List<PaletteAction> {
    val commands =
        PaletteAction.entries.filter {
            it != PaletteAction.Search &&
                labels.getValue(it).contains(query.trim(), ignoreCase = true)
        }
    return if (query.isBlank()) commands else commands + PaletteAction.Search
}

@Composable
internal fun CommandPalette(
    onDismiss: () -> Unit,
    onAction: (PaletteAction, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val labels =
        PaletteAction.entries.associateWith {
            if (it == PaletteAction.Search) stringResource(it.title, query.trim()) else stringResource(it.title)
        }
    val actions = matchingPaletteActions(query, labels)
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun execute() {
        actions.getOrNull(selectedIndex)?.let { onAction(it, query.trim()) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .onPreviewKeyEvent {
                    if (it.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (it.key) {
                            Key.Escape -> {
                                onDismiss()
                                true
                            }

                            Key.DirectionDown -> {
                                selectedIndex = (selectedIndex + 1).coerceAtMost(actions.lastIndex)
                                true
                            }

                            Key.DirectionUp -> {
                                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                true
                            }

                            Key.Enter, Key.NumPadEnter -> {
                                execute()
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }
                }.padding(16.dp),
        ) {
            Text(stringResource(R.string.flag_command_palette_title), modifier = Modifier.padding(bottom = 12.dp))
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    selectedIndex = 0
                },
                label = stringResource(R.string.command_palette_hint),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(actions, key = { _, action -> action.name }) { index, action ->
                    Text(
                        labels.getValue(action),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selectedIndex ==
                                        index
                                    ) {
                                        MiuixTheme.colorScheme.surfaceContainerHighest
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHigh
                                    },
                                ).semantics { selected = selectedIndex == index }
                                .clickable { onAction(action, query.trim()) }
                                .padding(14.dp),
                    )
                }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
        LaunchedEffect(selectedIndex, query) { if (actions.isNotEmpty()) listState.animateScrollToItem(selectedIndex) }
    }
}
