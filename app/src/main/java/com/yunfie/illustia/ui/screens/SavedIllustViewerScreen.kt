package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SavedIllustViewerScreen(state: IllustiaUiState, viewModel: IllustiaViewModel, onBack: () -> Unit) {
    PredictiveBackGestureHandler(onBack = { viewModel.closeSavedIllustViewer(); onBack() })
    val item = state.selectedSavedIllustId?.let { id -> state.savedIllusts.firstOrNull { it.illustId == id } }
    val imageUrl = item?.localCoverPath?.takeIf { it.isNotBlank() } ?: item?.thumbUrl.orEmpty()
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = item?.title ?: stringResource(R.string.offline_library_title),
                largeTitle = item?.title ?: stringResource(R.string.offline_library_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = { viewModel.closeSavedIllustViewer(); onBack() }) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item?.artistName.orEmpty(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(item?.pageCount?.let { stringResource(R.string.data_items_count, it) }.orEmpty(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            item {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item?.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                )
            }
        }
    }
}
