package com.yunfie.illustia.settings

import android.os.LocaleList
import com.yunfie.illustia.R

enum class AppLanguage(
    val value: String,
    val languageTag: String?,
) {
    System("system", null),
    Japanese("ja", "ja-JP"),
    English("en", "en-US");

    companion object {
        fun fromValue(value: String): AppLanguage {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

fun appLanguageOptions(): List<String> = AppLanguage.entries.map { it.value }

fun appLanguageLocaleList(value: String): LocaleList {
    val language = AppLanguage.fromValue(value)
    return language.languageTag
        ?.let(LocaleList::forLanguageTags)
        ?: LocaleList.getEmptyLocaleList()
}

fun appLanguageLabelRes(value: String): Int {
    return when (AppLanguage.fromValue(value)) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.Japanese -> R.string.language_japanese
        AppLanguage.English -> R.string.language_english
    }
}

fun currentAcceptLanguage(): String {
    val primaryLanguage = LocaleList.getDefault().get(0)?.language.orEmpty()
    return if (primaryLanguage == "ja") {
        AppLanguage.Japanese.languageTag ?: "ja-JP"
    } else {
        AppLanguage.English.languageTag ?: "en-US"
    }
}
