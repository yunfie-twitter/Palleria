package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val ProfileGridHorizontalSpacing = 10.dp
internal val ProfileGridVerticalSpacing = 18.dp

@Composable
internal fun adaptiveProfileGridColumns(): Int {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        profileGridColumnsForWidth(configuration.screenWidthDp)
    }
}

internal fun profileGridColumnsForWidth(screenWidthDp: Int): Int =
    when {
        screenWidthDp >= 1_200 -> 5
        screenWidthDp >= 840 -> 4
        screenWidthDp >= 600 -> 3
        else -> 2
    }

@Composable
internal fun adaptiveRecommendedTagColumns(): Int {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        recommendedTagColumnsForWidth(configuration.screenWidthDp)
    }
}

private const val WIDTH_DP_EXTRA_LARGE = 1_400
private const val WIDTH_DP_LARGE = 1_100
private const val TAG_COLUMNS_EXTRA_LARGE = 8
private const val TAG_COLUMNS_LARGE = 7
private const val TAG_COLUMNS_EXPANDED = 6

internal fun recommendedTagColumnsForWidth(screenWidthDp: Int): Int =
    when {
        screenWidthDp >= WIDTH_DP_EXTRA_LARGE -> TAG_COLUMNS_EXTRA_LARGE
        screenWidthDp >= WIDTH_DP_LARGE -> TAG_COLUMNS_LARGE
        screenWidthDp >= 840 -> TAG_COLUMNS_EXPANDED
        screenWidthDp >= 600 -> 4
        else -> 3
    }

@Composable
internal fun adaptiveRelatedIllustColumns(configuredColumns: Int = 3): Int {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuredColumns) {
        relatedIllustColumnsForWidth(configuration.screenWidthDp, configuredColumns)
    }
}

internal fun relatedIllustColumnsForWidth(
    screenWidthDp: Int,
    configuredColumns: Int = 3,
): Int {
    if (configuredColumns in 2..5) {
        return configuredColumns
    }
    return when {
        screenWidthDp >= 1_200 -> 5
        screenWidthDp >= 840 -> 4
        else -> 3
    }
}

internal fun profileGridContentPadding(
    top: Dp = 14.dp,
    bottom: Dp = 96.dp,
): PaddingValues =
    PaddingValues(
        start = 14.dp,
        end = 14.dp,
        top = top,
        bottom = bottom,
    )
