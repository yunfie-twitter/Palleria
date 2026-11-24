package com.yunfie.illustia.settings

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.flag_artwork_dynamic_theme_desc
import com.yunfie.illustia.resources.flag_artwork_dynamic_theme_title
import com.yunfie.illustia.resources.flag_bookmark_haptic_burst_desc
import com.yunfie.illustia.resources.flag_bookmark_haptic_burst_title
import com.yunfie.illustia.resources.flag_byte_range_resume_desc
import com.yunfie.illustia.resources.flag_byte_range_resume_title
import com.yunfie.illustia.resources.flag_courtesy_audio_fade_desc
import com.yunfie.illustia.resources.flag_courtesy_audio_fade_title
import com.yunfie.illustia.resources.flag_custom_download_path_desc
import com.yunfie.illustia.resources.flag_custom_download_path_title
import com.yunfie.illustia.resources.flag_delta_etag_sync_desc
import com.yunfie.illustia.resources.flag_delta_etag_sync_title
import com.yunfie.illustia.resources.flag_disable_adaptive_refresh_rate_desc
import com.yunfie.illustia.resources.flag_disable_adaptive_refresh_rate_title
import com.yunfie.illustia.resources.flag_fast_scroll_rendering_desc
import com.yunfie.illustia.resources.flag_fast_scroll_rendering_title
import com.yunfie.illustia.resources.flag_hide_home_novel_button_desc
import com.yunfie.illustia.resources.flag_hide_home_novel_button_title
import com.yunfie.illustia.resources.flag_image_blur_preview_desc
import com.yunfie.illustia.resources.flag_image_blur_preview_title
import com.yunfie.illustia.resources.flag_liquid_glass_buttons_desc
import com.yunfie.illustia.resources.flag_liquid_glass_buttons_title
import com.yunfie.illustia.resources.flag_liquid_glass_follow_button_desc
import com.yunfie.illustia.resources.flag_liquid_glass_follow_button_title
import com.yunfie.illustia.resources.flag_manga_adaptive_preload_desc
import com.yunfie.illustia.resources.flag_manga_adaptive_preload_title
import com.yunfie.illustia.resources.flag_miuix_quick_peek_desc
import com.yunfie.illustia.resources.flag_miuix_quick_peek_title
import com.yunfie.illustia.resources.flag_navigation_customization_desc
import com.yunfie.illustia.resources.flag_navigation_customization_title
import com.yunfie.illustia.resources.flag_navigation_icons_only_desc
import com.yunfie.illustia.resources.flag_navigation_icons_only_title
import com.yunfie.illustia.resources.flag_novel_tts_audiobook_desc
import com.yunfie.illustia.resources.flag_novel_tts_audiobook_title
import com.yunfie.illustia.resources.flag_offline_stale_cache_desc
import com.yunfie.illustia.resources.flag_offline_stale_cache_title
import com.yunfie.illustia.resources.flag_predns_socket_warming_desc
import com.yunfie.illustia.resources.flag_predns_socket_warming_title
import com.yunfie.illustia.resources.flag_privacy_exif_stripper_desc
import com.yunfie.illustia.resources.flag_privacy_exif_stripper_title
import com.yunfie.illustia.resources.flag_progressive_image_quality_desc
import com.yunfie.illustia.resources.flag_progressive_image_quality_title
import com.yunfie.illustia.resources.flag_shared_element_transitions_desc
import com.yunfie.illustia.resources.flag_shared_element_transitions_title
import com.yunfie.illustia.resources.flag_shorts_feed_desc
import com.yunfie.illustia.resources.flag_shorts_feed_title
import com.yunfie.illustia.resources.flag_smart_download_naming_desc
import com.yunfie.illustia.resources.flag_smart_download_naming_title
import com.yunfie.illustia.resources.flag_tag_menu_redesign_desc
import com.yunfie.illustia.resources.flag_tag_menu_redesign_title
import com.yunfie.illustia.resources.flag_task_snapshot_blur_desc
import com.yunfie.illustia.resources.flag_task_snapshot_blur_title
import com.yunfie.illustia.resources.flag_top_scroll_blur_desc
import com.yunfie.illustia.resources.flag_top_scroll_blur_title
import com.yunfie.illustia.resources.flag_tracking_url_cleaner_desc
import com.yunfie.illustia.resources.flag_tracking_url_cleaner_title
import com.yunfie.illustia.resources.flag_ugoira_power_save_desc
import com.yunfie.illustia.resources.flag_ugoira_power_save_title
import com.yunfie.illustia.resources.flag_user_profile_edit_desc
import com.yunfie.illustia.resources.flag_user_profile_edit_title
import com.yunfie.illustia.resources.flag_velocity_landing_prefetch_desc
import com.yunfie.illustia.resources.flag_velocity_landing_prefetch_title
import com.yunfie.illustia.resources.flag_volume_key_page_turner_desc
import com.yunfie.illustia.resources.flag_volume_key_page_turner_title
import org.jetbrains.compose.resources.StringResource

/** 機能フラグの表示ラベル。キーは shared 側の enum が保持する。 */
val FeatureFlag.titleRes: StringResource
    get() =
        when (this) {
            FeatureFlag.FastScrollRendering -> Res.string.flag_fast_scroll_rendering_title
            FeatureFlag.NavigationCustomization -> Res.string.flag_navigation_customization_title
            FeatureFlag.TopScrollBlur -> Res.string.flag_top_scroll_blur_title
            FeatureFlag.NavigationIconsOnly -> Res.string.flag_navigation_icons_only_title
            FeatureFlag.ShortsFeed -> Res.string.flag_shorts_feed_title
            FeatureFlag.HideHomeNovelButton -> Res.string.flag_hide_home_novel_button_title
            FeatureFlag.ArtworkDynamicTheme -> Res.string.flag_artwork_dynamic_theme_title
            FeatureFlag.CustomDownloadPath -> Res.string.flag_custom_download_path_title
            FeatureFlag.VolumeKeyPageTurner -> Res.string.flag_volume_key_page_turner_title
            FeatureFlag.NovelTtsAudiobook -> Res.string.flag_novel_tts_audiobook_title
            FeatureFlag.UserProfileEdit -> Res.string.flag_user_profile_edit_title
            FeatureFlag.OfflineStaleCache -> Res.string.flag_offline_stale_cache_title
            FeatureFlag.BookmarkHapticBurst -> Res.string.flag_bookmark_haptic_burst_title
            FeatureFlag.ImageBlurPreview -> Res.string.flag_image_blur_preview_title
            FeatureFlag.TaskSnapshotBlur -> Res.string.flag_task_snapshot_blur_title
            FeatureFlag.DisableAdaptiveRefreshRate -> Res.string.flag_disable_adaptive_refresh_rate_title
            FeatureFlag.CourtesyAudioFade -> Res.string.flag_courtesy_audio_fade_title
            FeatureFlag.TrackingUrlCleaner -> Res.string.flag_tracking_url_cleaner_title
            FeatureFlag.SmartDownloadNaming -> Res.string.flag_smart_download_naming_title
            FeatureFlag.PrivacyExifStripper -> Res.string.flag_privacy_exif_stripper_title
            FeatureFlag.UgoiraPowerSave -> Res.string.flag_ugoira_power_save_title
            FeatureFlag.MangaAdaptivePreload -> Res.string.flag_manga_adaptive_preload_title
            FeatureFlag.VelocityLandingPrefetch -> Res.string.flag_velocity_landing_prefetch_title
            FeatureFlag.ByteRangeResume -> Res.string.flag_byte_range_resume_title
            FeatureFlag.PreDnsSocketWarming -> Res.string.flag_predns_socket_warming_title
            FeatureFlag.DeltaEtagSync -> Res.string.flag_delta_etag_sync_title
            FeatureFlag.SharedElementTransitions -> Res.string.flag_shared_element_transitions_title
            FeatureFlag.LiquidGlassButtons -> Res.string.flag_liquid_glass_buttons_title
            FeatureFlag.LiquidGlassFollowButton -> Res.string.flag_liquid_glass_follow_button_title
            FeatureFlag.ProgressiveImageQuality -> Res.string.flag_progressive_image_quality_title
            FeatureFlag.MiuixQuickPeek -> Res.string.flag_miuix_quick_peek_title
            FeatureFlag.TagMenuRedesign -> Res.string.flag_tag_menu_redesign_title
        }

val FeatureFlag.descRes: StringResource
    get() =
        when (this) {
            FeatureFlag.FastScrollRendering -> Res.string.flag_fast_scroll_rendering_desc
            FeatureFlag.NavigationCustomization -> Res.string.flag_navigation_customization_desc
            FeatureFlag.TopScrollBlur -> Res.string.flag_top_scroll_blur_desc
            FeatureFlag.NavigationIconsOnly -> Res.string.flag_navigation_icons_only_desc
            FeatureFlag.ShortsFeed -> Res.string.flag_shorts_feed_desc
            FeatureFlag.HideHomeNovelButton -> Res.string.flag_hide_home_novel_button_desc
            FeatureFlag.ArtworkDynamicTheme -> Res.string.flag_artwork_dynamic_theme_desc
            FeatureFlag.CustomDownloadPath -> Res.string.flag_custom_download_path_desc
            FeatureFlag.VolumeKeyPageTurner -> Res.string.flag_volume_key_page_turner_desc
            FeatureFlag.NovelTtsAudiobook -> Res.string.flag_novel_tts_audiobook_desc
            FeatureFlag.UserProfileEdit -> Res.string.flag_user_profile_edit_desc
            FeatureFlag.OfflineStaleCache -> Res.string.flag_offline_stale_cache_desc
            FeatureFlag.BookmarkHapticBurst -> Res.string.flag_bookmark_haptic_burst_desc
            FeatureFlag.ImageBlurPreview -> Res.string.flag_image_blur_preview_desc
            FeatureFlag.TaskSnapshotBlur -> Res.string.flag_task_snapshot_blur_desc
            FeatureFlag.DisableAdaptiveRefreshRate -> Res.string.flag_disable_adaptive_refresh_rate_desc
            FeatureFlag.CourtesyAudioFade -> Res.string.flag_courtesy_audio_fade_desc
            FeatureFlag.TrackingUrlCleaner -> Res.string.flag_tracking_url_cleaner_desc
            FeatureFlag.SmartDownloadNaming -> Res.string.flag_smart_download_naming_desc
            FeatureFlag.PrivacyExifStripper -> Res.string.flag_privacy_exif_stripper_desc
            FeatureFlag.UgoiraPowerSave -> Res.string.flag_ugoira_power_save_desc
            FeatureFlag.MangaAdaptivePreload -> Res.string.flag_manga_adaptive_preload_desc
            FeatureFlag.VelocityLandingPrefetch -> Res.string.flag_velocity_landing_prefetch_desc
            FeatureFlag.ByteRangeResume -> Res.string.flag_byte_range_resume_desc
            FeatureFlag.PreDnsSocketWarming -> Res.string.flag_predns_socket_warming_desc
            FeatureFlag.DeltaEtagSync -> Res.string.flag_delta_etag_sync_desc
            FeatureFlag.SharedElementTransitions -> Res.string.flag_shared_element_transitions_desc
            FeatureFlag.LiquidGlassButtons -> Res.string.flag_liquid_glass_buttons_desc
            FeatureFlag.LiquidGlassFollowButton -> Res.string.flag_liquid_glass_follow_button_desc
            FeatureFlag.ProgressiveImageQuality -> Res.string.flag_progressive_image_quality_desc
            FeatureFlag.MiuixQuickPeek -> Res.string.flag_miuix_quick_peek_desc
            FeatureFlag.TagMenuRedesign -> Res.string.flag_tag_menu_redesign_desc
        }
