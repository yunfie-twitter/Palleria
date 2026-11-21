package com.yunfie.illustia.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * 分割画面・フリーフォームなど狭いウィンドウで [true] になる。
 *
 * `Activity.isInMultiWindowMode()` とウィンドウ幅の両方で判定する。Activity 側だけだと
 * フリーフォームの超横長ウィンドウで「広い」と判定されてしまうため、幅も見る。
 *
 * 幅は `LocalConfiguration.screenWidthDp` ではなく
 * [LocalWindowInfo] の `containerSize` を使う。screenWidthDp は「画面」幅であり、
 * 分割やフリーフォームの「ウィンドウ」幅を反映しないことがあるため。
 */
@Composable
internal fun rememberIsCompactWindow(): Boolean {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }

    var inMultiWindow by remember(activity) {
        mutableStateOf(activity?.isInMultiWindowMode == true)
    }

    // containerSize は構成変更（分割のリサイズ等）で新しい値になるので、
    // そのたびにマルチウィンドウ状態も取り直す。
    DisposableEffect(activity, windowInfo.containerSize) {
        inMultiWindow = activity?.isInMultiWindowMode == true
        onDispose { }
    }

    val widthDp = with(density) { windowInfo.containerSize.width.toDp() }
    return inMultiWindow || widthDp < COMPACT_WIDTH_DP
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private val COMPACT_WIDTH_DP = 480.dp
