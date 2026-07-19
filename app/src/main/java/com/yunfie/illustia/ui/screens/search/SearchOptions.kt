package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.ChoiceRow
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private val SearchSortOptions = SearchSort.entries.toList()
private val SearchTargetOptions = SearchTarget.entries.toList()
private val SearchDurationOptions = SearchDuration.entries.toList()
private val SearchBookmarkFilterOptions = SearchBookmarkFilter.entries.toList()

@Composable
internal fun SearchOptionsSheet(
    show: Boolean,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
) {
    if (!show) return
    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.search_options),
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        SearchOptionsContent(
            state = state,
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun SearchOptionsContent(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.search_sort_order), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchSortOptions,
            selected = state.settings.searchSort,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchSort,
        )
        Text(stringResource(R.string.search_target), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchTargetOptions,
            selected = state.settings.searchTarget,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchTarget,
        )
        Text(stringResource(R.string.search_duration), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchDurationOptions,
            selected = state.settings.searchDuration,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchDuration,
        )
        Text(stringResource(R.string.search_bookmark_count), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
        ChoiceRow(
            values = SearchBookmarkFilterOptions,
            selected = state.settings.searchBookmarkFilter,
            label = { stringResource(it.labelResId) },
            onSelect = viewModel::updateSearchBookmarkFilter,
        )
    }
}
