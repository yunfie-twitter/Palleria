package com.yunfie.illustia.settings

/**
 * アプリの表示言語。
 *
 * [languageTag] は BCP-47 形式で、ロケール解決はプラットフォーム側が行う。
 */
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
