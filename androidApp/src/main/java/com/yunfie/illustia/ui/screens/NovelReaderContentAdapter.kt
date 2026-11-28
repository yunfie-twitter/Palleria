package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontFamily
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.screens.novel.NovelPage
import com.yunfie.illustia.ui.screens.novel.NovelTtsParagraphLocation
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import com.yunfie.illustia.ui.screens.novel.reader.NovelJumpButton as SharedNovelJumpButton
import com.yunfie.illustia.ui.screens.novel.reader.NovelReaderContinuousContent as SharedNovelReaderContinuousContent
import com.yunfie.illustia.ui.screens.novel.reader.NovelReaderPage as SharedNovelReaderPage

@Composable
internal fun NovelReaderPage(
    page: NovelPage,
    pageIndex: Int,
    paragraphStep: Int = 0,
    keyboardActive: () -> Boolean = { true },
    pageCount: Int,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    fontFamily: FontFamily,
    viewModel: IllustiaViewModel,
    uriHandler: UriHandler,
    seriesNextId: Long? = null,
    seriesNextTitle: String? = null,
    onJumpPage: (Int) -> Unit,
    onOpenSeriesEpisode: ((Long, String) -> Unit)? = null,
    onToggleControls: () -> Unit,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    readingBlockIndex: Int? = null,
) {
    SharedNovelReaderPage(
        page = page,
        pageIndex = pageIndex,
        paragraphStep = paragraphStep,
        keyboardActive = keyboardActive,
        pageCount = pageCount,
        fontSize = fontSize,
        lineHeightMultiplier = lineHeightMultiplier,
        textColor = textColor,
        fontFamily = fontFamily,
        onOpenIllust = viewModel::openIllust,
        loadIllustPreviewUrl = viewModel::getIllustPreviewUrl,
        uriHandler = uriHandler,
        seriesNextId = seriesNextId,
        seriesNextTitle = seriesNextTitle,
        onJumpPage = onJumpPage,
        onOpenSeriesEpisode = onOpenSeriesEpisode,
        onToggleControls = onToggleControls,
        scrollBehavior = scrollBehavior,
        contentPadding = contentPadding,
        modifier = modifier,
        readingBlockIndex = readingBlockIndex,
    )
}

@Composable
internal fun NovelReaderContinuousContent(
    pages: List<NovelPage>,
    lazyListState: LazyListState,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    fontFamily: FontFamily,
    viewModel: IllustiaViewModel,
    uriHandler: UriHandler,
    seriesNextId: Long? = null,
    seriesNextTitle: String? = null,
    onJumpPage: (Int) -> Unit,
    onOpenSeriesEpisode: ((Long, String) -> Unit)? = null,
    onToggleControls: () -> Unit,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    readingLocation: NovelTtsParagraphLocation? = null,
) {
    SharedNovelReaderContinuousContent(
        pages = pages,
        lazyListState = lazyListState,
        fontSize = fontSize,
        lineHeightMultiplier = lineHeightMultiplier,
        textColor = textColor,
        fontFamily = fontFamily,
        onOpenIllust = viewModel::openIllust,
        loadIllustPreviewUrl = viewModel::getIllustPreviewUrl,
        uriHandler = uriHandler,
        seriesNextId = seriesNextId,
        seriesNextTitle = seriesNextTitle,
        onJumpPage = onJumpPage,
        onOpenSeriesEpisode = onOpenSeriesEpisode,
        onToggleControls = onToggleControls,
        scrollBehavior = scrollBehavior,
        contentPadding = contentPadding,
        modifier = modifier,
        readingLocation = readingLocation,
    )
}

@Composable
internal fun NovelJumpButton(
    pageNumber: Int,
    onJumpPage: (Int) -> Unit,
) = SharedNovelJumpButton(pageNumber = pageNumber, onJumpPage = onJumpPage)
