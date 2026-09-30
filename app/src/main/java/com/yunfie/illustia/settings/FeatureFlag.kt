package com.yunfie.illustia.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R

@Immutable
enum class FeatureFlag(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val defaultEnabled: Boolean = false,
) {
    CustomAppIcon(
        key = "flag_custom_app_icon",
        titleRes = R.string.flag_custom_app_icon_title,
        descRes = R.string.flag_custom_app_icon_desc,
        defaultEnabled = false,
    ),
    NavigationCustomization(
        key = "flag_navigation_customization",
        titleRes = R.string.flag_navigation_customization_title,
        descRes = R.string.flag_navigation_customization_desc,
        defaultEnabled = false,
    ),
    ShortsFeed(
        key = "flag_shorts_feed",
        titleRes = R.string.flag_shorts_feed_title,
        descRes = R.string.flag_shorts_feed_desc,
        defaultEnabled = false,
    ),
    HideHomeNovelButton(
        key = "flag_hide_home_novel_button",
        titleRes = R.string.flag_hide_home_novel_button_title,
        descRes = R.string.flag_hide_home_novel_button_desc,
        defaultEnabled = false,
    ),
    ArtworkDynamicTheme(
        key = "flag_artwork_dynamic_theme",
        titleRes = R.string.flag_artwork_dynamic_theme_title,
        descRes = R.string.flag_artwork_dynamic_theme_desc,
        defaultEnabled = false,
    ),
    EmbedMetadata(
        key = "flag_embed_metadata",
        titleRes = R.string.flag_embed_metadata_title,
        descRes = R.string.flag_embed_metadata_desc,
        defaultEnabled = false,
    ),
    CustomDownloadPath(
        key = "flag_custom_download_path",
        titleRes = R.string.flag_custom_download_path_title,
        descRes = R.string.flag_custom_download_path_desc,
        defaultEnabled = false,
    ),
    VolumeKeyPageTurner(
        key = "flag_volume_key_page_turner",
        titleRes = R.string.flag_volume_key_page_turner_title,
        descRes = R.string.flag_volume_key_page_turner_desc,
        defaultEnabled = false,
    ),
    NovelTtsAudiobook(
        key = "flag_novel_tts_audiobook",
        titleRes = R.string.flag_novel_tts_audiobook_title,
        descRes = R.string.flag_novel_tts_audiobook_desc,
        defaultEnabled = false,
    ),
    QuickPeek(
        key = "flag_quick_peek",
        titleRes = R.string.flag_quick_peek_title,
        descRes = R.string.flag_quick_peek_desc,
        defaultEnabled = false,
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

fun AppSettings.isFeatureEnabled(flag: FeatureFlag): Boolean = featureFlags[flag.key] ?: flag.defaultEnabled
