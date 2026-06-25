package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.NovelPreview
import com.yunfie.illustia.data.NovelTextContent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.miuixClickable
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NovelScreen(
    items: List<NovelPreview>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val gridState = remember { LazyGridState() }
    val scrollBehavior = MiuixScrollBehavior()
    val prefetchUrls = remember(items) {
        items.asSequence().take(12).map { it.coverUrl }.toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)

    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            viewModel.refreshNovels()
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.nav_novel),
                largeTitle = stringResource(R.string.nav_novel),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshNovels) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefresh(
            isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
            onRefresh = { viewModel.refreshNovels() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(1),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
                }
                if (items.isEmpty() && loadState != LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(stringResource(R.string.novel_empty))
                    }
                }

                gridItems(items, key = { it.id }, contentType = { "novel_card" }) { novel ->
                    NovelCard(novel = novel, onClick = { viewModel.openNovel(novel) })
                }

                if (nextUrl != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = viewModel::loadMoreNovels,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelCard(
    novel: NovelPreview,
    onClick: () -> Unit,
) {
    ElevatedPanel(modifier = Modifier.fillMaxWidth().miuixClickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(0.76f)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                PixivImage(
                    url = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = true,
                    thumbnail = true,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            ),
                        ),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = novel.title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = novel.userName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = if (novel.caption.isBlank()) {
                        stringResource(R.string.novel_length_label, novel.textLength)
                    } else novel.caption,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovelMetaPill(text = stringResource(R.string.novel_page_count, novel.pageCount))
                    NovelMetaPill(text = stringResource(R.string.novel_bookmark_count, novel.totalBookmarks))
                }
            }
        }
    }
}

@Composable
private fun NovelMetaPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun NovelReaderScreen(
    novel: NovelPreview?,
    text: NovelTextContent?,
    loadState: LoadState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val currentNovel = novel ?: return
    val scrollBehavior = MiuixScrollBehavior()
    val pages = remember(text?.text) {
        text?.text?.let(::parseNovelPages).orEmpty().ifEmpty { listOf(NovelPage(emptyList())) }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = currentNovel.title,
                largeTitle = currentNovel.title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    IconButton(onClick = onRetry) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        when {
            loadState == LoadState.Loading && text == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
            text != null -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    NovelReaderPage(
                        page = pages[pageIndex],
                        pageIndex = pageIndex,
                        pageCount = pages.size,
                        viewModel = viewModel,
                        uriHandler = uriHandler,
                        onJumpPage = { targetPage ->
                            if (targetPage in pages.indices) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.padding(scaffoldPadding),
                    )
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.novel_reader_loading),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelSeriesChip(title: String) {
    ElevatedPanel(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(16.dp),
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Medium,
        )
    }
}

private sealed interface NovelBlock

private data class NovelPage(
    val blocks: List<NovelBlock>,
)

private data object NovelSpacerBlock : NovelBlock

private data class NovelParagraphBlock(
    val text: AnnotatedString,
) : NovelBlock

private data class NovelChapterBlock(
    val title: String,
) : NovelBlock

private data class NovelPixivImageBlock(
    val illustId: Long,
) : NovelBlock

private data class NovelJumpBlock(
    val pageNumber: Int,
) : NovelBlock

@Composable
private fun NovelReaderPage(
    page: NovelPage,
    pageIndex: Int,
    pageCount: Int,
    viewModel: IllustiaViewModel,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onJumpPage: (Int) -> Unit,
    scrollBehavior: top.yukonga.miuix.kmp.basic.ScrollBehavior,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NovelMetaPill(text = "${pageIndex + 1} / $pageCount")
        }
        items(page.blocks) { block ->
            when (block) {
                NovelSpacerBlock -> {
                    Box(modifier = Modifier.padding(vertical = 2.dp))
                }
                is NovelChapterBlock -> {
                    Text(
                        text = block.title,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Black,
                    )
                }
                is NovelPixivImageBlock -> {
                    Button(onClick = { viewModel.openIllust(block.illustId) }) {
                        Text(stringResource(R.string.action_open))
                    }
                }
                is NovelJumpBlock -> {
                    Button(onClick = { onJumpPage(block.pageNumber - 1) }) {
                        Text(text = "Go to page ${block.pageNumber}")
                    }
                }
                is NovelParagraphBlock -> {
                    if (block.text.text.isNotBlank()) {
                        val urlAnnotations = block.text.getStringAnnotations("URL", 0, block.text.length)
                        if (urlAnnotations.isNotEmpty()) {
                            ClickableText(
                                text = block.text,
                                style = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                                onClick = { offset ->
                                    block.text.getStringAnnotations("URL", offset, offset)
                                        .firstOrNull()
                                        ?.let { uriHandler.openUri(it.item) }
                                },
                            )
                        } else {
                            Text(
                                text = block.text,
                                style = MiuixTheme.textStyles.body1,
                                lineHeight = 26.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseNovelPages(rawText: String): List<NovelPage> {
    val normalized = rawText.replace("\r\n", "\n")
    return normalized
        .split(Regex("""\s*\[newpage\]\s*"""))
        .map { parseNovelPage(it) }
        .ifEmpty { listOf(NovelPage(emptyList())) }
}

private fun parseNovelPage(rawPage: String): NovelPage {
    val lines = rawPage.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<NovelBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isEmpty()) return
        val paragraphText = paragraphBuffer.joinToString("\n").trimEnd()
        if (paragraphText.isNotBlank()) {
            blocks += NovelParagraphBlock(parseNovelInlineText(paragraphText))
        }
        paragraphBuffer.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> {
                flushParagraph()
                blocks += NovelSpacerBlock
            }
            trimmed.startsWith("[chapter:") && trimmed.endsWith("]") -> {
                flushParagraph()
                blocks += NovelChapterBlock(trimmed.removePrefix("[chapter:").removeSuffix("]"))
            }
            trimmed.startsWith("[pixivimage:") && trimmed.endsWith("]") -> {
                flushParagraph()
                blocks += NovelPixivImageBlock(trimmed.removePrefix("[pixivimage:").removeSuffix("]").toLongOrNull() ?: continue)
            }
            trimmed.startsWith("[jump:") && trimmed.endsWith("]") -> {
                flushParagraph()
                blocks += NovelJumpBlock(trimmed.removePrefix("[jump:").removeSuffix("]").toIntOrNull() ?: continue)
            }
            else -> paragraphBuffer += line
        }
    }
    flushParagraph()

    return NovelPage(blocks)
}

private fun parseNovelInlineText(text: String): AnnotatedString {
    val pattern = Regex("""(\[\[(?:rb|emphasismark|jumpuri):.*?\]\]|\[(?:b|i):.*?\])""")
    val result = buildAnnotatedString {
        var index = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > index) {
                append(text.substring(index, match.range.first))
            }
            appendNovelToken(match.value)
            index = match.range.last + 1
        }
        if (index < text.length) {
            append(text.substring(index))
        }
    }
    return result
}

private fun AnnotatedString.Builder.appendNovelToken(token: String) {
    when {
        token.startsWith("[[rb:") -> {
            val inner = token.removePrefix("[[rb:").removeSuffix("]]")
            val parts = inner.split(" > ", limit = 2)
            val base = parts.getOrNull(0).orEmpty()
            val ruby = parts.getOrNull(1).orEmpty()
            append(base)
            if (ruby.isNotBlank()) {
                append("（")
                withStyle(
                    SpanStyle(
                        fontSize = 0.72.em,
                    ),
                ) {
                    append(ruby)
                }
                append("）")
            }
        }
        token.startsWith("[[emphasismark:") -> {
            val inner = token.removePrefix("[[emphasismark:").removeSuffix("]]")
            val parts = inner.split(" > ", limit = 2)
            val base = parts.getOrNull(0).orEmpty()
            val mark = parts.getOrNull(1).orEmpty().ifBlank { "﹅" }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(base)
            }
            if (base.isNotBlank()) {
                val repeated = if (mark.length == 1) mark.repeat(base.length.coerceAtLeast(1)) else mark
                withStyle(
                    SpanStyle(
                        fontSize = 0.72.em,
                    ),
                ) {
                    append(repeated)
                }
            }
        }
        token.startsWith("[[jumpuri:") -> {
            val inner = token.removePrefix("[[jumpuri:").removeSuffix("]]")
            val parts = inner.split(" > ", limit = 2)
            val title = parts.getOrNull(0).orEmpty()
            val url = parts.getOrNull(1).orEmpty()
            if (url.isNotBlank()) {
                pushStringAnnotation(tag = "URL", annotation = url)
            }
            withStyle(
                SpanStyle(
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(title)
            }
            if (url.isNotBlank()) {
                pop()
            }
        }
        token.startsWith("[b:") -> {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removePrefix("[b:").removeSuffix("]"))
            }
        }
        token.startsWith("[i:") -> {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(token.removePrefix("[i:").removeSuffix("]"))
            }
        }
    }
}
