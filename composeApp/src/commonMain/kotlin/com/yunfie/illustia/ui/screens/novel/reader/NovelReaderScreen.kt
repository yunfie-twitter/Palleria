package com.yunfie.illustia.ui.screens.novel.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.novel_reader_loading
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.ParagraphNavigation
import com.yunfie.illustia.ui.screens.novel.NovelPage
import com.yunfie.illustia.ui.screens.novel.NovelPageIndexMapping
import com.yunfie.illustia.ui.screens.novel.NovelParagraphBlock
import com.yunfie.illustia.ui.screens.novel.NovelTtsParagraphLocation
import com.yunfie.illustia.ui.screens.novel.NovelTtsTextSanitizer
import com.yunfie.illustia.ui.screens.novel.extractChapters
import com.yunfie.illustia.ui.screens.novel.parseNovelPages
import com.yunfie.illustia.ui.screens.profile.ReaderShortcut
import com.yunfie.illustia.ui.screens.profile.UserProfilePlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val DARK_LUMINANCE_THRESHOLD = 0.5f

@Composable
@Suppress("LongParameterList")
fun NovelReaderScreen(
    novel: NovelPreview?,
    text: NovelTextContent?,
    loadState: LoadState,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    platform: UserProfilePlatform,
    tts: NovelReaderTts?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val currentNovel =
        novel ?: run {
            // ルートが積まれているのに表示対象が無い瞬間（連続で戻る操作をしたときなど）
            // がある。ここで何も描かずに return すると画面全体が空白になるため、
            // 背景とインジケータを必ず出す。
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            return
        }
    val scrollBehavior = MiuixScrollBehavior()
    val pages by produceState(
        initialValue = listOf(NovelPage(emptyList())),
        key1 = text?.text,
    ) {
        val raw = text?.text
        value =
            if (raw.isNullOrBlank()) {
                listOf(NovelPage(emptyList()))
            } else {
                withContext(Dispatchers.Default) {
                    parseNovelPages(raw)
                }.ifEmpty { listOf(NovelPage(emptyList())) }
            }
    }
    val chapters = remember(pages) { extractChapters(pages) }
    val initialPage =
        remember(currentNovel.id, pages.size) {
            val saved = settings.novelProgress[currentNovel.id]?.lastReadPage ?: 0
            saved.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    val continuousListState = rememberLazyListState()
    val isScrolling = pagerState.isScrollInProgress || continuousListState.isScrollInProgress
    platform.ScrollRefreshRateBoost(isScrolling)
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(currentNovel.id, pages.size) {
        val saved = settings.novelProgress[currentNovel.id]?.lastReadPage ?: 0
        if (saved > 0 && saved < pages.size) {
            continuousListState.scrollToItem(NovelPageIndexMapping.pageToItemIndex(pages, saved))
        }
    }

    val fontSize = settings.novelFontSize
    val lineSpacing = remember(settings.novelLineSpacing) { NovelLineSpacing.fromId(settings.novelLineSpacing) }
    val theme = remember(settings.novelTheme) { NovelTheme.fromId(settings.novelTheme) }
    val layoutMode = remember(settings.novelLayoutMode) { NovelLayoutMode.fromId(settings.novelLayoutMode) }
    val fontFamily = remember(settings.novelFontFamily) { NovelFontFamily.fromId(settings.novelFontFamily) }
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var paragraphStep by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // 全画面の復帰書き込みとバー配色の両方で、デバイスのナイトモードではなく
    // アプリのテーマ（MiuixTheme）から解決した値を使う。
    val isDarkTheme = MiuixTheme.colorScheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD
    // 帯の色は Compose（appNavigationScrim）が塗る。window は透過のまま（#647）。
    platform.ApplyReaderFullscreen(fullscreen, isDarkTheme = isDarkTheme)
    LaunchedEffect(fullscreen) { controlsVisible = !fullscreen }
    com.yunfie.illustia.ui.components.ParagraphNavigation(
        continuousListState,
        paragraphStep,
        { layoutMode == NovelLayoutMode.Scroll },
        remember(pages) {
            buildList {
                var offset = 0
                pages.forEachIndexed { index, page ->
                    page.blocks.forEachIndexed { blockIndex, block ->
                        if (block is NovelParagraphBlock) add(offset + 1 + blockIndex)
                    }
                    offset += 1 + page.blocks.size + if (index < pages.lastIndex) 1 else 0
                }
            }
        },
    )
    var showTocSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }

    NovelReaderSystemBarsEffect(theme = theme, isDarkTheme = isDarkTheme, platform = platform)

    val backgroundColor = theme.backgroundColor()
    val textColor = theme.textColor()

    fun currentPageForState(): Int =
        when (layoutMode) {
            NovelLayoutMode.Paged, NovelLayoutMode.Vertical -> {
                pagerState.currentPage
            }

            NovelLayoutMode.Scroll -> {
                NovelPageIndexMapping.itemToPage(pages, continuousListState.firstVisibleItemIndex)
            }
        }

    fun currentNovelPage(): Int = currentPageForState()

    LaunchedEffect(pagerState, continuousListState, layoutMode, pages.size, currentNovel.id) {
        snapshotFlow { currentPageForState() }
            .distinctUntilChanged()
            .collect { page ->
                if (pages.isNotEmpty() && page in pages.indices) {
                    viewModel.updateNovelProgress(
                        novelId = currentNovel.id,
                        page = page,
                        totalPages = pages.size,
                    )
                }
            }
    }

    fun jumpToPage(targetPage: Int) {
        if (targetPage in pages.indices) {
            coroutineScope.launch {
                when (layoutMode) {
                    NovelLayoutMode.Paged, NovelLayoutMode.Vertical -> {
                        pagerState.animateScrollToPage(targetPage)
                    }

                    NovelLayoutMode.Scroll -> {
                        continuousListState.animateScrollToItem(NovelPageIndexMapping.pageToItemIndex(pages, targetPage))
                    }
                }
            }
        }
    }

    val isVolumeTurnerEnabled = settings.isFeatureEnabled(FeatureFlag.VolumeKeyPageTurner)
    val isTtsEnabled = settings.isFeatureEnabled(FeatureFlag.NovelTtsAudiobook)
    val ttsPlayer = tts

    // TTS が無効なときに全文書を走査しても無駄なので、有効なときだけ抽出する。
    val ttsLocations =
        remember(pages, isTtsEnabled) {
            if (isTtsEnabled) NovelTtsTextSanitizer.extractTtsParagraphLocations(pages) else emptyList()
        }
    val currentTtsLocation =
        remember(isTtsEnabled, ttsPlayer?.isPlaying, ttsPlayer?.currentParagraphIndex, ttsLocations) {
            if (isTtsEnabled && ttsPlayer?.isPlaying == true) {
                ttsLocations.getOrNull(ttsPlayer.currentParagraphIndex)
            } else {
                null
            }
        }

    LaunchedEffect(ttsPlayer?.currentParagraphIndex, ttsPlayer?.isPlaying) {
        val player = ttsPlayer ?: return@LaunchedEffect
        if (!player.isPlaying) return@LaunchedEffect
        val loc = ttsLocations.getOrNull(player.currentParagraphIndex) ?: return@LaunchedEffect
        when (layoutMode) {
            NovelLayoutMode.Paged, NovelLayoutMode.Vertical -> {
                if (pagerState.currentPage != loc.pageIndex) {
                    pagerState.animateScrollToPage(loc.pageIndex)
                }
            }

            NovelLayoutMode.Scroll -> {
                val targetItemIndex = NovelPageIndexMapping.blockToItemIndex(pages, loc.pageIndex, loc.blockIndexInPage)
                continuousListState.animateScrollToItem((targetItemIndex - 1).coerceAtLeast(0))
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    val event = keyEvent
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val direction = platform.readerPageDirection(event, isVolumeTurnerEnabled)
                        when {
                            direction != 0 -> {
                                jumpToPage((currentNovelPage() + direction).coerceIn(0, pages.lastIndex))
                                true
                            }

                            else -> {
                                when (platform.readerShortcut(event)) {
                                    ReaderShortcut.ToggleFullscreen -> {
                                        if (!platform.isKeyAutoRepeat(event)) fullscreen = !fullscreen
                                        true
                                    }

                                    ReaderShortcut.ParagraphNext -> {
                                        paragraphStep += 1
                                        true
                                    }

                                    ReaderShortcut.ParagraphPrevious -> {
                                        paragraphStep -= 1
                                        true
                                    }

                                    null -> {
                                        false
                                    }
                                }
                            }
                        }
                    }
                }.focusable(),
        containerColor = backgroundColor,
        topBar = {
            NovelReaderTopBar(
                visible = controlsVisible,
                title = currentNovel.title,
                isBookmarked = currentNovel.isBookmarked,
                backgroundColor = backgroundColor,
                textColor = textColor,
                scrollBehavior = scrollBehavior,
                onBack = onBack,
                onToggleBookmark = { viewModel.toggleNovelBookmark(currentNovel) },
                onRetry = onRetry,
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = controlsVisible && text != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                NovelBottomControlBar(
                    currentPage = currentNovelPage(),
                    pageCount = pages.size,
                    onPageChange = ::jumpToPage,
                    onOpenToc = { showTocSheet = true },
                    onOpenSettings = { showSettingsSheet = true },
                    ttsPlaying = ttsPlayer?.isPlaying == true,
                    onToggleTts =
                        if (isTtsEnabled && ttsPlayer != null && text != null) {
                            {
                                if (ttsPlayer.isPlaying) {
                                    ttsPlayer.pause()
                                } else {
                                    if (ttsPlayer.novelId == currentNovel.id && ttsPlayer.totalParagraphs > 0) {
                                        ttsPlayer.resume()
                                    } else {
                                        val curPage = currentNovelPage()
                                        val initialIndex =
                                            ttsLocations.indexOfFirst { it.pageIndex == curPage }.coerceAtLeast(0)
                                        ttsPlayer.startReading(
                                            rawText = text.text,
                                            startIndex = initialIndex,
                                            novelTitle = currentNovel.title,
                                            authorName = currentNovel.userName,
                                            novelId = currentNovel.id,
                                            rate = settings.novelTtsSpeechRate,
                                            pitchLevel = settings.novelTtsPitch,
                                            voice = settings.novelTtsVoiceName,
                                            skipSymbols = settings.novelTtsSkipSymbols,
                                            customDictionary = settings.novelTtsCustomDictionary,
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                )
            }
        },
    ) { scaffoldPadding ->
        val layoutDirection = LocalLayoutDirection.current
        // スクロール中に毎回作り直されると子のスキップが効かなくなるため、パディングは
        // scaffoldPadding 由来の値が変わったときだけ再生成する。
        val readerPadding =
            remember(scaffoldPadding, layoutDirection) {
                PaddingValues(
                    start = scaffoldPadding.calculateStartPadding(layoutDirection) + 18.dp,
                    top = scaffoldPadding.calculateTopPadding(),
                    end = scaffoldPadding.calculateEndPadding(layoutDirection) + 18.dp,
                    bottom = scaffoldPadding.calculateBottomPadding(),
                )
            }
        when {
            loadState == LoadState.Loading && text == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            text != null -> {
                when (layoutMode) {
                    NovelLayoutMode.Paged -> {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                        ) { pageIndex ->
                            NovelReaderPage(
                                page = pages[pageIndex],
                                pageIndex = pageIndex,
                                paragraphStep = paragraphStep,
                                keyboardActive = { pageIndex == pagerState.settledPage },
                                pageCount = pages.size,
                                fontSize = fontSize,
                                lineHeightMultiplier = lineSpacing.multiplier,
                                textColor = textColor,
                                fontFamily = fontFamily.fontFamily,
                                onOpenIllust = viewModel::openIllust,
                                loadIllustPreviewUrl = viewModel::getIllustPreviewUrl,
                                uriHandler = uriHandler,
                                seriesNextId = text.seriesNextId,
                                seriesNextTitle = text.seriesNextTitle,
                                onJumpPage = ::jumpToPage,
                                onOpenSeriesEpisode = { id, title -> viewModel.openNovelById(id, title) },
                                onToggleControls = { controlsVisible = !controlsVisible },
                                scrollBehavior = scrollBehavior,
                                contentPadding = readerPadding,
                                readingBlockIndex =
                                    if (currentTtsLocation?.pageIndex == pageIndex) {
                                        currentTtsLocation.blockIndexInPage
                                    } else {
                                        null
                                    },
                            )
                        }
                    }

                    NovelLayoutMode.Scroll -> {
                        NovelReaderContinuousContent(
                            pages = pages,
                            lazyListState = continuousListState,
                            fontSize = fontSize,
                            lineHeightMultiplier = lineSpacing.multiplier,
                            textColor = textColor,
                            fontFamily = fontFamily.fontFamily,
                            onOpenIllust = viewModel::openIllust,
                            loadIllustPreviewUrl = viewModel::getIllustPreviewUrl,
                            uriHandler = uriHandler,
                            seriesNextId = text.seriesNextId,
                            seriesNextTitle = text.seriesNextTitle,
                            onJumpPage = ::jumpToPage,
                            onOpenSeriesEpisode = { id, title -> viewModel.openNovelById(id, title) },
                            onToggleControls = { controlsVisible = !controlsVisible },
                            scrollBehavior = scrollBehavior,
                            contentPadding = readerPadding,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                            readingLocation = currentTtsLocation,
                        )
                    }

                    NovelLayoutMode.Vertical -> {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize().background(backgroundColor),
                        ) { pageIndex ->
                            NovelReaderVerticalPage(
                                page = pages[pageIndex],
                                pageIndex = pageIndex,
                                paragraphStep = paragraphStep,
                                keyboardActive = { pageIndex == pagerState.settledPage },
                                pageCount = pages.size,
                                fontSize = fontSize,
                                lineHeightMultiplier = lineSpacing.multiplier,
                                textColor = textColor,
                                fontFamily = fontFamily.fontFamily,
                                onOpenIllust = viewModel::openIllust,
                                loadIllustPreviewUrl = viewModel::getIllustPreviewUrl,
                                onJumpPage = ::jumpToPage,
                                onToggleControls = { controlsVisible = !controlsVisible },
                                scrollBehavior = scrollBehavior,
                                contentPadding = readerPadding,
                                readingBlockIndex =
                                    if (currentTtsLocation?.pageIndex == pageIndex) {
                                        currentTtsLocation.blockIndexInPage
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
            }

            else -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.novel_reader_loading),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    if (showTocSheet) {
        NovelTocBottomSheet(
            show = true,
            currentPage = currentNovelPage(),
            pageCount = pages.size,
            chapters = chapters,
            seriesPrevId = text?.seriesPrevId,
            seriesPrevTitle = text?.seriesPrevTitle,
            seriesNextId = text?.seriesNextId,
            seriesNextTitle = text?.seriesNextTitle,
            onJumpPage = ::jumpToPage,
            onOpenSeriesEpisode = { id, title ->
                viewModel.openNovelById(id, title)
                showTocSheet = false
            },
            onDismiss = { showTocSheet = false },
        )
    }

    NovelSettingsBottomSheet(
        show = showSettingsSheet,
        fontSize = fontSize,
        onFontSizeChange = viewModel::updateNovelFontSize,
        lineSpacing = lineSpacing,
        onLineSpacingChange = { viewModel.updateNovelLineSpacing(it.id) },
        theme = theme,
        onThemeChange = { viewModel.updateNovelTheme(it.id) },
        layoutMode = layoutMode,
        onLayoutModeChange = { newMode ->
            val current = currentNovelPage()
            viewModel.updateNovelLayoutMode(newMode.id)
            jumpToPage(current)
        },
        fontFamily = fontFamily,
        onFontFamilyChange = { viewModel.updateNovelFontFamily(it.id) },
        onDismiss = { showSettingsSheet = false },
        isTtsEnabled = isTtsEnabled,
        ttsSpeechRate = settings.novelTtsSpeechRate,
        onTtsSpeechRateChange = { rate ->
            viewModel.updateNovelTtsSpeechRate(rate)
            ttsPlayer?.setRate(rate)
        },
        ttsPitch = settings.novelTtsPitch,
        onTtsPitchChange = { pitch ->
            viewModel.updateNovelTtsPitch(pitch)
            ttsPlayer?.setPitchLevel(pitch)
        },
        ttsVoiceName = settings.novelTtsVoiceName,
        availableVoices = ttsPlayer?.availableVoices.orEmpty(),
        onTtsVoiceChange = { voice ->
            viewModel.updateNovelTtsVoiceName(voice)
            ttsPlayer?.setVoiceSelection(voice)
        },
        ttsSkipSymbols = settings.novelTtsSkipSymbols,
        onTtsSkipSymbolsChange = viewModel::updateNovelTtsSkipSymbols,
        ttsCustomDictionary = settings.novelTtsCustomDictionary,
        onAddDictionaryEntry = viewModel::addNovelTtsDictionaryEntry,
        onRemoveDictionaryEntry = viewModel::removeNovelTtsDictionaryEntry,
    )
}
