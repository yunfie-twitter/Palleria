package com.yunfie.illustia.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.decodeFeatureFlags

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
    UserProfileEdit(
        key = "flag_user_profile_edit",
        titleRes = R.string.flag_user_profile_edit_title,
        descRes = R.string.flag_user_profile_edit_desc,
        defaultEnabled = false,
    ),
    OfflineStaleCache(
        key = "flag_offline_stale_cache",
        titleRes = R.string.flag_offline_stale_cache_title,
        descRes = R.string.flag_offline_stale_cache_desc,
        defaultEnabled = true,
    ),
    BookmarkHapticBurst(
        key = "flag_bookmark_haptic_burst",
        titleRes = R.string.flag_bookmark_haptic_burst_title,
        descRes = R.string.flag_bookmark_haptic_burst_desc,
        defaultEnabled = true,
    ),
    ImageBlurPreview(
        key = "flag_image_blur_preview",
        titleRes = R.string.flag_image_blur_preview_title,
        descRes = R.string.flag_image_blur_preview_desc,
        defaultEnabled = true,
    ),
    TaskSnapshotBlur(
        key = "flag_task_snapshot_blur",
        titleRes = R.string.flag_task_snapshot_blur_title,
        descRes = R.string.flag_task_snapshot_blur_desc,
        defaultEnabled = false,
    ),
    CourtesyAudioFade(
        key = "flag_courtesy_audio_fade",
        titleRes = R.string.flag_courtesy_audio_fade_title,
        descRes = R.string.flag_courtesy_audio_fade_desc,
        defaultEnabled = true,
    ),
    TrackingUrlCleaner(
        key = "flag_tracking_url_cleaner",
        titleRes = R.string.flag_tracking_url_cleaner_title,
        descRes = R.string.flag_tracking_url_cleaner_desc,
        defaultEnabled = true,
    ),
    SmartDownloadNaming(
        key = "flag_smart_download_naming",
        titleRes = R.string.flag_smart_download_naming_title,
        descRes = R.string.flag_smart_download_naming_desc,
        defaultEnabled = true,
    ),
    PrivacyExifStripper(
        key = "flag_privacy_exif_stripper",
        titleRes = R.string.flag_privacy_exif_stripper_title,
        descRes = R.string.flag_privacy_exif_stripper_desc,
        defaultEnabled = true,
    ),
    UgoiraPowerSave(
        key = "flag_ugoira_power_save",
        titleRes = R.string.flag_ugoira_power_save_title,
        descRes = R.string.flag_ugoira_power_save_desc,
        defaultEnabled = true,
    ),
    MangaAdaptivePreload(
        key = "flag_manga_adaptive_preload",
        titleRes = R.string.flag_manga_adaptive_preload_title,
        descRes = R.string.flag_manga_adaptive_preload_desc,
        defaultEnabled = true,
    ),
    VelocityLandingPrefetch(
        key = "flag_velocity_landing_prefetch",
        titleRes = R.string.flag_velocity_landing_prefetch_title,
        descRes = R.string.flag_velocity_landing_prefetch_desc,
        defaultEnabled = true,
    ),
    ByteRangeResume(
        key = "flag_byte_range_resume",
        titleRes = R.string.flag_byte_range_resume_title,
        descRes = R.string.flag_byte_range_resume_desc,
        defaultEnabled = true,
    ),
    PreDnsSocketWarming(
        key = "flag_predns_socket_warming",
        titleRes = R.string.flag_predns_socket_warming_title,
        descRes = R.string.flag_predns_socket_warming_desc,
        defaultEnabled = true,
    ),
    DeltaEtagSync(
        key = "flag_delta_etag_sync",
        titleRes = R.string.flag_delta_etag_sync_title,
        descRes = R.string.flag_delta_etag_sync_desc,
        defaultEnabled = true,
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

fun AppSettings.isFeatureEnabled(flag: FeatureFlag): Boolean = featureFlags[flag.key] ?: flag.defaultEnabled

fun readFeatureFlagSync(
    context: Context,
    flag: FeatureFlag,
): Boolean {
    val prefs =
        context.applicationContext.getSharedPreferences(
            LEGACY_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    val raw = prefs.getString("featureFlags", null) ?: return flag.defaultEnabled
    val flags = decodeFeatureFlags(raw)
    return flags[flag.key] ?: flag.defaultEnabled
}
