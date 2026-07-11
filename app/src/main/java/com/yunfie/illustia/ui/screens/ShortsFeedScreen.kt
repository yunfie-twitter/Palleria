package com.yunfie.illustia.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.components.PixivImage
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Share

@Composable
fun ShortsFeedScreen(
    items: List<Illust>,
    currentIllustId: Long?,
    viewModel: IllustiaViewModel,
    onOpenComments: (Long) -> Unit,
) {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.action_share)
    val initialPage = remember(items, currentIllustId) {
        items.indexOfFirst { it.id == currentIllustId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size },
    )

    LaunchedEffect(pagerState.currentPage, items) {
        items.getOrNull(pagerState.currentPage)?.let {
            viewModel.updateShortsFeedCurrentIllust(it.id)
        }
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        if (items.isNotEmpty() && pagerState.currentPage >= items.lastIndex - 2) {
            viewModel.loadMoreShortsFeed()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            Text(
                text = "イラストを読み込んでいます…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val illust = items[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .combinedClickable(
                            onClick = { viewModel.openIllust(illust) },
                            onLongClick = { viewModel.onIllustLongPress(illust) },
                        ),
                ) {
                    PixivImage(
                        url = illust.imageUrl.ifBlank { illust.previewUrl },
                        contentDescription = illust.title,
                        contentScale = ContentScale.Fit,
                        crossfade = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                                ),
                            )
                            .padding(start = 20.dp, end = 20.dp, top = 72.dp, bottom = 24.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = illust.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "@${illust.artistName}",
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 10.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleBookmark(illust) },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = if (illust.isBookmarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                contentDescription = stringResource(R.string.action_bookmark),
                                tint = if (illust.isBookmarked) Color(0xFFFF4D67) else Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        IconButton(
                            onClick = { onOpenComments(illust.id) },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Messages,
                                contentDescription = stringResource(R.string.detail_comments),
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "https://www.pixiv.net/artworks/${illust.id}")
                                }
                                context.startActivity(Intent.createChooser(intent, shareLabel))
                            },
                            modifier = Modifier.size(64.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Share,
                                contentDescription = shareLabel,
                                tint = Color.White,
                                modifier = Modifier.size(38.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
