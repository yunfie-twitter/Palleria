package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.UserPreview
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** ユーザー検索・関連ユーザー一覧の行。 */
@Composable
fun UserResultCard(
    user: UserPreview,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (user.previewIllusts.isNotEmpty()) {
                val previewCount = minOf(3, user.previewIllusts.size)
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (i in 0 until previewCount) {
                        val illust = user.previewIllusts[i]
                        PixivImage(
                            url = illust.squareImageUrl.ifBlank { illust.mediumImageUrl.ifBlank { illust.imageUrl } },
                            contentDescription = illust.title,
                            contentScale = ContentScale.Crop,
                            thumbnail = true,
                            maxDecodeDimensionPx = 384,
                            modifier = Modifier.weight(1f).height(118.dp),
                        )
                    }
                    repeat(3 - previewCount) {
                        Spacer(modifier = Modifier.weight(1f).height(118.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvatarImage(url = user.profileImageUrl, name = user.name, size = 62.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        user.name,
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${user.account}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FollowPill(isFollowed = user.isFollowed)
            }
        }
    }
}
