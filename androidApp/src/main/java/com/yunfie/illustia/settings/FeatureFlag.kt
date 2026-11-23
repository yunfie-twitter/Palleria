package com.yunfie.illustia.settings

import android.content.Context
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.decodeFeatureFlags

@Immutable
enum class FeatureFlag(
    val key: String,
    val defaultEnabled: Boolean = false,
) {
    FastScrollRendering(
        key = "flag_fast_scroll_rendering",
    ),
    NavigationCustomization(
        key = "flag_navigation_customization",
        defaultEnabled = true,
    ),
    TopScrollBlur(
        key = "flag_top_scroll_blur",
    ),
    NavigationIconsOnly(
        key = "flag_navigation_icons_only",
    ),
    ShortsFeed(
        key = "flag_shorts_feed",
        defaultEnabled = false,
    ),
    HideHomeNovelButton(
        key = "flag_hide_home_novel_button",
        defaultEnabled = false,
    ),
    ArtworkDynamicTheme(
        key = "flag_artwork_dynamic_theme",
        defaultEnabled = false,
    ),
    CustomDownloadPath(
        key = "flag_custom_download_path",
        defaultEnabled = false,
    ),
    VolumeKeyPageTurner(
        key = "flag_volume_key_page_turner",
        defaultEnabled = false,
    ),
    NovelTtsAudiobook(
        key = "flag_novel_tts_audiobook",
        defaultEnabled = false,
    ),
    UserProfileEdit(
        key = "flag_user_profile_edit",
        defaultEnabled = false,
    ),
    OfflineStaleCache(
        key = "flag_offline_stale_cache",
        defaultEnabled = true,
    ),
    BookmarkHapticBurst(
        key = "flag_bookmark_haptic_burst",
        defaultEnabled = true,
    ),
    ImageBlurPreview(
        key = "flag_image_blur_preview",
        defaultEnabled = true,
    ),
    TaskSnapshotBlur(
        key = "flag_task_snapshot_blur",
        defaultEnabled = false,
    ),
    DisableAdaptiveRefreshRate(
        key = "flag_disable_adaptive_refresh_rate",
        defaultEnabled = false,
    ),
    CourtesyAudioFade(
        key = "flag_courtesy_audio_fade",
        defaultEnabled = true,
    ),
    TrackingUrlCleaner(
        key = "flag_tracking_url_cleaner",
        defaultEnabled = true,
    ),
    SmartDownloadNaming(
        key = "flag_smart_download_naming",
        defaultEnabled = true,
    ),
    PrivacyExifStripper(
        key = "flag_privacy_exif_stripper",
        defaultEnabled = true,
    ),
    UgoiraPowerSave(
        key = "flag_ugoira_power_save",
        defaultEnabled = true,
    ),
    MangaAdaptivePreload(
        key = "flag_manga_adaptive_preload",
        defaultEnabled = true,
    ),
    VelocityLandingPrefetch(
        key = "flag_velocity_landing_prefetch",
        defaultEnabled = true,
    ),
    ByteRangeResume(
        key = "flag_byte_range_resume",
        defaultEnabled = true,
    ),
    PreDnsSocketWarming(
        key = "flag_predns_socket_warming",
        defaultEnabled = true,
    ),
    DeltaEtagSync(
        key = "flag_delta_etag_sync",
        defaultEnabled = true,
    ),
    SharedElementTransitions(
        key = "flag_shared_element_transitions",
        defaultEnabled = false,
    ),
    LiquidGlassButtons(
        key = "flag_liquid_glass_buttons",
        defaultEnabled = false,
    ),
    LiquidGlassFollowButton(
        key = "flag_liquid_glass_follow_button",
        defaultEnabled = false,
    ),
    ProgressiveImageQuality(
        key = "flag_progressive_image_quality",
        defaultEnabled = false,
    ),
    MiuixQuickPeek(
        key = "flag_miuix_quick_peek",
        defaultEnabled = false,
    ),
    TagMenuRedesign(
        key = "flag_tag_menu_redesign",
        defaultEnabled = false,
    ),
    ;

    companion object {
        fun fromKey(key: String): FeatureFlag? = entries.firstOrNull { it.key == key }
    }
}

fun AppSettings.isFeatureEnabled(flag: FeatureFlag): Boolean = featureFlags[flag.key] ?: flag.defaultEnabled

private val featureFlagCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

fun updateFeatureFlagCache(flags: Map<String, Boolean>) {
    featureFlagCache.putAll(flags)
}

@Suppress("ReturnCount")
fun readFeatureFlagSync(
    context: Context,
    flag: FeatureFlag,
): Boolean {
    featureFlagCache[flag.key]?.let { return it }
    val prefs =
        context.applicationContext.getSharedPreferences(
            LEGACY_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    val raw = prefs.getString("featureFlags", null)
    if (raw == null) {
        featureFlagCache[flag.key] = flag.defaultEnabled
        return flag.defaultEnabled
    }
    val flags = decodeFeatureFlags(raw)
    featureFlagCache.putAll(flags)
    val resolved = flags[flag.key] ?: flag.defaultEnabled
    featureFlagCache[flag.key] = resolved
    return resolved
}
