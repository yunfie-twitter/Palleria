package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.screens.novel.NovelPage
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import com.yunfie.illustia.ui.screens.novel.reader.NovelReaderVerticalPage as SharedNovelReaderVerticalPage

@Composable
internal fun NovelReaderVerticalPage(
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
    onJumpPage: (Int) -> Unit,
    onToggleControls: () -> Unit,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    readingBlockIndex: Int? = null,
) {
    SharedNovelReaderVerticalPage(
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
        onJumpPage = onJumpPage,
        onToggleControls = onToggleControls,
        scrollBehavior = scrollBehavior,
        contentPadding = contentPadding,
        modifier = modifier,
        readingBlockIndex = readingBlockIndex,
    )
}
