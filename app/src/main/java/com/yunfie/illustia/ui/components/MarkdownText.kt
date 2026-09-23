package com.yunfie.illustia.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val URL_REGEX = Regex("""https?://[^\s)\]]+""")
private val MARKDOWN_LINK_REGEX = Regex("""\[([^\]]+)\]\((https?://[^\s)]+)\)""")
private val BOLD_REGEX = Regex("""\*\*(.*?)\*\*""")
private val ITALIC_REGEX = Regex("""\*(.*?)\*""")
private val CODE_REGEX = Regex("""`([^`]+)`""")

private const val HEADER_LEVEL_1 = 1
private const val HEADER_LEVEL_2 = 2
private const val HEADER_LEVEL_3 = 3
private const val HEADER_H1_SIZE_SP = 17
private const val HEADER_H2_SIZE_SP = 15
private const val HEADER_H3_SIZE_SP = 14
private const val CODE_FONT_SCALE = 0.9f
private const val CODE_BACKGROUND_ALPHA = 0.1f
private const val BLOCKQUOTE_ALPHA = 0.85f
private const val BLOCKQUOTE_CONTAINER_ALPHA = 0.5f

private sealed interface MarkdownBlock {
    data class Header(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class ListItem(
        val bullet: String,
        val text: String,
    ) : MarkdownBlock

    data class Blockquote(
        val text: String,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    baseTextStyle: TextStyle = MiuixTheme.textStyles.footnote1,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    linkColor: Color = MiuixTheme.colorScheme.primary,
) {
    val uriHandler = LocalUriHandler.current
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerStyle =
                        when (block.level) {
                            HEADER_LEVEL_1 -> baseTextStyle.copy(fontSize = HEADER_H1_SIZE_SP.sp, fontWeight = FontWeight.Bold)
                            HEADER_LEVEL_2 -> baseTextStyle.copy(fontSize = HEADER_H2_SIZE_SP.sp, fontWeight = FontWeight.Bold)
                            else -> baseTextStyle.copy(fontSize = HEADER_H3_SIZE_SP.sp, fontWeight = FontWeight.SemiBold)
                        }
                    val annotated =
                        remember(block.text, textColor, linkColor) {
                            buildInlineAnnotatedString(block.text, headerStyle, textColor, linkColor)
                        }
                    ClickableMarkdownLine(
                        annotatedString = annotated,
                        style = headerStyle,
                        onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                    )
                }

                is MarkdownBlock.ListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = block.bullet,
                            style = baseTextStyle.copy(fontWeight = FontWeight.Bold),
                            color = linkColor,
                        )
                        val annotated =
                            remember(block.text, textColor, linkColor) {
                                buildInlineAnnotatedString(block.text, baseTextStyle, textColor, linkColor)
                            }
                        ClickableMarkdownLine(
                            annotatedString = annotated,
                            style = baseTextStyle,
                            modifier = Modifier.weight(1f),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                        )
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = BLOCKQUOTE_CONTAINER_ALPHA))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        val annotated =
                            remember(block.text, textColor, linkColor) {
                                buildInlineAnnotatedString(block.text, baseTextStyle, textColor.copy(alpha = BLOCKQUOTE_ALPHA), linkColor)
                            }
                        ClickableMarkdownLine(
                            annotatedString = annotated,
                            style = baseTextStyle.copy(fontStyle = FontStyle.Italic),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated =
                        remember(block.text, textColor, linkColor) {
                            buildInlineAnnotatedString(block.text, baseTextStyle, textColor, linkColor)
                        }
                    ClickableMarkdownLine(
                        annotatedString = annotated,
                        style = baseTextStyle,
                        onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClickableMarkdownLine(
    annotatedString: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onUrlClick: (String) -> Unit,
) {
    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    onUrlClick(annotation.item)
                }
        },
    )
}

private fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val lines = rawText.lines()
    val blocks = mutableListOf<MarkdownBlock>()

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue

        when {
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(HEADER_LEVEL_3, trimmed.removePrefix("### ").trim()))
            }

            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(HEADER_LEVEL_2, trimmed.removePrefix("## ").trim()))
            }

            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(HEADER_LEVEL_1, trimmed.removePrefix("# ").trim()))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.ListItem("•", trimmed.substring(2).trim()))
            }

            trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                val num = trimmed.substringBefore(".")
                val rest = trimmed.substringAfter(".").trim()
                blocks.add(MarkdownBlock.ListItem("$num.", rest))
            }

            trimmed.startsWith("> ") -> {
                blocks.add(MarkdownBlock.Blockquote(trimmed.removePrefix("> ").trim()))
            }

            else -> {
                blocks.add(MarkdownBlock.Paragraph(trimmed))
            }
        }
    }
    return blocks
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun buildInlineAnnotatedString(
    text: String,
    baseStyle: TextStyle,
    textColor: Color,
    linkColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        var processedText = text

        // Replace markdown links [title](url) with a token or process directly
        val linkMatches = MARKDOWN_LINK_REGEX.findAll(processedText).toList()
        val plainUrlMatches =
            URL_REGEX
                .findAll(processedText)
                .filterNot { urlMatch ->
                    linkMatches.any { lm -> lm.range.contains(urlMatch.range.first) }
                }.toList()

        var currentIndex = 0
        val allSpans = mutableListOf<InlineSpan>()

        for (match in linkMatches) {
            val title = match.groupValues[1]
            val url = match.groupValues[2]
            allSpans.add(InlineSpan(match.range.first, match.range.last + 1, SpanType.Link(title, url)))
        }

        for (match in plainUrlMatches) {
            val url = match.value
            allSpans.add(InlineSpan(match.range.first, match.range.last + 1, SpanType.Link(url, url)))
        }

        for (match in BOLD_REGEX.findAll(processedText)) {
            val inner = match.groupValues[1]
            allSpans.add(InlineSpan(match.range.first, match.range.last + 1, SpanType.Bold(inner)))
        }

        for (match in CODE_REGEX.findAll(processedText)) {
            val inner = match.groupValues[1]
            allSpans.add(InlineSpan(match.range.first, match.range.last + 1, SpanType.Code(inner)))
        }

        // Sort non-overlapping spans by start index
        allSpans.sortBy { it.start }

        var pos = 0
        while (pos < processedText.length) {
            val nextSpan = allSpans.firstOrNull { it.start >= pos }
            if (nextSpan == null) {
                appendClean(processedText.substring(pos), textColor)
                break
            }

            if (nextSpan.start > pos) {
                appendClean(processedText.substring(pos, nextSpan.start), textColor)
            }

            when (val type = nextSpan.type) {
                is SpanType.Link -> {
                    val startPos = length
                    append(type.title)
                    addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                        startPos,
                        length,
                    )
                    addStringAnnotation(tag = "URL", annotation = type.url, start = startPos, end = length)
                }

                is SpanType.Bold -> {
                    val startPos = length
                    append(type.content)
                    addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold, color = textColor),
                        startPos,
                        length,
                    )
                }

                is SpanType.Code -> {
                    val startPos = length
                    append(type.content)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = textColor.copy(alpha = CODE_BACKGROUND_ALPHA),
                            fontSize = (baseStyle.fontSize.value * CODE_FONT_SCALE).sp,
                        ),
                        startPos,
                        length,
                    )
                }
            }
            pos = nextSpan.end
        }
    }

private fun AnnotatedString.Builder.appendClean(
    text: String,
    textColor: Color,
) {
    val clean = text.replace("**", "").replace("`", "")
    val start = length
    append(clean)
    addStyle(SpanStyle(color = textColor), start, length)
}

private sealed interface SpanType {
    data class Link(
        val title: String,
        val url: String,
    ) : SpanType

    data class Bold(
        val content: String,
    ) : SpanType

    data class Code(
        val content: String,
    ) : SpanType
}

private data class InlineSpan(
    val start: Int,
    val end: Int,
    val type: SpanType,
)
