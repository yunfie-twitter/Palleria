package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.SavedIllust
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.offline_library_empty
import com.yunfie.illustia.resources.offline_library_title
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustGrid
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import com.yunfie.illustia.ui.components.onTopBarTap
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.smoothScrollToTop
import com.yunfie.illustia.ui.screens.offline.OfflineLibraryActions
import com.yunfie.illustia.visibleWithSettings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OfflineLibraryScreen(
    state: IllustiaUiState,
    viewModel: OfflineLibraryActions,
    onBack: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.loadSavedLibrary() }

    val scrollBehavior = MiuixScrollBehavior()
    val columns = adaptiveIllustColumns(state.settings)
    // 保存作品全件の変換とフィルタはスクロール中も毎回走るため、保存一覧か設定が変わったときだけ計算する。
    val savedIllusts =
        remember(state.savedIllusts, state.settings) {
            state.savedIllusts.map(SavedIllust::toIllust).visibleWithSettings(state.settings)
        }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val performHaptic = rememberHapticFeedbackAction()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.offline_library_title),
                largeTitle = stringResource(Res.string.offline_library_title),
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.onTopBarTap(
                        navIconWidth = 64.dp,
                        actionsWidth = 0.dp,
                        hasBottomContent = false,
                    ) {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            gridState.smoothScrollToTop(scrollBehavior)
                        }
                    },
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
            )
        },
    ) { padding ->
        IllustGrid(
            illusts = savedIllusts,
            state = gridState,
            emptyMessage = stringResource(Res.string.offline_library_empty),
            onOpenIllust = { viewModel.openIllustFromCard(it) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            columns = columns,
            showBookmarkButton = false,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 14.dp,
                    bottom = 96.dp,
                ),
        )
    }
}

private fun SavedIllust.toIllust(): Illust {
    val previewUrl = localCoverPath?.takeIf { it.isNotBlank() } ?: thumbUrl
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
        xRestrict = xRestrict,
    )
}
