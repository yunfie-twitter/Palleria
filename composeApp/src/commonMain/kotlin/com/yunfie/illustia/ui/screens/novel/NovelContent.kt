package com.yunfie.illustia.ui.screens.novel

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

sealed interface NovelBlock {
    val contentType: String
        get() =
            when (this) {
                is NovelParagraphBlock -> "paragraph"
                is NovelChapterBlock -> "chapter"
                is NovelPixivImageBlock -> "image"
                is NovelJumpBlock -> "jump"
                NovelSpacerBlock -> "spacer"
            }
}

data class NovelPage(
    val blocks: List<NovelBlock>,
)

data class NovelChapterInfo(
    val title: String,
    val pageIndex: Int,
)

fun extractChapters(pages: List<NovelPage>): List<NovelChapterInfo> =
    pages.mapIndexedNotNull { index, page ->
        page.blocks.filterIsInstance<NovelChapterBlock>().firstOrNull()?.let {
            NovelChapterInfo(title = it.title, pageIndex = index)
        }
    }

data object NovelSpacerBlock : NovelBlock

sealed interface NovelInlineItem {
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

data class InlineRubyInfo(
    val base: String,
    val ruby: String,
    val isEmphasis: Boolean = false,
)

data class NovelParagraphBlock(
    val text: AnnotatedString,
    val items: List<NovelInlineItem> = emptyList(),
    val rubyItems: Map<String, InlineRubyInfo> = emptyMap(),
) : NovelBlock

data class NovelChapterBlock(
    val title: String,
) : NovelBlock

data class NovelPixivImageBlock(
    val illustId: Long,
) : NovelBlock

data class NovelJumpBlock(
    val pageNumber: Int,
) : NovelBlock

fun parseNovelPages(rawText: String): List<NovelPage> = NovelContentParser.parsePages(rawText)

object NovelContentParser {
    private val NEWPAGE_REGEX = Regex("""\s*\[newpage\]\s*""")
    private val TOKEN_PATTERN = Regex("""(\[\[(?:rb|emphasismark|jumpuri):.*?\]\]|\[(?:b|i):.*?\])""")

    fun parsePages(rawText: String): List<NovelPage> {
        val normalized = rawText.replace("\r\n", "\n")
        return normalized
            .split(NEWPAGE_REGEX)
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
        var lastIndex = 0

        TOKEN_PATTERN.findAll(rawParagraph).forEach { match ->
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
