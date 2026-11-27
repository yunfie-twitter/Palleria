package com.yunfie.illustia.settings

import android.os.LocaleList

fun appLanguageLocaleList(value: String): LocaleList {
    val language = AppLanguage.fromValue(value)
    return language.languageTag
        ?.let(LocaleList::forLanguageTags)
        ?: LocaleList.getEmptyLocaleList()
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
