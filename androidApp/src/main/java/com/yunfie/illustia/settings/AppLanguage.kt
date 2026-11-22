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
    Spanish("es", "es-ES"),
    Portuguese("pt", "pt-PT"),
    French("fr", "fr-FR"),
    German("de", "de-DE"),
    Russian("ru", "ru-RU"),
    Indonesian("id", "id-ID"),
    Thai("th", "th-TH"),
    Vietnamese("vi", "vi-VN"),
    Italian("it", "it-IT"),
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
        AppLanguage.Spanish -> R.string.language_spanish
        AppLanguage.Portuguese -> R.string.language_portuguese
        AppLanguage.French -> R.string.language_french
        AppLanguage.German -> R.string.language_german
        AppLanguage.Russian -> R.string.language_russian
        AppLanguage.Indonesian -> R.string.language_indonesian
        AppLanguage.Thai -> R.string.language_thai
        AppLanguage.Vietnamese -> R.string.language_vietnamese
        AppLanguage.Italian -> R.string.language_italian
    }

fun currentAcceptLanguage(): String {
    val locale = LocaleList.getDefault().get(0)
    val primaryLanguage = locale?.language.orEmpty()
    return when (primaryLanguage) {
        "ja" -> AppLanguage.Japanese.languageTag ?: "ja-JP"
        "ko" -> AppLanguage.Korean.languageTag ?: "ko-KR"
        "es" -> AppLanguage.Spanish.languageTag ?: "es-ES"
        "pt" -> AppLanguage.Portuguese.languageTag ?: "pt-PT"
        "fr" -> AppLanguage.French.languageTag ?: "fr-FR"
        "de" -> AppLanguage.German.languageTag ?: "de-DE"
        "ru" -> AppLanguage.Russian.languageTag ?: "ru-RU"
        "id" -> AppLanguage.Indonesian.languageTag ?: "id-ID"
        "th" -> AppLanguage.Thai.languageTag ?: "th-TH"
        "vi" -> AppLanguage.Vietnamese.languageTag ?: "vi-VN"
        "it" -> AppLanguage.Italian.languageTag ?: "it-IT"
        "zh" -> resolveChineseLanguageTag(locale?.script, locale?.country)
        else -> AppLanguage.English.languageTag ?: "en-US"
    }
}

private fun resolveChineseLanguageTag(
    script: String?,
    country: String?,
): String {
    val isTraditional =
        script.equals("Hant", ignoreCase = true) ||
            country in setOf("TW", "HK", "MO")
    return if (isTraditional) {
        AppLanguage.TraditionalChinese.languageTag ?: "zh-Hant"
    } else {
        AppLanguage.SimplifiedChinese.languageTag ?: "zh-Hans"
    }
}
