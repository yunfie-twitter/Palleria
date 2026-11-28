package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize

/**
 * 現在のウィンドウサイズ（dp）。`LocalConfiguration.screenWidthDp` /
 * `screenHeightDp` は Android 専用なので、Compose Multiplatform の
 * `LocalWindowInfo.containerSize` と `LocalDensity` から同じ値を算出する。
 */
data class WindowSizeDp(
    val widthDp: Int,
    val heightDp: Int,
)

@Composable
@ReadOnlyComposable
fun currentWindowSizeDp(): WindowSizeDp {
    val size: IntSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    return WindowSizeDp(
        widthDp =
            with(density) {
                size.width
                    .toDp()
                    .value
                    .toInt()
            },
        heightDp =
            with(density) {
                size.height
                    .toDp()
                    .value
                    .toInt()
            },
    )
}
