package com.yunfie.illustia.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.settings.AppSettings
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.utils.SinkFeedback
import top.yukonga.miuix.kmp.utils.pressable

const val MotionFast = 180
const val MotionMedium = 260
const val MotionSlow = 340

val MainNavigationContentPadding = 152.dp
val BottomSheetInsideMargin = DpSize(width = 24.dp, height = 12.dp)

val LocalPixivImageProxyBaseUrl = compositionLocalOf { "" }
val LocalPreferLowDataImages = compositionLocalOf { false }
val LocalBottomSheetBackgroundColor = compositionLocalOf { Color.Unspecified }

@Composable
fun overlayActionButtonColors() = ButtonDefaults.buttonColors(
    color = MiuixTheme.colorScheme.surfaceContainer,
    contentColor = MiuixTheme.colorScheme.onSurface,
)

fun Context.isActiveNetworkMetered(): Boolean {
    val connectivityManager = getSystemService(ConnectivityManager::class.java)
    return runCatching {
        connectivityManager?.isActiveNetworkMetered ?: false
    }.getOrDefault(false)
}

@Composable
fun NonAmoledDarkTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(
            colorSchemeMode = ColorSchemeMode.Dark,
            darkColors = darkColorScheme(),
        )
    }
    MiuixTheme(controller = controller) {
        content()
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
fun PredictiveBackGestureHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return
    }

    BackHandler(enabled = enabled) {
        onBack()
    }
}

@Composable
fun adaptiveIllustColumns(settings: AppSettings): Int {
    val configuration = LocalConfiguration.current
    val columns by remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        settings.horizontalColumnCount,
        settings.verticalColumnCount,
    ) {
        derivedStateOf {
            val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
            if (isLandscape) {
                settings.horizontalColumnCount.coerceIn(3, 6)
            } else {
                settings.verticalColumnCount.coerceIn(2, 4)
            }
        }
    }
    return columns
}

fun Modifier.horizontalPadding(padding: Dp): Modifier = layout { measurable, constraints ->
    val paddingPx = padding.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth - paddingPx * 2))
    layout(placeable.width, placeable.height) {
        placeable.place(paddingPx, 0)
    }
}

@Composable
fun Modifier.miuixClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    haptic: Boolean = false,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    return pressable(
        interactionSource = null,
        indication = SinkFeedback(sinkAmount = pressedScale),
    ).clickable(
        interactionSource = null,
        indication = null,
        onClick = {
            if (haptic) performAppHapticFeedback(context, hapticFeedback, hapticMode)
            onClick()
        },
    )
}

@Composable
fun AvatarImage(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    val commonModifier = modifier
        .size(size)
        .clip(CircleShape)
        .background(MiuixTheme.colorScheme.surfaceContainerHigh)

    if (url != null) {
        PixivImage(
            url = url,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = commonModifier,
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
