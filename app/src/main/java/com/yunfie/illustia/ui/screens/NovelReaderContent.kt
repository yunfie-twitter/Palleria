package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.ElevatedPanel
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

enum class NovelLayoutMode(
    val id: String,
    val labelRes: Int,
) {
    Paged("paged", R.string.novel_layout_paged),
    Scroll("scroll", R.string.novel_layout_scroll),
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
internal fun NovelReaderPage(
    page: NovelPage,
    pageIndex: Int,
    pageCount: Int,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
    viewModel: IllustiaViewModel,
    uriHandler: UriHandler,
    onJumpPage: (Int) -> Unit,
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
                viewModel = viewModel,
                uriHandler = uriHandler,
                onJumpPage = onJumpPage,
                onToggleControls = onToggleControls,
            )
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
    viewModel: IllustiaViewModel,
    uriHandler: UriHandler,
    onJumpPage: (Int) -> Unit,
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
    }
}

@Composable
private fun NovelBlockItem(
    block: NovelBlock,
    fontSize: Float,
    lineHeightMultiplier: Float,
    textColor: Color,
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = block.title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
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

        is NovelPixivImageBlock -> {
            NovelArtworkCard(
                illustId = block.illustId,
                textColor = textColor,
                onOpen = { viewModel.openIllust(block.illustId) },
            )
        }

        is NovelJumpBlock -> {
            Button(
                onClick = { onJumpPage(block.pageNumber - 1) },
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
                    Text(text = stringResource(R.string.novel_go_to_page, block.pageNumber))
                }
            }
        }

        is NovelParagraphBlock -> {
            if (block.text.text.isNotBlank()) {
                val urlAnnotations = block.text.getStringAnnotations("URL", 0, block.text.length)
                val textStyle =
                    MiuixTheme.textStyles.body1.copy(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * lineHeightMultiplier).sp,
                        color = textColor,
                    )
                if (urlAnnotations.isNotEmpty()) {
                    ClickableText(
                        text = block.text,
                        style = textStyle,
                        onClick = { offset ->
                            val annotation = block.text.getStringAnnotations("URL", offset, offset).firstOrNull()
                            if (annotation != null) {
                                uriHandler.openUri(annotation.item)
                            } else {
                                onToggleControls()
                            }
                        },
                    )
                } else {
                    Text(
                        text = block.text,
                        style = textStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelArtworkCard(
    illustId: Long,
    textColor: Color,
    onOpen: () -> Unit,
) {
    ElevatedPanel(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

internal fun parseNovelPages(rawText: String): List<NovelPage> {
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

            else -> {
                paragraphBuffer += line
            }
        }
    }
    flushParagraph()

    return NovelPage(blocks)
}

private fun parseNovelInlineText(text: String): AnnotatedString {
    val pattern = Regex("""(\[\[(?:rb|emphasismark|jumpuri):.*?\]\]|\[(?:b|i):.*?\])""")
    val result =
        buildAnnotatedString {
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
                withStyle(SpanStyle(fontSize = 0.72.em)) {
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
                withStyle(SpanStyle(fontSize = 0.72.em)) {
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
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
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
