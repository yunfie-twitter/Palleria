package com.yunfie.illustia.settings

enum class AppHapticMode(
    val value: String,
) {
    Off("off"),
    Rich("rich"),
    Clear("clear"),
    ;

    companion object {
        fun fromValue(value: String): AppHapticMode = entries.firstOrNull { it.value == value } ?: Rich
    }
}

fun effectiveAppHapticMode(
    value: String,
    supported: Boolean,
): AppHapticMode = if (supported) AppHapticMode.fromValue(value) else AppHapticMode.Off
