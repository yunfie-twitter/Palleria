package com.yunfie.illustia.platform

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal enum class WindowWidthSizeClass {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        fun fromWidth(width: Dp): WindowWidthSizeClass =
            when {
                width < 600.dp -> Compact
                width < 840.dp -> Medium
                else -> Expanded
            }
    }
}

internal enum class WindowHeightSizeClass {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        fun fromHeight(height: Dp): WindowHeightSizeClass =
            when {
                height < 480.dp -> Compact
                height < 900.dp -> Medium
                else -> Expanded
            }
    }
}

internal data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
) {
    val isExpanded: Boolean get() = widthSizeClass == WindowWidthSizeClass.Expanded
    val isCompact: Boolean get() = widthSizeClass == WindowWidthSizeClass.Compact
    val isMedium: Boolean get() = widthSizeClass == WindowWidthSizeClass.Medium

    companion object {
        fun calculate(
            width: Dp,
            height: Dp,
        ): WindowSizeClass =
            WindowSizeClass(
                widthSizeClass = WindowWidthSizeClass.fromWidth(width),
                heightSizeClass = WindowHeightSizeClass.fromHeight(height),
            )
    }
}
