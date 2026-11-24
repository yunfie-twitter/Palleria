package com.yunfie.illustia.settings

import android.os.LocaleList
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.language_chinese_simplified
import com.yunfie.illustia.resources.language_chinese_traditional
import com.yunfie.illustia.resources.language_english
import com.yunfie.illustia.resources.language_french
import com.yunfie.illustia.resources.language_german
import com.yunfie.illustia.resources.language_indonesian
import com.yunfie.illustia.resources.language_italian
import com.yunfie.illustia.resources.language_japanese
import com.yunfie.illustia.resources.language_korean
import com.yunfie.illustia.resources.language_portuguese
import com.yunfie.illustia.resources.language_russian
import com.yunfie.illustia.resources.language_spanish
import com.yunfie.illustia.resources.language_system
import com.yunfie.illustia.resources.language_thai
import com.yunfie.illustia.resources.language_vietnamese
import org.jetbrains.compose.resources.StringResource

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

private val appLanguageLabelResources: Map<AppLanguage, StringResource> by lazy {
    mapOf(
        AppLanguage.System to Res.string.language_system,
        AppLanguage.Japanese to Res.string.language_japanese,
        AppLanguage.English to Res.string.language_english,
        AppLanguage.Korean to Res.string.language_korean,
        AppLanguage.SimplifiedChinese to Res.string.language_chinese_simplified,
        AppLanguage.TraditionalChinese to Res.string.language_chinese_traditional,
        AppLanguage.Spanish to Res.string.language_spanish,
        AppLanguage.Portuguese to Res.string.language_portuguese,
        AppLanguage.French to Res.string.language_french,
        AppLanguage.German to Res.string.language_german,
        AppLanguage.Russian to Res.string.language_russian,
        AppLanguage.Indonesian to Res.string.language_indonesian,
        AppLanguage.Thai to Res.string.language_thai,
        AppLanguage.Vietnamese to Res.string.language_vietnamese,
        AppLanguage.Italian to Res.string.language_italian,
    )
}

fun appLanguageLabelRes(value: String): StringResource = appLanguageLabelResources.getValue(AppLanguage.fromValue(value))

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
