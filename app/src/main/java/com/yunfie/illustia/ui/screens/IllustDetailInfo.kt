package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.ui.components.ArtworkCardPreferences
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.LocalArtworkCardPreferences
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.adaptiveRelatedIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun IllustDetailInfo(
    illust: Illust,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    onOpenUser: () -> Unit,
    onOpenUserById: (Long) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onSearchTag: (String) -> Unit,
    onLongPressTag: (String) -> Unit,
    sectionOrder: List<String>,
    relatedContent: @Composable (isNearViewport: Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    val relatedSectionTriggerPx = with(LocalDensity.current) { screenHeightDp.dp.toPx() * 1.05f }
    val relatedSectionReady = remember(illust.id) { mutableStateOf(false) }
    var followAnimationTrigger by remember(illust.artistId) { mutableIntStateOf(0) }
    // Cached so the link handler survives recomposition; otherwise the whole caption subtree is invalidated.
    val currentOnOpenIllustById by rememberUpdatedState(onOpenIllustById)
    val currentOnOpenUserById by rememberUpdatedState(onOpenUserById)
    val currentOnSearchTag by rememberUpdatedState(onSearchTag)
    val customUriHandler =
        remember(uriHandler) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val event = NativeIntentRouter.parseText(uri)
                    when (event) {
                        is NativeIntentEvent.Artwork -> {
                            currentOnOpenIllustById(event.id)
                        }

                        is NativeIntentEvent.User -> {
                            currentOnOpenUserById(event.id)
                        }

                        is NativeIntentEvent.Tag -> {
                            currentOnSearchTag(event.tag)
                        }

                        else -> {
                            try {
                                uriHandler.openUri(uri)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            }
        }
    val orderedSections =
        remember(sectionOrder) {
            val defaults = listOf("artist", "tags", "description", "related")
            sectionOrder.filter { it in defaults }.distinct() + defaults.filterNot { it in sectionOrder }
        }
    val displayTags =
        remember(illust.tags) {
            if (illust.tags.size <= 12) illust.tags else illust.tags.take(12)
        }
    // HTML キャプションの解析は1回だけ行い、他の設定変更による再コンポーズでは再解析しない。
    val captionKey = illust.caption
    val parsedCaptions = remember(captionKey) { mutableStateMapOf<String, AnnotatedString>() }
    LaunchedEffect(captionKey) {
        if (captionKey.isNotBlank() && parsedCaptions[captionKey] == null) {
            val parsed =
                withContext(Dispatchers.Default) {
                    try {
                        AnnotatedString.fromHtml(captionKey)
                    } catch (_: Exception) {
                        AnnotatedString(captionKey)
                    }
                }
            parsedCaptions[captionKey] = parsed
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MiuixTheme.colorScheme.surface)
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                illust.title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Black,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ID ${illust.id}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.Bold,
                    style = MiuixTheme.textStyles.footnote1,
                )
                if (illust.series != null && onOpenSeries != null) {
                    Text(
                        text = illust.series.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_series),
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier =
                            Modifier
                                .weight(1f)
                                .miuixClickable(
                                    pressedScale = 0.96f,
                                    haptic = true,
                                    onClick = onOpenSeries,
                                ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        illust.type,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                if (illust.pageCount > 1) {
                    Text(
                        "${illust.pageCount}P",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
            Text(
                text =
                    stringResource(R.string.detail_illust_id) + " ${illust.id}    " + stringResource(R.string.detail_resolution) +
                        "  ${if (illust.originalImageUrl != null) "original" else "large"}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            orderedSections.forEach { section ->
                key(section) {
                    when (section) {
                        "artist" -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .miuixClickable(onClick = onOpenUser)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                AvatarImage(
                                    url = illust.artistAvatarUrl,
                                    name = illust.artistName,
                                    size = 44.dp,
                                )
                                Text(
                                    text = illust.artistName,
                                    color = MiuixTheme.colorScheme.onBackground,
                                    style = MiuixTheme.textStyles.main,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier =
                                        Modifier
                                            .clip(
                                                androidx.compose.foundation.shape
                                                    .RoundedCornerShape(20.dp),
                                            ).miuixClickable(
                                                pressedScale = 0.94f,
                                                haptic = true,
                                                onClick =
                                                    if (isArtistMuted) {
                                                        onUnmuteUser
                                                    } else {
                                                        {
                                                            if (!isArtistFollowed) {
                                                                followAnimationTrigger += 1
                                                            }
                                                            onToggleFollow()
                                                        }
                                                    },
                                            ),
                                ) {
                                    if (isArtistMuted) {
                                        DetailMutedUserPill()
                                    } else {
                                        FollowPill(
                                            isFollowed = isArtistFollowed,
                                            followAnimationTrigger = followAnimationTrigger,
                                        )
                                    }
                                }
                            }
                        }

                        "tags" -> {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                FlowButtons(
                                    values = displayTags,
                                    label = { "#$it" },
                                    onClick = onSearchTag,
                                    onLongClick = onLongPressTag,
                                )
                            }
                        }

                        "description" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
                                    ElevatedPanel(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        contentPadding = PaddingValues(16.dp),
                                    ) {
                                        val annotatedCaption = parsedCaptions[captionKey]
                                        SelectionContainer {
                                            if (illust.caption.isBlank()) {
                                                Text(
                                                    text = stringResource(R.string.detail_no_caption),
                                                    color = MiuixTheme.colorScheme.onBackground,
                                                    style = MiuixTheme.textStyles.body1,
                                                    lineHeight = 23.sp,
                                                )
                                            } else {
                                                annotatedCaption?.let { caption ->
                                                    Text(
                                                        text = caption,
                                                        color = MiuixTheme.colorScheme.onBackground,
                                                        style = MiuixTheme.textStyles.body1,
                                                        lineHeight = 23.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                CommentEntryButton(
                                    onClick = onOpenComments,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }

                        "related" -> {
                            Column(
                                modifier =
                                    Modifier.onGloballyPositioned { coordinates ->
                                        if (
                                            !relatedSectionReady.value &&
                                            coordinates.boundsInWindow().top <= relatedSectionTriggerPx
                                        ) {
                                            relatedSectionReady.value = true
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.detail_related),
                                    color = MiuixTheme.colorScheme.onBackground,
                                    style = MiuixTheme.textStyles.title4,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                relatedContent(relatedSectionReady.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsLabel = stringResource(R.string.detail_show_comments)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(14.dp),
                        ).background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Messages,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = commentsLabel,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun DetailMutedUserPill(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(24.dp),
                ).background(MiuixTheme.colorScheme.error)
                .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_unmute),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

/**
 * One row of the related-artwork grid: up to [columns] square cards sharing the full width.
 *
 * Kept separate from [RelatedIllustsList] so a single row can be composed on its own once it comes
 * near the viewport instead of building every card up front.
 */
@Composable
internal fun RelatedIllustRow(
    rowItems: List<Illust>,
    columns: Int,
    cardPreferences: ArtworkCardPreferences,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rowItems.forEach { related ->
            Box(modifier = Modifier.weight(1f)) {
                RelatedIllustCard(
                    related = related,
                    showR18Badge = cardPreferences.showR18Badge,
                    showRelatedR18 = cardPreferences.showRelatedR18,
                    onOpenIllust = onOpenIllust,
                    onLongPressIllust = onLongPressIllust,
                )
            }
        }
        repeat(columns - rowItems.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Non-lazy variant kept for hosts that cannot emit lazy items (for example the two-pane header).
 * Prefer [RelatedIllustRow] inside the paged detail list so off-screen rows stay uncomposed.
 */
@Composable
internal fun RelatedIllustsList(
    relatedIllusts: List<Illust>,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier,
    configuredColumns: Int = 3,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
    val columns = adaptiveRelatedIllustColumns(configuredColumns)
    val relatedRows = remember(relatedIllusts, columns) { relatedIllustRows(relatedIllusts, columns) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        relatedRows.forEachIndexed { index, rowItems ->
            DeferredRelatedIllustRow(
                // The first rows are visible as soon as the section appears; later rows wait until
                // they near the viewport so the whole grid never composes and decodes at once.
                initiallyVisible = index < RELATED_ROWS_COMPOSED_IMMEDIATELY,
                rowItems = rowItems,
                columns = columns,
                cardPreferences = cardPreferences,
                onOpenIllust = onOpenIllust,
                onLongPressIllust = onLongPressIllust,
            )
        }
    }
}

/**
 * Renders one row only once it comes close to the viewport.
 *
 * The placeholder keeps the row's square height so promoting it does not move the content below.
 * [columns] is always >= 1 here because [adaptiveRelatedIllustColumns] never returns less.
 */
@Composable
private fun DeferredRelatedIllustRow(
    initiallyVisible: Boolean,
    rowItems: List<Illust>,
    columns: Int,
    cardPreferences: ArtworkCardPreferences,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
) {
    // Keyed on the row's identities so a re-emitted-but-equal list keeps the promoted state, and a
    // genuinely different row starts hidden again.
    val rowKey = remember(rowItems) { rowItems.map { it.id } }
    var visible by remember(rowKey, initiallyVisible) { mutableStateOf(initiallyVisible) }
    if (visible) {
        RelatedIllustRow(
            rowItems = rowItems,
            columns = columns,
            cardPreferences = cardPreferences,
            onOpenIllust = onOpenIllust,
            onLongPressIllust = onLongPressIllust,
        )
        return
    }
    // A row of `columns` square cards spans the full width, so its height is width / columns.
    // The placeholder matches that so promoting the row does not shift the content below it.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val rowHeight = configuration.screenWidthDp.dp / columns
    val triggerPx = with(density) { configuration.screenHeightDp.dp.toPx() } * 1.05f
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(rowHeight),
    ) {
        // onGloballyPositioned fires for the placeholder once it is measured; comparing its window
        // position against a screen-height threshold promotes it exactly like the section gate does.
        Spacer(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.boundsInWindow().top <= triggerPx) {
                            visible = true
                        }
                    },
        )
    }
}

/** Rows of the related grid that are composed as soon as the section appears. */
private const val RELATED_ROWS_COMPOSED_IMMEDIATELY = 2

/** Splits the related list into grid rows for the given column count. */
internal fun <T> relatedIllustRows(
    relatedIllusts: List<T>,
    columns: Int,
): List<List<T>> = if (columns <= 0) emptyList() else relatedIllusts.chunked(columns)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RelatedIllustCard(
    related: Illust,
    showR18Badge: Boolean,
    showRelatedR18: Boolean,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
) {
    val ageRestrictionBadgeText =
        remember(related.id, related.isR18, related.isR18G, showR18Badge, showRelatedR18) {
            if (showR18Badge && showRelatedR18) {
                related.ageRestrictionBadgeText
            } else {
                null
            }
        }
    val onClick = remember(related.id) { { onOpenIllust(related) } }
    val onLongClick = remember(related.id) { { onLongPressIllust(related) } }
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        PixivImage(
            url = related.squareImageUrl.ifBlank { related.mediumImageUrl.ifBlank { related.imageUrl } },
            contentDescription = related.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            thumbnail = true,
            maxDecodeDimensionPx = 384,
        )
        if (ageRestrictionBadgeText != null) {
            Text(
                text = ageRestrictionBadgeText,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .squircleBackground(Color(0xFFFA383E), 6.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (related.pageCount > 1) {
            Text(
                text = "${related.pageCount}P",
                color = Color.White,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .squircleBackground(Color.Black.copy(alpha = 0.4f), 6.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
