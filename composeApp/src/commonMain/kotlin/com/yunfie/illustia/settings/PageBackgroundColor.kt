package com.yunfie.illustia.settings

import androidx.compose.ui.graphics.Color

/**
 * ページ背景色を決める。動的カラーが有効かつ利用可能な場合は動的カラーを優先する。
 */
fun resolvePageBackgroundColor(
    useDynamicColor: Boolean,
    dynamicColorAvailable: Boolean,
    dynamicBackgroundColor: Color,
    fallbackBackgroundColor: Color,
): Color =
    if (useDynamicColor && dynamicColorAvailable) {
        dynamicBackgroundColor
    } else {
        fallbackBackgroundColor
    }
