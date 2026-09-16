package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.PixivImage
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val VERTICAL_COLUMN_SPACING_FACTOR = 0.45f
private const val VERTICAL_PADDING_HORIZONTAL_FACTOR = 0.22f
private const val VERTICAL_SPACER_WIDTH_FACTOR = 1.2f
private const val VERTICAL_MIN_CHARS = 12
private const val VERTICAL_MAX_CHARS = 42
private const val RUBY_FONT_SCALE = 0.5f
private const val RUBY_LINE_HEIGHT_SCALE = 0.52f
private const val EMPHASIS_FONT_SCALE = 0.45f

internal sealed interface NovelVerticalToken {
    data class Char(
        val char: kotlin.Char,
    ) : NovelVerticalToken

    data class Ruby(
        val base: String,
        val ruby: String,
    ) : NovelVerticalToken

    data class Emphasis(
        val char: kotlin.Char,
        val mark: String,
    ) : NovelVerticalToken
}

internal object NovelVerticalEngine {
    private val PUNCTUATION_MAP =
        mapOf(
            'ー' to '丨',
            '―' to '丨',
            '—' to '丨',
            '-' to '丨',
            '(' to '︵',
            '）' to '︶',
            ')' to '︶',
            '（' to '︵',
            '[' to '﹇',
            '］' to '﹈',
            ']' to '﹈',
            '［' to '﹇',
            '{' to '︷',
            '｝' to '︸',
            '}' to '︸',
            '｛' to '︷',
            '【' to '︻',
            '】' to '︼',
            '〔' to '︹',
            '〕' to '︺',
            '〈' to '︿',
            '〉' to '﹀',
            '《' to '︽',
            '》' to '︾',
            '「' to '﹁',
            '」' to '﹂',
            '『' to '﹃',
            '』' to '﹄',
            '…' to '︙',
            '‥' to '︰',
            '～' to '︴',
            '~' to '︴',
            '=' to '〢',
            '＝' to '〢',
        )

    private val KINSOKU_LINE_START =
        setOf(
            '、',
            '。',
            '，',
            '．',
            '！',
            '？',
            '!',
            '?',
            '︶',
            '﹂',
            '﹄',
            '︼',
            '︺',
            '﹀',
            '︾',
            '﹈',
            '︙',
            '︰',
            '丨',
            '々',
            'ー',
            'ぁ',
            'ぃ',
            'ぅ',
            'ぇ',
            'ぉ',
            'っ',
            'ゃ',
            'ゅ',
            'ょ',
            'ゎ',
            'ァ',
            'ィ',
            'ゥ',
            'ェ',
            'ォ',
            'ッ',
            'ャ',
            'ュ',
            'ョ',
            'ヮ',
        )

    fun convertPunctuation(char: Char): Char = PUNCTUATION_MAP[char] ?: char

    fun breakIntoColumns(
        paragraph: NovelParagraphBlock,
        maxChars: Int,
    ): List<List<NovelVerticalToken>> {
        val effectiveMaxChars = maxChars.coerceIn(VERTICAL_MIN_CHARS, VERTICAL_MAX_CHARS)
        val tokens = mutableListOf<NovelVerticalToken>()
        paragraph.items.forEach { item ->
            appendTokens(item, tokens)
        }
        if (tokens.isEmpty()) return emptyList()

        val columns = mutableListOf<List<NovelVerticalToken>>()
        var currentColumn = mutableListOf<NovelVerticalToken>()
        var currentWeight = 0

        for (token in tokens) {
            val weight = tokenWeight(token)
            val isForbidden = isLineStartForbidden(token)

            if (currentWeight + weight > effectiveMaxChars && currentColumn.isNotEmpty()) {
                if (isForbidden && currentWeight <= effectiveMaxChars + 1) {
                    currentColumn.add(token)
                    columns.add(currentColumn)
                    currentColumn = mutableListOf()
                    currentWeight = 0
                    continue
                }
                columns.add(currentColumn)
                currentColumn = mutableListOf()
                currentWeight = 0
            }
            currentColumn.add(token)
            currentWeight += weight
        }
        if (currentColumn.isNotEmpty()) {
            columns.add(currentColumn)
        }
        return columns
    }

    private fun tokenWeight(token: NovelVerticalToken): Int =
        when (token) {
            is NovelVerticalToken.Ruby -> token.base.length.coerceAtLeast(1)
            else -> 1
        }

    private fun isLineStartForbidden(token: NovelVerticalToken): Boolean =
        when (token) {
            is NovelVerticalToken.Char -> token.char in KINSOKU_LINE_START
            else -> false
        }

    private fun appendTokens(
        item: NovelInlineItem,
        tokens: MutableList<NovelVerticalToken>,
    ) {
        when (item) {
            is NovelInlineItem.Text -> {
                item.content.forEach { ch ->
                    tokens += NovelVerticalToken.Char(convertPunctuation(ch))
                }
            }

            is NovelInlineItem.Ruby -> {
                tokens += NovelVerticalToken.Ruby(item.base, item.ruby)
            }

            is NovelInlineItem.Emphasis -> {
                item.base.forEach { ch ->
                    tokens += NovelVerticalToken.Emphasis(convertPunctuation(ch), item.mark.ifBlank { "﹅" })
                }
            }

            is NovelInlineItem.Bold -> {
                item.text.forEach { ch ->
                    tokens += NovelVerticalToken.Char(convertPunctuation(ch))
                }
            }

            is NovelInlineItem.Italic -> {
                item.text.forEach { ch ->
                    tokens += NovelVerticalToken.Char(convertPunctuation(ch))
                }
            }

            is NovelInlineItem.Link -> {
                item.title.forEach { ch ->
                    tokens += NovelVerticalToken.Char(convertPunctuation(ch))
                }
            }
        }
    }
}

@Composable
internal fun NovelReaderVerticalPage(
    page: NovelPage,
    pageIndex: Int,
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
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleControls,
                ).padding(contentPadding),
    ) {
        val availableHeight = (maxHeight - 24.dp).coerceAtLeast(100.dp)
        val charHeight = (fontSize.coerceAtLeast(8f) * lineHeightMultiplier.coerceAtLeast(0.5f)).dp
        val maxChars = (availableHeight / charHeight).toInt().coerceIn(VERTICAL_MIN_CHARS, VERTICAL_MAX_CHARS)

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy((fontSize * VERTICAL_COLUMN_SPACING_FACTOR).dp),
            ) {
                page.blocks.forEach { block ->
                    renderVerticalBlock(
                        block = block,
                        maxChars = maxChars,
                        fontSize = fontSize,
                        textColor = textColor,
                        fontFamily = fontFamily,
                        viewModel = viewModel,
                        onJumpPage = onJumpPage,
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 12.dp),
        ) {
            NovelMetaPill(text = "${pageIndex + 1} / $pageCount")
        }
    }
}

private fun LazyListScope.renderVerticalBlock(
    block: NovelBlock,
    maxChars: Int,
    fontSize: Float,
    textColor: Color,
    fontFamily: FontFamily,
    viewModel: IllustiaViewModel,
    onJumpPage: (Int) -> Unit,
) {
    when (block) {
        NovelSpacerBlock -> {
            item {
                Spacer(modifier = Modifier.width((fontSize * VERTICAL_SPACER_WIDTH_FACTOR).dp))
            }
        }

        is NovelChapterBlock -> {
            item {
                NovelVerticalChapterItem(title = block.title, textColor = textColor, fontFamily = fontFamily)
            }
        }

        is NovelPixivImageBlock -> {
            item {
                NovelVerticalArtworkCard(
                    illustId = block.illustId,
                    textColor = textColor,
                    viewModel = viewModel,
                    onOpen = { viewModel.openIllust(block.illustId) },
                )
            }
        }

        is NovelJumpBlock -> {
            item {
                NovelJumpButton(pageNumber = block.pageNumber, onJumpPage = onJumpPage)
            }
        }

        is NovelParagraphBlock -> {
            val columns = NovelVerticalEngine.breakIntoColumns(block, maxChars)
            items(columns) { columnTokens ->
                NovelVerticalColumn(
                    tokens = columnTokens,
                    fontSize = fontSize,
                    textColor = textColor,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

@Composable
private fun NovelVerticalChapterItem(
    title: String,
    textColor: Color,
    fontFamily: FontFamily,
) {
    Row(
        modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.85f)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.6f)),
        )
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            title.forEach { ch ->
                Text(
                    text = NovelVerticalEngine.convertPunctuation(ch).toString(),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontFamily = fontFamily,
                )
            }
        }
    }
}

@Composable
private fun NovelVerticalArtworkCard(
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
                .width(260.dp)
                .fillMaxHeight(0.9f)
                .padding(vertical = 8.dp)
                .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                val currentPreviewUrl = previewUrl
                if (currentPreviewUrl != null) {
                    PixivImage(
                        url = currentPreviewUrl,
                        contentDescription = stringResource(R.string.novel_inline_illust_label),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = MiuixIcons.Photos,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.novel_open_illust),
                        style = MiuixTheme.textStyles.subtitle,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
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
private fun NovelVerticalColumn(
    tokens: List<NovelVerticalToken>,
    fontSize: Float,
    textColor: Color,
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxHeight().padding(horizontal = (fontSize * VERTICAL_PADDING_HORIZONTAL_FACTOR).dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tokens.forEach { token ->
            when (token) {
                is NovelVerticalToken.Char -> {
                    Text(
                        text = token.char.toString(),
                        fontSize = fontSize.sp,
                        lineHeight = fontSize.sp,
                        fontFamily = fontFamily,
                        color = textColor,
                    )
                }

                is NovelVerticalToken.Ruby -> {
                    NovelVerticalRubyToken(token = token, fontSize = fontSize, textColor = textColor, fontFamily = fontFamily)
                }

                is NovelVerticalToken.Emphasis -> {
                    NovelVerticalEmphasisToken(token = token, fontSize = fontSize, textColor = textColor, fontFamily = fontFamily)
                }
            }
        }
    }
}

@Composable
private fun NovelVerticalRubyToken(
    token: NovelVerticalToken.Ruby,
    fontSize: Float,
    textColor: Color,
    fontFamily: FontFamily,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            token.ruby.forEach { rChar ->
                Text(
                    text = NovelVerticalEngine.convertPunctuation(rChar).toString(),
                    fontSize = (fontSize * RUBY_FONT_SCALE).sp,
                    lineHeight = (fontSize * RUBY_LINE_HEIGHT_SCALE).sp,
                    fontFamily = fontFamily,
                    color = textColor.copy(alpha = 0.85f),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            token.base.forEach { bChar ->
                Text(
                    text = NovelVerticalEngine.convertPunctuation(bChar).toString(),
                    fontSize = fontSize.sp,
                    lineHeight = fontSize.sp,
                    fontFamily = fontFamily,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun NovelVerticalEmphasisToken(
    token: NovelVerticalToken.Emphasis,
    fontSize: Float,
    textColor: Color,
    fontFamily: FontFamily,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = token.mark,
            fontSize = (fontSize * EMPHASIS_FONT_SCALE).sp,
            lineHeight = (fontSize * RUBY_FONT_SCALE).sp,
            fontFamily = fontFamily,
            color = textColor.copy(alpha = 0.85f),
        )
        Text(
            text = token.char.toString(),
            fontSize = fontSize.sp,
            lineHeight = fontSize.sp,
            fontFamily = fontFamily,
            color = textColor,
            fontWeight = FontWeight.Bold,
        )
    }
}
