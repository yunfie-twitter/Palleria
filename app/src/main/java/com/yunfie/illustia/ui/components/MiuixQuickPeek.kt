package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserPreview
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix-styled long-press preview for an artwork.
 *
 * Uses [OverlayBottomSheet] so it follows the Miuix guideline shape: hero artwork, a title block,
 * then full-width action buttons with the standard overlay button colours. This replaces the
 * free-floating centred dialog used by the legacy [QuickPeekOverlay].
 */
@Composable
fun MiuixArtworkQuickPeek(
    illust: Illust,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onBookmark: () -> Unit,
    onCopyImage: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = illust.title.ifBlank { stringResource(R.string.dialog_work_options) },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                insideMargin = PaddingValues(0.dp),
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    PixivImage(
                        url = illust.imageUrl,
                        contentDescription = illust.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = illust.title.ifBlank { stringResource(R.string.dialog_work_options) },
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.dialog_artist_label, illust.artistName),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onOpenDetail,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.dialog_show_detail))
            }
            Button(
                onClick = onBookmark,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (illust.isBookmarked) {
                                R.string.action_remove_bookmark
                            } else {
                                R.string.action_bookmark
                            },
                        ),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onCopyImage,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.action_copy_image))
            }
        }
    }
}

/**
 * Miuix-styled long-press preview for a user.
 *
 * Mirrors [MiuixArtworkQuickPeek]: avatar and name in the body, then follow and profile actions.
 */
@Composable
fun MiuixUserQuickPeek(
    user: UserPreview,
    onDismiss: () -> Unit,
    onOpenProfile: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = user.name.ifBlank { "@${user.account}" },
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarImage(
                    url = user.profileImageUrl,
                    name = user.name,
                    size = 64.dp,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = user.name.ifBlank { "@${user.account}" },
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${user.account}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (user.previewIllusts.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.quick_peek_illust_count, user.previewIllusts.size),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                user.previewIllusts.firstOrNull()?.let { illust ->
                    PixivImage(
                        url = illust.squareImageUrl.ifBlank { illust.imageUrl },
                        contentDescription = illust.title,
                        contentScale = ContentScale.Crop,
                        thumbnail = true,
                        maxDecodeDimensionPx = 384,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    )
                }
            }

            if (user.comment.isNotBlank()) {
                Text(
                    text = user.comment,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.quick_peek_show_profile))
            }
            Button(
                onClick = onToggleFollow,
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(
                    text =
                        stringResource(
                            if (user.isFollowed) {
                                R.string.quick_peek_unfollow
                            } else {
                                R.string.quick_peek_follow
                            },
                        ),
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}
