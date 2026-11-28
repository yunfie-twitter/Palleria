package com.yunfie.illustia.settings

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

/** 設定画面に並べる言語の値一覧。 */
fun appLanguageOptions(): List<String> = AppLanguage.entries.map { it.value }

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

/** 言語値から表示名の文字列リソースを引く。 */
fun appLanguageLabelRes(value: String): StringResource = appLanguageLabelResources.getValue(AppLanguage.fromValue(value))
