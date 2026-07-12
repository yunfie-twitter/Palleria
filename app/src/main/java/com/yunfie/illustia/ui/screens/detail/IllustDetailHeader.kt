package com.yunfie.illustia.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.pow

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IllustDetailHeader(
    illust: Illust,
    highQualityImages: Boolean,
    detailQuality: String,
    prefetchImages: Boolean,
    confirmOnLongPressSave: Boolean,
    skipConfirmOnDetailSave: Boolean,
    pixivUrl: String,
    onBack: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onDoubleTapImage: () -> Unit,
    onSaveImage: (String, String, Boolean) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    showImage: Boolean,
    maskMutedArtwork: Boolean,
    onRevealMutedArtwork: () -> Unit,
    mutedArtworkTitle: String,
    mutedArtworkSummary: String,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val openInBrowserLabel = stringResource(R.string.detail_open_in_browser)
    val shareLabel = stringResource(R.string.detail_share)
    val saveImageLabel = stringResource(R.string.detail_save_image)
    val saveAllPagesLabel = stringResource(R.string.detail_save_all_pages)
    val copyUrlLabel = stringResource(R.string.detail_copy_url)
    val muteWorkLabel = stringResource(R.string.detail_mute_work)
    val muteArtistLabel = stringResource(R.string.detail_mute_artist)
    val moreLabel = stringResource(R.string.detail_more)
    val browserFailedMessage = stringResource(R.string.error_browser_failed)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val urlCopiedMessage = stringResource(R.string.msg_url_copied)
    var useDarkHeaderIcons by remember(illust.id) { mutableStateOf(false) }
    val previewUrl: String = remember(illust.id, highQualityImages, detailQuality) {
        when {
            !highQualityImages || detailQuality == "low" -> illust.mediumImagePages.firstOrNull()
                ?: detailFallbackImageUrl(illust.mediumImageUrl, illust.squareImageUrl, illust.imageUrl)
            detailQuality == "medium" -> illust.imagePages.firstOrNull()
                ?: detailFallbackImageUrl(illust.imageUrl, illust.mediumImageUrl, illust.squareImageUrl)
            else -> illust.originalImagePages.firstOrNull()
                ?: illust.imagePages.firstOrNull()
                ?: detailFallbackImageUrl(
                    illust.imageUrl,
                    illust.mediumImageUrl,
                    illust.squareImageUrl,
                    illust.originalImageUrl,
                )
        }
    } ?: ""
    val imageUrls = remember(illust.id, highQualityImages, detailQuality) {
        when {
            !highQualityImages || detailQuality == "low" -> illust.mediumImagePages.ifEmpty {
                listOfNotNull(
                    detailFallbackImageUrl(illust.mediumImageUrl, illust.squareImageUrl, illust.imageUrl),
                )
            }
            detailQuality == "medium" -> illust.imagePages.ifEmpty {
                listOfNotNull(detailFallbackImageUrl(illust.imageUrl, illust.mediumImageUrl, illust.squareImageUrl))
            }
            else -> illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty {
                    listOfNotNull(
                        detailFallbackImageUrl(
                            illust.imageUrl,
                            illust.mediumImageUrl,
                            illust.squareImageUrl,
                            illust.originalImageUrl,
                        ),
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 320.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (showImage) {
            if (illust.type == "ugoira") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                ) {
                    UgoiraArtwork(
                        previewUrl = previewUrl,
                        contentDescription = illust.title,
                        loadPlayback = { loadUgoiraPlayback(illust.id) },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (maskMutedArtwork) Modifier.blur(18.dp) else Modifier),
                    )
                    if (maskMutedArtwork) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.38f)),
                        )
                    }
                }
            } else {
                val pagerState = rememberPagerState(pageCount = { imageUrls.size })

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp),
                    beyondViewportPageCount = if (prefetchImages) 1 else 0,
                    key = { it },
                ) { page ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        PixivImage(
                            url = imageUrls[page],
                            contentDescription = illust.title,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (maskMutedArtwork) Modifier.blur(18.dp) else Modifier)
                                .combinedClickable(
                                    enabled = !maskMutedArtwork,
                                    onClick = { onOpenImage(page) },
                                    onDoubleClick = onDoubleTapImage,
                                    onLongClick = {
                                        performAppHapticFeedback(context, haptic, hapticMode)
                                        onSaveImage(imageUrls[page], "illustia_${illust.id}_p$page", confirmOnLongPressSave)
                                    },
                                ),
                            crossfade = true,
                            onSuccess = { bitmap ->
                                if (page == pagerState.currentPage) {
                                    useDarkHeaderIcons = shouldUseDarkHeaderIcons(bitmap)
                                }
                            },
                        )
                        if (maskMutedArtwork) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.38f)),
                            )
                        }
                    }
                }

                if (maskMutedArtwork) {
                    MutedArtworkOverlay(
                        title = mutedArtworkTitle,
                        summary = mutedArtworkSummary,
                        onReveal = onRevealMutedArtwork,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                if (imageUrls.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text("${pagerState.currentPage + 1} / ${imageUrls.size}", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val headerIconBackground = if (useDarkHeaderIcons) {
                Color.White.copy(alpha = 0.58f)
            } else {
                Color.Black.copy(alpha = 0.35f)
            }
            val headerIconTint = if (useDarkHeaderIcons) {
                Color.Black.copy(alpha = 0.92f)
            } else {
                Color.White.copy(alpha = 0.92f)
            }
            HeaderOverlayIcon(
                MiuixIcons.Back,
                onBack,
                backgroundColor = headerIconBackground,
                contentColor = headerIconTint,
            )
            WindowIconDropdownMenu(
                entries = listOf(
                    DropdownEntry(
                        items = listOfNotNull(
                            DropdownItem(
                                text = openInBrowserLabel,
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pixivUrl)))
                                    }.onFailure { onMessage(browserFailedMessage) }
                                },
                            ),
                            DropdownItem(
                                text = shareLabel,
                                onClick = {
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${illust.title} by ${illust.artistName}\n$pixivUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, shareLabel))
                                    }.onFailure { onMessage(shareFailedMessage) }
                                },
                            ),
                            DropdownItem(
                                text = saveImageLabel,
                                onClick = {
                                    onSaveImage(illust.originalImageUrl ?: illust.imageUrl, "illustia_${illust.id}", !skipConfirmOnDetailSave)
                                },
                            ),
                            if (imageUrls.size > 1) {
                                DropdownItem(
                                    text = saveAllPagesLabel,
                                    onClick = { onSaveAllImages(imageUrls, "illustia_${illust.id}") },
                                )
                            } else {
                                null
                            },
                            DropdownItem(
                                text = copyUrlLabel,
                                onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Pixiv URL", pixivUrl))
                                    onMessage(urlCopiedMessage)
                                },
                            ),
                        ),
                    ),
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = muteWorkLabel,
                                onClick = {
                                    onMuteIllust()
                                    onBack()
                                },
                            ),
                            DropdownItem(
                                text = muteArtistLabel,
                                onClick = {
                                    onMuteUser()
                                    onBack()
                                },
                            ),
                        ),
                    ),
                ),
                collapseOnSelection = true,
                backgroundColor = headerIconBackground,
                cornerRadius = 19.dp,
                minWidth = 38.dp,
                minHeight = 38.dp,
            ) {
                Icon(MiuixIcons.More, contentDescription = moreLabel, tint = headerIconTint, modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun detailFallbackImageUrl(vararg candidates: String?): String? {
    return candidates.firstOrNull { !it.isNullOrBlank() }
}

@Composable
internal fun MutedArtworkOverlay(
    title: String,
    summary: String,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .padding(24.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = summary,
            color = Color.White.copy(alpha = 0.82f),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
        )
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onReveal,
            colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
            ),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.action_show), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

private fun shouldUseDarkHeaderIcons(bitmap: Bitmap): Boolean {
    if (bitmap.width <= 0 || bitmap.height <= 0) return false

    val edgeInsetX = (bitmap.width / 6).coerceAtLeast(1)
    val edgeInsetY = (bitmap.height / 6).coerceAtLeast(1)
    val stepX = (bitmap.width / 32).coerceAtLeast(1)
    val stepY = (bitmap.height / 32).coerceAtLeast(1)

    var totalLuminance = 0.0
    var sampleCount = 0

    for (y in 0 until bitmap.height step stepY) {
        val isEdgeRow = y < edgeInsetY || y >= bitmap.height - edgeInsetY
        for (x in 0 until bitmap.width step stepX) {
            val isEdgeColumn = x < edgeInsetX || x >= bitmap.width - edgeInsetX
            if (!isEdgeRow && !isEdgeColumn) continue

            val pixel = bitmap.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 16) continue

            totalLuminance += relativeLuminance(pixel)
            sampleCount++
        }
    }

    if (sampleCount == 0) {
        val fallbackStepX = (bitmap.width / 40).coerceAtLeast(1)
        val fallbackStepY = (bitmap.height / 40).coerceAtLeast(1)
        for (y in 0 until bitmap.height step fallbackStepY) {
            for (x in 0 until bitmap.width step fallbackStepX) {
                val pixel = bitmap.getPixel(x, y)
                if (AndroidColor.alpha(pixel) < 16) continue

                totalLuminance += relativeLuminance(pixel)
                sampleCount++
            }
        }
    }

    if (sampleCount == 0) return false

    val averageLuminance = totalLuminance / sampleCount
    return averageLuminance >= 0.58
}

private fun relativeLuminance(color: Int): Double {
    fun linearize(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.03928) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    return 0.2126 * linearize(AndroidColor.red(color)) +
        0.7152 * linearize(AndroidColor.green(color)) +
        0.0722 * linearize(AndroidColor.blue(color))
}
