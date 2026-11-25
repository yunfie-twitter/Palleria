package com.yunfie.illustia.ui.components

import android.content.Context
import android.net.ConnectivityManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.yunfie.illustia.platform.PlatformCapabilities
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun Context.isActiveNetworkMetered(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    return runCatching {
        connectivityManager?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)
}

@Composable
fun PredictiveBackGestureHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    if (PlatformCapabilities.supportsPredictiveBack()) {
        return
    }

    BackHandler(enabled = enabled) {
        onBack()
    }
}

@Composable
fun AvatarImage(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    maxDecodeDimensionPx: Int = AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX,
) {
    val commonModifier =
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)

    if (url != null) {
        PixivImage(
            url = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = commonModifier,
            thumbnail = true,
            maxDecodeDimensionPx = maxDecodeDimensionPx,
        )
    } else {
        Box(
            modifier = commonModifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "no image",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
