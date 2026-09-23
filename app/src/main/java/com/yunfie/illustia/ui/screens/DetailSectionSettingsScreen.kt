package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.DEFAULT_DETAIL_SECTION_ORDER
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DetailSectionSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val orderedSections = normalizeDetailOrder(state.settings.detailSectionOrder)

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.detail_section_settings_title),
                largeTitle = stringResource(R.string.detail_section_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Section(stringResource(R.string.experimental_detail_section)) {
                    ElevatedPanel {
                        orderedSections.forEachIndexed { index, id ->
                            OrderEditorRow(
                                title = detailSectionLabel(id),
                                canMoveUp = index > 0,
                                canMoveDown = index < orderedSections.lastIndex,
                                onMoveUp = {
                                    viewModel.updateDetailSectionOrder(orderedSections.moved(index, index - 1))
                                },
                                onMoveDown = {
                                    viewModel.updateDetailSectionOrder(orderedSections.moved(index, index + 1))
                                },
                            )
                            if (index < orderedSections.lastIndex) DividerLine()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderEditorRow(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    BasicComponent(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        endActions = { MoveButtons(canMoveUp, canMoveDown, onMoveUp, onMoveDown) },
    )
}

@Composable
private fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_up),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(-90f),
        )
    }
    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = stringResource(R.string.action_move_down),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.rotate(90f),
        )
    }
}

private fun normalizeDetailOrder(order: List<String>): List<String> =
    order.filter { it in DEFAULT_DETAIL_SECTION_ORDER }.distinct() +
        DEFAULT_DETAIL_SECTION_ORDER.filterNot { it in order }

private fun <T> List<T>.moved(
    from: Int,
    to: Int,
): List<T> =
    toMutableList().apply {
        add(to, removeAt(from))
    }

@Composable
private fun detailSectionLabel(id: String): String =
    when (id) {
        "tags" -> stringResource(R.string.experimental_detail_tags)
        "description" -> stringResource(R.string.experimental_detail_description)
        "related" -> stringResource(R.string.experimental_detail_related)
        else -> stringResource(R.string.experimental_detail_artist)
    }
