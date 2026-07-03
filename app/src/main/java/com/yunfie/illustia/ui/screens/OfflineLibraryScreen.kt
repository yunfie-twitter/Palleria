package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustGrid
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OfflineLibraryScreen(state: IllustiaUiState, viewModel: IllustiaViewModel, onBack: () -> Unit) {
    PredictiveBackGestureHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.loadSavedLibrary() }

    val scrollBehavior = MiuixScrollBehavior()
    val columns = adaptiveIllustColumns(state.settings)
    val savedIllusts = state.savedIllusts.map(SavedIllustEntity::toIllust)

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.offline_library_title),
                largeTitle = stringResource(R.string.offline_library_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
            )
        },
    ) { padding ->
        IllustGrid(
            illusts = savedIllusts,
            emptyMessage = stringResource(R.string.offline_library_empty),
            onOpenIllust = { viewModel.openIllust(it) },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            columns = columns,
            showBookmarkButton = false,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 14.dp,
                bottom = 96.dp,
            ),
        )
    }
}

private fun SavedIllustEntity.toIllust(): Illust {
    val previewUrl = localCoverPath?.takeIf { it.isNotBlank() } ?: thumbUrl.orEmpty()
    return Illust(
        id = illustId,
        title = title.orEmpty(),
        type = if (pageCount > 1) "manga" else "illust",
        caption = "",
        artistId = artistId,
        artistName = artistName.orEmpty(),
        artistAvatarUrl = null,
        squareImageUrl = previewUrl,
        mediumImageUrl = previewUrl,
        imageUrl = previewUrl,
        originalImageUrl = null,
        mediumImagePages = emptyList(),
        imagePages = emptyList(),
        originalImagePages = emptyList(),
        tags = emptyList(),
        pageCount = pageCount,
        isBookmarked = false,
    )
}
