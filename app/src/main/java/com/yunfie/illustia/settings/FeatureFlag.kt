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
    FastScrollRendering(
        key = "flag_fast_scroll_rendering",
        titleRes = R.string.flag_fast_scroll_rendering_title,
        descRes = R.string.flag_fast_scroll_rendering_desc,
    ),
    CommandPalette(
        key = "flag_command_palette",
        titleRes = R.string.flag_command_palette_title,
        descRes = R.string.flag_command_palette_desc,
        defaultEnabled = true,
    ),
    CustomAppIcon(
        key = "flag_custom_app_icon",
        titleRes = R.string.flag_custom_app_icon_title,
        descRes = R.string.flag_custom_app_icon_desc,
        defaultEnabled = true,
    ),
    NavigationCustomization(
        key = "flag_navigation_customization",
        titleRes = R.string.flag_navigation_customization_title,
        descRes = R.string.flag_navigation_customization_desc,
        defaultEnabled = false,
    ),
    TopScrollBlur(
        key = "flag_top_scroll_blur",
        titleRes = R.string.flag_top_scroll_blur_title,
        descRes = R.string.flag_top_scroll_blur_desc,
    ),
    NavigationIconsOnly(
        key = "flag_navigation_icons_only",
        titleRes = R.string.flag_navigation_icons_only_title,
        descRes = R.string.flag_navigation_icons_only_desc,
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
        defaultEnabled = true,
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
        defaultEnabled = true,
    ),
    GridPinchToZoomColumns(
        key = "flag_grid_pinch_to_zoom_columns",
        titleRes = R.string.flag_grid_pinch_to_zoom_title,
        descRes = R.string.flag_grid_pinch_to_zoom_desc,
        defaultEnabled = true,
    ),
    QuickPeekSharedElementTransition(
        key = "flag_quick_peek_shared_element_transition",
        titleRes = R.string.flag_quick_peek_shared_element_transition_title,
        descRes = R.string.flag_quick_peek_shared_element_transition_desc,
        defaultEnabled = true,
    ),
    UgoiraAutoPlay(
        key = "flag_ugoira_auto_play",
        titleRes = R.string.flag_ugoira_auto_play_title,
        descRes = R.string.flag_ugoira_auto_play_desc,
        defaultEnabled = true,
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

fun AppSettings.isFeatureEnabled(flag: FeatureFlag): Boolean = featureFlags[flag.key] ?: flag.defaultEnabled
