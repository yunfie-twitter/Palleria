package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class NovelTheme(
    val id: String,
    val displayNameRes: Int,
) {
    System("system", R.string.novel_theme_system),
    Sepia("sepia", R.string.novel_theme_sepia),
    Dark("dark", R.string.novel_theme_dark),
    Black("black", R.string.novel_theme_black),
    ;

    @Composable
    fun backgroundColor(): Color =
        when (this) {
            System -> MiuixTheme.colorScheme.surface
            Sepia -> Color(0xFFF4ECD8)
            Dark -> Color(0xFF1E1E1E)
            Black -> Color(0xFF000000)
        }

    @Composable
    fun textColor(): Color =
        when (this) {
            System -> MiuixTheme.colorScheme.onSurface
            Sepia -> Color(0xFF5F4B32)
            Dark -> Color(0xFFE0E0E0)
            Black -> Color(0xFFCCCCCC)
        }

    companion object {
        fun fromId(id: String?): NovelTheme = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: System
    }
}

private const val LINE_SPACING_COMPACT = 1.35f
private const val LINE_SPACING_NORMAL = 1.65f
private const val LINE_SPACING_RELAXED = 2.0f
private const val RUBY_INLINE_HEIGHT_SCALE = 1.6f
private const val RUBY_INLINE_FONT_SCALE = 0.5f
private const val RUBY_INLINE_LINE_HEIGHT_SCALE = 0.54f
private const val EMPHASIS_INLINE_FONT_SCALE = 0.45f
private const val RUBY_CHAR_WIDTH_SCALE = 0.55f
private const val RUBY_CHAR_PADDING_FACTOR = 1.08f
private const val EMPHASIS_CHAR_PADDING_FACTOR = 1.05f

enum class NovelLineSpacing(
    val id: String,
    val multiplier: Float,
    val labelRes: Int,
) {
    Compact("compact", LINE_SPACING_COMPACT, R.string.novel_line_height_compact),
    Normal("normal", LINE_SPACING_NORMAL, R.string.novel_line_height_normal),
    Relaxed("relaxed", LINE_SPACING_RELAXED, R.string.novel_line_height_relaxed),
    ;

    companion object {
        fun fromId(id: String?): NovelLineSpacing = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Normal
    }
}

enum class NovelFontFamily(
    val id: String,
    val labelRes: Int,
    val fontFamily: FontFamily,
) {
    System("system", R.string.novel_font_system, FontFamily.Default),
    Serif("serif", R.string.novel_font_serif, FontFamily.Serif),
    SansSerif("sans_serif", R.string.novel_font_sans_serif, FontFamily.SansSerif),
    ;

    companion object {
        fun fromId(id: String?): NovelFontFamily = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: System
    }
}

enum class NovelLayoutMode(
    val id: String,
    val labelRes: Int,
) {
    Paged("paged", R.string.novel_layout_paged),
    Scroll("scroll", R.string.novel_layout_scroll),
    Vertical("vertical", R.string.novel_layout_vertical),
    ;

    companion object {
        fun fromId(id: String?): NovelLayoutMode = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Paged
    }
}

internal sealed interface NovelBlock

internal data class NovelPage(
    val blocks: List<NovelBlock>,
)

internal data class NovelChapterInfo(
    val title: String,
    val pageIndex: Int,
)

internal fun extractChapters(pages: List<NovelPage>): List<NovelChapterInfo> =
    pages.mapIndexedNotNull { index, page ->
        page.blocks.filterIsInstance<NovelChapterBlock>().firstOrNull()?.let {
            NovelChapterInfo(title = it.title, pageIndex = index)
        }
    }

internal data object NovelSpacerBlock : NovelBlock

internal sealed interface NovelInlineItem {
    data class Text(
        val content: String,
    ) : NovelInlineItem

    data class Ruby(
        val base: String,
        val ruby: String,
    ) : NovelInlineItem

    data class Emphasis(
        val base: String,
        val mark: String,
    ) : NovelInlineItem

    data class Link(
        val title: String,
        val url: String,
    ) : NovelInlineItem

    data class Bold(
        val text: String,
    ) : NovelInlineItem

    data class Italic(
        val text: String,
    ) : NovelInlineItem
}

internal data class InlineRubyInfo(
    val base: String,
    val ruby: String,
    val isEmphasis: Boolean = false,
)

internal data class NovelParagraphBlock(
    val text: AnnotatedString,
    val items: List<NovelInlineItem> = emptyList(),
    val rubyItems: Map<String, InlineRubyInfo> = emptyMap(),
) : NovelBlock

internal data class NovelChapterBlock(
    val title: String,
) : NovelBlock

internal data class NovelPixivImageBlock(
    val illustId: Long,
) : NovelBlock

internal data class NovelJumpBlock(
    val pageNumber: Int,
) : NovelBlock

@Composable
internal fun NovelReaderPage(
    page: NovelPage,
    pageIndex: Int,
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
) {
    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls,
                ),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NovelMetaPill(text = "${pageIndex + 1} / $pageCount")
        }
        items(page.blocks) { block ->
            NovelBlockItem(
                block = block,
                fontSize = fontSize,
                lineHeightMultiplier = lineHeightMultiplier,
                textColor = textColor,
                fontFamily = fontFamily,
                viewModel = viewModel,
                uriHandler = uriHandler,
                onJumpPage = onJumpPage,
                onToggleControls = onToggleControls,
            )
        }
        if (pageIndex == pageCount - 1 && seriesNextId != null) {
            item {
                ElevatedPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 24.dp)
                            .miuixClickable(
                                pressedScale = 0.98f,
                                haptic = true,
                                onClick = { onOpenSeriesEpisode?.invoke(seriesNextId, seriesNextTitle.orEmpty()) },
                            ),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.novel_series_next_episode),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = seriesNextTitle.orEmpty().ifBlank { stringResource(R.string.novel_series_next_episode) },
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
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
) {
    LazyColumn(
        state = lazyListState,
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls,
                ),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        pages.forEachIndexed { pageIndex, page ->
            item(key = "page_header_$pageIndex") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NovelMetaPill(text = "${pageIndex + 1} / ${pages.size}")
                }
            }
            items(page.blocks) { block ->
                NovelBlockItem(
                    block = block,
                    fontSize = fontSize,
                    lineHeightMultiplier = lineHeightMultiplier,
                    textColor = textColor,
                    fontFamily = fontFamily,
                    viewModel = viewModel,
                    uriHandler = uriHandler,
                    onJumpPage = onJumpPage,
                    onToggleControls = onToggleControls,
                )
            }
            if (pageIndex < pages.size - 1) {
                item(key = "page_divider_$pageIndex") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .height(1.dp)
                                .background(textColor.copy(alpha = 0.12f)),
                    )
                }
            }
        }
        if (seriesNextId != null) {
            item(key = "series_next_episode_card") {
                ElevatedPanel(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 24.dp)
                            .miuixClickable(
                                pressedScale = 0.98f,
                                haptic = true,
                                onClick = { onOpenSeriesEpisode?.invoke(seriesNextId, seriesNextTitle.orEmpty()) },
                            ),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.novel_series_next_episode),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = seriesNextTitle.orEmpty().ifBlank { stringResource(R.string.novel_series_next_episode) },
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Icon(
                            MiuixIcons.ChevronForward,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelBlockItem(
    block: NovelBlock,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    fontFamily: FontFamily,
    viewModel: IllustiaViewModel,
    uriHandler: UriHandler,
    onJumpPage: (Int) -> Unit,
    onToggleControls: () -> Unit,
) {
    when (block) {
        NovelSpacerBlock -> {
            Spacer(modifier = Modifier.height(10.dp))
        }

        is NovelChapterBlock -> {
            NovelChapterItem(title = block.title, textColor = textColor, fontFamily = fontFamily)
        }

        is NovelPixivImageBlock -> {
            NovelArtworkCard(
                illustId = block.illustId,
                textColor = textColor,
                viewModel = viewModel,
                onOpen = { viewModel.openIllust(block.illustId) },
            )
        }

        is NovelJumpBlock -> {
            NovelJumpButton(pageNumber = block.pageNumber, onJumpPage = onJumpPage)
        }

        is NovelParagraphBlock -> {
            NovelParagraph(
                block = block,
                fontSize = fontSize,
                lineHeightMultiplier = lineHeightMultiplier,
                textColor = textColor,
                fontFamily = fontFamily,
                uriHandler = uriHandler,
                onToggleControls = onToggleControls,
            )
        }
    }
}

@Composable
private fun NovelChapterItem(
    title: String,
    textColor: Color,
    fontFamily: FontFamily,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            color = textColor,
            fontFamily = fontFamily,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
    }
}

@Composable
internal fun NovelJumpButton(
    pageNumber: Int,
    onJumpPage: (Int) -> Unit,
) {
    Button(
        onClick = { onJumpPage(pageNumber - 1) },
        colors =
            ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.surfaceContainerHighest,
                contentColor = MiuixTheme.colorScheme.onSurface,
            ),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(MiuixIcons.ChevronForward, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text = stringResource(R.string.novel_go_to_page, pageNumber))
        }
    }
}

@Composable
private fun NovelArtworkCard(
    illustId: Long,
    textColor: Color,
    viewModel: IllustiaViewModel,
    onOpen: () -> Unit,
) {
    var previewUrl by remember(illustId) { mutableStateOf<String?>(null) }
    LaunchedEffect(illustId) {
        previewUrl = viewModel.getIllustPreviewUrl(illustId)
    }

    ElevatedPanel(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable(onClick = onOpen),
    ) {
        val currentPreviewUrl = previewUrl
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentPreviewUrl != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    PixivImage(
                        url = currentPreviewUrl,
                        contentDescription = stringResource(R.string.novel_inline_illust_label),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentPreviewUrl == null) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .squircleSurface(MiuixTheme.colorScheme.primaryContainer, 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Photos,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.novel_open_illust),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                    )
                    Text(
                        text = "ID: $illustId",
                        style = MiuixTheme.textStyles.footnote1,
                        color = textColor.copy(alpha = 0.65f),
                    )
                }
                Button(onClick = onOpen) {
                    Text(stringResource(R.string.action_open))
                }
            }
        }
    }
}

@Composable
private fun NovelParagraph(
    block: NovelParagraphBlock,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    fontFamily: FontFamily,
    uriHandler: UriHandler,
    onToggleControls: () -> Unit,
) {
    if (block.text.text.isBlank()) return

    val inlineContentMap =
        remember(block.rubyItems, fontSize, textColor, fontFamily) {
            createInlineRubyContent(block.rubyItems, fontSize, textColor, fontFamily)
        }

    val textStyle =
        MiuixTheme.textStyles.body1.copy(
            fontSize = fontSize.sp,
            lineHeight = (fontSize * lineHeightMultiplier).sp,
            fontFamily = fontFamily,
            color = textColor,
        )

    val urlAnnotations = block.text.getStringAnnotations("URL", 0, block.text.length)
    if (urlAnnotations.isNotEmpty()) {
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        BasicText(
            text = block.text,
            style = textStyle,
            inlineContent = inlineContentMap,
            onTextLayout = { layoutResult = it },
            modifier =
                Modifier.pointerInput(block.text) {
                    detectTapGestures { pos ->
                        val offset = layoutResult?.getOffsetForPosition(pos) ?: -1
                        val annotation =
                            if (offset >= 0) block.text.getStringAnnotations("URL", offset, offset).firstOrNull() else null
                        if (annotation != null) {
                            runCatching { uriHandler.openUri(annotation.item) }
                        } else {
                            onToggleControls()
                        }
                    }
                },
        )
    } else {
        BasicText(
            text = block.text,
            style = textStyle,
            inlineContent = inlineContentMap,
        )
    }
}

private fun createInlineRubyContent(
    rubyItems: Map<String, InlineRubyInfo>,
    fontSize: Float,
    textColor: Color,
    fontFamily: FontFamily,
): Map<String, InlineTextContent> =
    rubyItems.mapValues { (_, info) ->
        val isEmph = info.isEmphasis
        val baseLen = info.base.length.coerceAtLeast(1)
        val rubyLen = info.ruby.length.coerceAtLeast(1)
        val charWidth =
            if (isEmph) {
                baseLen.toFloat() * EMPHASIS_CHAR_PADDING_FACTOR
            } else {
                maxOf(baseLen.toFloat(), rubyLen * RUBY_CHAR_WIDTH_SCALE) * RUBY_CHAR_PADDING_FACTOR
            }
        InlineTextContent(
            Placeholder(
                width = (charWidth * fontSize).sp,
                height = (fontSize * RUBY_INLINE_HEIGHT_SCALE).sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                val topText =
                    if (isEmph) {
                        val mark = info.ruby.ifBlank { "﹅" }
                        if (mark.length == 1) mark.repeat(baseLen) else mark
                    } else {
                        info.ruby
                    }
                Text(
                    text = topText,
                    fontSize = (fontSize * RUBY_INLINE_FONT_SCALE).sp,
                    lineHeight = (fontSize * RUBY_INLINE_LINE_HEIGHT_SCALE).sp,
                    color = textColor.copy(alpha = 0.85f),
                    fontFamily = fontFamily,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = info.base,
                    fontSize = fontSize.sp,
                    lineHeight = fontSize.sp,
                    color = textColor,
                    fontFamily = fontFamily,
                    fontWeight = if (isEmph) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

internal fun parseNovelPages(rawText: String): List<NovelPage> = NovelContentParser.parsePages(rawText)

internal object NovelContentParser {
    fun parsePages(rawText: String): List<NovelPage> {
        val normalized = rawText.replace("\r\n", "\n")
        return normalized
            .split(Regex("""\s*\[newpage\]\s*"""))
            .map { parsePage(it) }
            .ifEmpty { listOf(NovelPage(emptyList())) }
    }

    private fun parsePage(rawPage: String): NovelPage {
        val lines = rawPage.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<NovelBlock>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isEmpty()) return
            val paragraphText = paragraphBuffer.joinToString("\n").trimEnd()
            if (paragraphText.isNotBlank()) {
                blocks += parseParagraph(paragraphText)
            }
            paragraphBuffer.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                flushParagraph()
                blocks += NovelSpacerBlock
                continue
            }
            val specialBlock = parseSpecialBlock(trimmed)
            if (specialBlock != null) {
                flushParagraph()
                blocks += specialBlock
            } else {
                paragraphBuffer += line
            }
        }
        flushParagraph()

        return NovelPage(blocks)
    }

    private fun parseSpecialBlock(trimmed: String): NovelBlock? =
        when {
            trimmed.startsWith("[chapter:") && trimmed.endsWith("]") -> {
                NovelChapterBlock(trimmed.removePrefix("[chapter:").removeSuffix("]"))
            }

            trimmed.startsWith("[pixivimage:") && trimmed.endsWith("]") -> {
                trimmed
                    .removePrefix("[pixivimage:")
                    .removeSuffix("]")
                    .toLongOrNull()
                    ?.let { NovelPixivImageBlock(it) }
            }

            trimmed.startsWith("[jump:") && trimmed.endsWith("]") -> {
                trimmed
                    .removePrefix("[jump:")
                    .removeSuffix("]")
                    .toIntOrNull()
                    ?.let { NovelJumpBlock(it) }
            }

            else -> {
                null
            }
        }

    private fun parseParagraph(rawParagraph: String): NovelParagraphBlock {
        val items = mutableListOf<NovelInlineItem>()
        val pattern = Regex("""(\[\[(?:rb|emphasismark|jumpuri):.*?\]\]|\[(?:b|i):.*?\])""")
        var lastIndex = 0

        pattern.findAll(rawParagraph).forEach { match ->
            if (match.range.first > lastIndex) {
                items += NovelInlineItem.Text(rawParagraph.substring(lastIndex, match.range.first))
            }
            appendParsedTokenItem(match.value, items)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < rawParagraph.length) {
            items += NovelInlineItem.Text(rawParagraph.substring(lastIndex))
        }

        val rubyMap = mutableMapOf<String, InlineRubyInfo>()
        var rubyCounter = 0
        val annotated =
            buildAnnotatedString {
                items.forEach { item ->
                    appendItemToAnnotatedString(item, rubyMap) { "ruby_${rubyCounter++}" }
                }
            }
        return NovelParagraphBlock(text = annotated, items = items, rubyItems = rubyMap)
    }

    private fun appendParsedTokenItem(
        token: String,
        items: MutableList<NovelInlineItem>,
    ) {
        when {
            token.startsWith("[[rb:") -> {
                val inner = token.removePrefix("[[rb:").removeSuffix("]]")
                val parts = inner.split(" > ", limit = 2)
                items += NovelInlineItem.Ruby(base = parts.getOrNull(0).orEmpty(), ruby = parts.getOrNull(1).orEmpty())
            }

            token.startsWith("[[emphasismark:") -> {
                val inner = token.removePrefix("[[emphasismark:").removeSuffix("]]")
                val parts = inner.split(" > ", limit = 2)
                items += NovelInlineItem.Emphasis(base = parts.getOrNull(0).orEmpty(), mark = parts.getOrNull(1).orEmpty().ifBlank { "﹅" })
            }

            token.startsWith("[[jumpuri:") -> {
                val inner = token.removePrefix("[[jumpuri:").removeSuffix("]]")
                val parts = inner.split(" > ", limit = 2)
                items += NovelInlineItem.Link(title = parts.getOrNull(0).orEmpty(), url = parts.getOrNull(1).orEmpty())
            }

            token.startsWith("[b:") -> {
                items += NovelInlineItem.Bold(token.removePrefix("[b:").removeSuffix("]"))
            }

            token.startsWith("[i:") -> {
                items += NovelInlineItem.Italic(token.removePrefix("[i:").removeSuffix("]"))
            }
        }
    }

    private fun AnnotatedString.Builder.appendItemToAnnotatedString(
        item: NovelInlineItem,
        rubyMap: MutableMap<String, InlineRubyInfo>,
        nextKey: () -> String,
    ) {
        when (item) {
            is NovelInlineItem.Text -> {
                append(item.content)
            }

            is NovelInlineItem.Ruby -> {
                val key = nextKey()
                rubyMap[key] = InlineRubyInfo(base = item.base, ruby = item.ruby, isEmphasis = false)
                appendInlineContent(key, "${item.base}（${item.ruby}）")
            }

            is NovelInlineItem.Emphasis -> {
                val key = nextKey()
                rubyMap[key] = InlineRubyInfo(base = item.base, ruby = item.mark, isEmphasis = true)
                appendInlineContent(key, item.base)
            }

            is NovelInlineItem.Link -> {
                if (item.url.isNotBlank()) {
                    pushStringAnnotation(tag = "URL", annotation = item.url)
                }
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(item.title)
                }
                if (item.url.isNotBlank()) {
                    pop()
                }
            }

            is NovelInlineItem.Bold -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(item.text)
                }
            }

            is NovelInlineItem.Italic -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(item.text)
                }
            }
        }
    }
}
