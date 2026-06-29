package com.yunfie.illustia.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R

enum class AppHapticMode(
    val value: String,
    @StringRes val labelResId: Int,
) {
    Rich("rich", R.string.general_haptics_rich),
    Clear("clear", R.string.general_haptics_clear);

    companion object {
        fun fromValue(value: String): AppHapticMode {
            return entries.firstOrNull { it.value == value } ?: Rich
        }
    }
}

fun appHapticOptions(): List<String> = AppHapticMode.entries.map { it.value }

@Composable
fun appHapticLabel(value: String): String {
    return stringResource(AppHapticMode.fromValue(value).labelResId)
}
