package com.yunfie.illustia.settings

import android.os.LocaleList
import com.yunfie.illustia.R

enum class AppLanguage(
    val value: String,
    val languageTag: String?,
) {
    System("system", null),
    Japanese("ja", "ja-JP"),
    English("en", "en-US"),
    Korean("ko", "ko-KR"),
    SimplifiedChinese("zh-Hans", "zh-Hans"),
    TraditionalChinese("zh-Hant", "zh-Hant"),
    ;

    companion object {
        fun fromValue(value: String): AppLanguage = entries.firstOrNull { it.value == value } ?: System
    }
}

fun appLanguageOptions(): List<String> = AppLanguage.entries.map { it.value }

fun appLanguageLocaleList(value: String): LocaleList {
    val language = AppLanguage.fromValue(value)
    return language.languageTag
        ?.let(LocaleList::forLanguageTags)
        ?: LocaleList.getEmptyLocaleList()
}

fun appLanguageLabelRes(value: String): Int =
    when (AppLanguage.fromValue(value)) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.Japanese -> R.string.language_japanese
        AppLanguage.English -> R.string.language_english
        AppLanguage.Korean -> R.string.language_korean
        AppLanguage.SimplifiedChinese -> R.string.language_chinese_simplified
        AppLanguage.TraditionalChinese -> R.string.language_chinese_traditional
    }

fun currentAcceptLanguage(): String {
    val primaryLanguage =
        LocaleList
            .getDefault()
            .get(0)
            ?.language
            .orEmpty()
    return when (primaryLanguage) {
        "ja" -> {
            AppLanguage.Japanese.languageTag ?: "ja-JP"
        }

        "ko" -> {
            AppLanguage.Korean.languageTag ?: "ko-KR"
        }

        "zh" -> {
            val locale = LocaleList.getDefault().get(0)
            if (locale?.script.equals("Hant", ignoreCase = true) ||
                locale?.country in setOf("TW", "HK", "MO")
            ) {
                AppLanguage.TraditionalChinese.languageTag ?: "zh-Hant"
            } else {
                AppLanguage.SimplifiedChinese.languageTag ?: "zh-Hans"
            }
        }

        else -> {
            AppLanguage.English.languageTag ?: "en-US"
        }
    }
}
