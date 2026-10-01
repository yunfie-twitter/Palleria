package com.yunfie.illustia.ui.screens

internal data class NovelTtsParagraphLocation(
    val pageIndex: Int,
    val blockIndexInPage: Int,
    val text: String,
)

internal object NovelTtsTextSanitizer {
    private val RUBY_REGEX = Regex("""\[\[rb:[^>]*>(.*?)\]\]""")
    private val JUMP_REGEX = Regex("""\[jump:\d+\]""")
    private val PIXIV_IMAGE_REGEX = Regex("""\[pixivimage:\d+\]""")
    private val CHAPTER_REGEX = Regex("""\[chapter:(.*?)\]""")
    private val URL_REGEX = Regex("""https?://\S+""")
    private val REPEATED_SYMBOLS_REGEX = Regex("""[…―─—~～・※＊\*\-_=＝]{2,}""")
    private val DECORATIVE_SYMBOLS_REGEX = Regex("""[◆◇■□▲△▼▽★☆◎○●〇・]{1,}""")

    fun extractTtsParagraphLocations(pages: List<NovelPage>): List<NovelTtsParagraphLocation> {
        val result = mutableListOf<NovelTtsParagraphLocation>()
        pages.forEachIndexed { pageIndex, page ->
            page.blocks.forEachIndexed { blockIndex, block ->
                when (block) {
                    is NovelParagraphBlock -> {
                        val rawText = speakableText(block)
                        if (rawText.isNotBlank()) {
                            result += NovelTtsParagraphLocation(pageIndex, blockIndex, rawText)
                        }
                    }

                    is NovelChapterBlock -> {
                        if (block.title.isNotBlank()) {
                            result += NovelTtsParagraphLocation(pageIndex, blockIndex, block.title)
                        }
                    }

                    else -> {}
                }
            }
        }
        return result
    }

    fun speakableText(block: NovelParagraphBlock): String {
        if (block.items.isNotEmpty()) {
            val sb = StringBuilder()
            for (item in block.items) {
                when (item) {
                    is NovelInlineItem.Text -> sb.append(item.content)
                    is NovelInlineItem.Ruby -> sb.append(item.ruby.ifBlank { item.base })
                    is NovelInlineItem.Emphasis -> sb.append(item.base)
                    is NovelInlineItem.Link -> sb.append(item.title)
                    is NovelInlineItem.Bold -> sb.append(item.text)
                    is NovelInlineItem.Italic -> sb.append(item.text)
                }
            }
            return sb.toString().trim()
        }
        return block.text.text.trim()
    }

    fun sanitizeParagraph(
        paragraphText: String,
        skipSymbols: Boolean = true,
        customDictionary: Map<String, String> = emptyMap(),
    ): String {
        var processed = paragraphText

        if (customDictionary.isNotEmpty()) {
            customDictionary.forEach { (word, reading) ->
                if (word.isNotBlank()) {
                    processed = processed.replace(word, reading)
                }
            }
        }

        processed =
            processed
                .replace(RUBY_REGEX, "$1")
                .replace(JUMP_REGEX, "")
                .replace(PIXIV_IMAGE_REGEX, "")
                .replace(CHAPTER_REGEX, "$1")

        if (skipSymbols) {
            processed =
                processed
                    .replace(URL_REGEX, "")
                    .replace(REPEATED_SYMBOLS_REGEX, "、")
                    .replace(DECORATIVE_SYMBOLS_REGEX, "")
        }

        return processed.trim()
    }

    fun sanitizeTextToParagraphs(
        rawText: String,
        skipSymbols: Boolean = true,
        customDictionary: Map<String, String> = emptyMap(),
    ): List<String> {
        val pages = parseNovelPages(rawText)
        val locations = extractTtsParagraphLocations(pages)
        if (locations.isNotEmpty()) {
            return locations.map { loc ->
                sanitizeParagraph(loc.text, skipSymbols, customDictionary).ifBlank { "、" }
            }
        }

        // フォールバック: パース結果が空の場合
        return rawText
            .split("\n")
            .map { sanitizeParagraph(it, skipSymbols, customDictionary) }
            .filter { it.isNotBlank() }
    }
}
