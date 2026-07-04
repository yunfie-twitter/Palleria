package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UgoiraArtwork(
    previewUrl: String,
    contentDescription: String,
    loadPlayback: suspend () -> UgoiraPlayback,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reloadKey by remember { mutableIntStateOf(0) }
    val playbackResult by produceState<Result<UgoiraPlayback>?>(initialValue = null, reloadKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadPlayback() }
        }
    }
    val playback = playbackResult?.getOrNull()
    var currentFrameIndex by remember(playback, reloadKey) { mutableIntStateOf(0) }

    LaunchedEffect(playback) {
        val nextPlayback = playback ?: return@LaunchedEffect
        if (nextPlayback.frames.isEmpty()) return@LaunchedEffect
        currentFrameIndex = 0
        while (isActive) {
            val frame = nextPlayback.frames[currentFrameIndex % nextPlayback.frames.size]
            delay(frame.delayMillis.toLong().coerceAtLeast(20L))
            currentFrameIndex = (currentFrameIndex + 1) % nextPlayback.frames.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PixivImage(
            url = previewUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxSize(),
        )

        when {
            playbackResult == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            playback != null -> {
                val currentFrame = playback.frames[currentFrameIndex % playback.frames.size]
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentFrame.filePath)
                        .crossfade(false)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "UGOIRA ${currentFrameIndex + 1}/${playback.frames.size}",
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }

            playbackResult?.isFailure == true -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Ugoira load failed",
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }
}
