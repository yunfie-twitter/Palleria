package com.yunfie.illustia.platform

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_cancel
import com.yunfie.illustia.resources.action_save
import com.yunfie.illustia.resources.app_lock_biometric_subtitle
import com.yunfie.illustia.resources.app_lock_biometric_title
import com.yunfie.illustia.resources.app_lock_biometric_verify_subtitle
import com.yunfie.illustia.resources.app_lock_biometric_verify_title
import com.yunfie.illustia.resources.app_lock_recovery_account_mismatch
import com.yunfie.illustia.resources.app_name
import com.yunfie.illustia.resources.data_delete_cache
import com.yunfie.illustia.resources.data_delete_cache_desc
import com.yunfie.illustia.resources.data_delete_mute_data
import com.yunfie.illustia.resources.data_delete_mute_data_desc
import com.yunfie.illustia.resources.data_delete_search_history
import com.yunfie.illustia.resources.data_delete_search_history_desc
import com.yunfie.illustia.resources.data_delete_view_history
import com.yunfie.illustia.resources.data_delete_view_history_desc
import com.yunfie.illustia.resources.data_delete_watchlist_tags
import com.yunfie.illustia.resources.data_delete_watchlist_tags_desc
import com.yunfie.illustia.resources.data_items_count
import com.yunfie.illustia.resources.detail_save_offline
import com.yunfie.illustia.resources.discord_login_success
import com.yunfie.illustia.resources.download_queue_waiting
import com.yunfie.illustia.resources.error_bad_request
import com.yunfie.illustia.resources.error_bookmark_failed
import com.yunfie.illustia.resources.error_cache_delete_failed
import com.yunfie.illustia.resources.error_data_export_failed
import com.yunfie.illustia.resources.error_data_import_failed
import com.yunfie.illustia.resources.error_forbidden
import com.yunfie.illustia.resources.error_generic
import com.yunfie.illustia.resources.error_illust_not_found
import com.yunfie.illustia.resources.error_load_artist_failed
import com.yunfie.illustia.resources.error_load_detail_failed
import com.yunfie.illustia.resources.error_not_found
import com.yunfie.illustia.resources.error_notification_open_failed
import com.yunfie.illustia.resources.error_notifications_load_failed
import com.yunfie.illustia.resources.error_novel_load_failed
import com.yunfie.illustia.resources.error_pixiv_user_id_not_set
import com.yunfie.illustia.resources.error_rate_limited
import com.yunfie.illustia.resources.error_related_users_load_failed
import com.yunfie.illustia.resources.error_save_failed
import com.yunfie.illustia.resources.error_server_error
import com.yunfie.illustia.resources.error_timeout
import com.yunfie.illustia.resources.flag_novel_tts_audiobook_title
import com.yunfie.illustia.resources.live_wallpaper_folder_permission_failed
import com.yunfie.illustia.resources.more_about
import com.yunfie.illustia.resources.more_download_list
import com.yunfie.illustia.resources.more_favorite_tags
import com.yunfie.illustia.resources.more_mute_settings
import com.yunfie.illustia.resources.more_notifications
import com.yunfie.illustia.resources.more_settings
import com.yunfie.illustia.resources.more_view_history
import com.yunfie.illustia.resources.msg_bookmark_added
import com.yunfie.illustia.resources.msg_bookmark_removed
import com.yunfie.illustia.resources.msg_cache_deleted
import com.yunfie.illustia.resources.msg_data_exported
import com.yunfie.illustia.resources.msg_data_imported
import com.yunfie.illustia.resources.msg_download_history_cleared
import com.yunfie.illustia.resources.msg_logged_out
import com.yunfie.illustia.resources.msg_mute_data_deleted
import com.yunfie.illustia.resources.msg_no_saveable_images
import com.yunfie.illustia.resources.msg_report_failed
import com.yunfie.illustia.resources.msg_report_submitted
import com.yunfie.illustia.resources.msg_save_skipped_duplicate
import com.yunfie.illustia.resources.msg_save_started
import com.yunfie.illustia.resources.msg_shared_image_received
import com.yunfie.illustia.resources.msg_watchlist_tag_added
import com.yunfie.illustia.resources.msg_watchlist_tag_removed
import com.yunfie.illustia.resources.msg_watchlist_tags_deleted
import com.yunfie.illustia.resources.msg_web_login_complete
import com.yunfie.illustia.resources.nav_novel
import com.yunfie.illustia.resources.notifications_target_unsupported
import com.yunfie.illustia.resources.offline_cache_displayed
import com.yunfie.illustia.resources.offline_capacity_limit_desc
import com.yunfie.illustia.resources.offline_no_cache
import com.yunfie.illustia.resources.offline_wifi_only_desc
import com.yunfie.illustia.resources.post_update_notice
import com.yunfie.illustia.resources.settings_account
import com.yunfie.illustia.resources.settings_bookmark
import com.yunfie.illustia.resources.settings_bookmark_summary
import com.yunfie.illustia.resources.settings_data
import com.yunfie.illustia.resources.settings_general
import com.yunfie.illustia.resources.settings_general_summary
import com.yunfie.illustia.resources.settings_image
import com.yunfie.illustia.resources.settings_image_summary
import com.yunfie.illustia.resources.settings_logged_in
import com.yunfie.illustia.resources.settings_not_logged_in
import com.yunfie.illustia.resources.tts_channel_description
import com.yunfie.illustia.resources.tts_channel_name
import com.yunfie.illustia.resources.tts_next_paragraph
import com.yunfie.illustia.resources.tts_pause
import com.yunfie.illustia.resources.tts_play
import com.yunfie.illustia.resources.tts_prev_paragraph
import com.yunfie.illustia.resources.tts_stop
import com.yunfie.illustia.resources.ugoira_load_failed
import com.yunfie.illustia.resources.update_check_failed
import com.yunfie.illustia.resources.update_download_failed
import com.yunfie.illustia.resources.update_install_failed
import com.yunfie.illustia.resources.update_method_shizuku
import com.yunfie.illustia.resources.update_method_standard
import com.yunfie.illustia.resources.update_notification_downloaded_desc
import com.yunfie.illustia.resources.update_settings_summary
import com.yunfie.illustia.resources.update_settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.concurrent.ConcurrentHashMap

/**
 * `StringProvider` の Compose Multiplatform 実装。
 *
 * `android.content.Context` を要求しないため `commonMain` から利用できる。文言は
 * `composeResources` に移行済みで、Android の `R` には依存しない。
 *
 * Compose Resources の `getString` は `suspend` だが、[StringProvider] は同期契約
 * （77 か所の `str()` 呼び出しが同期的に使う）。そこで [preload] で先に読み込み、
 * `get` はキャッシュから返す。未読み込みキーは既定ロケールの解決結果ではなく
 * キー名を返し、起動時の早期表示でもクラッシュさせない。
 */
class ComposeStringProvider : StringProvider {
    private val cache = ConcurrentHashMap<StringKey, String>()

    /** 全キーを一度だけ解決してキャッシュする。アプリ起動時に suspend 文脈から呼ぶ。 */
    suspend fun preload() {
        for (key in StringKey.entries) {
            if (!cache.containsKey(key)) {
                cache[key] = getString(key.resource())
            }
        }
    }

    override fun get(key: StringKey): String = cache[key] ?: key.name

    override fun get(
        key: StringKey,
        vararg args: Any,
    ): String = formatStringArgs(cache[key] ?: key.name, args)

    @Suppress("CyclomaticComplexMethod")
    private fun StringKey.resource(): StringResource = RESOURCES.getValue(this)

    private companion object {
        private val RESOURCES: Map<StringKey, StringResource> =
            mapOf(
                StringKey.AppLockRecoveryAccountMismatch to Res.string.app_lock_recovery_account_mismatch,
                StringKey.DetailSaveOffline to Res.string.detail_save_offline,
                StringKey.DownloadQueueWaiting to Res.string.download_queue_waiting,
                StringKey.ErrorBadRequest to Res.string.error_bad_request,
                StringKey.ErrorBookmarkFailed to Res.string.error_bookmark_failed,
                StringKey.ErrorCacheDeleteFailed to Res.string.error_cache_delete_failed,
                StringKey.ErrorDataExportFailed to Res.string.error_data_export_failed,
                StringKey.ErrorDataImportFailed to Res.string.error_data_import_failed,
                StringKey.ErrorForbidden to Res.string.error_forbidden,
                StringKey.ErrorGeneric to Res.string.error_generic,
                StringKey.ErrorIllustNotFound to Res.string.error_illust_not_found,
                StringKey.ErrorLoadArtistFailed to Res.string.error_load_artist_failed,
                StringKey.ErrorLoadDetailFailed to Res.string.error_load_detail_failed,
                StringKey.ErrorNotFound to Res.string.error_not_found,
                StringKey.ErrorNotificationsLoadFailed to Res.string.error_notifications_load_failed,
                StringKey.ErrorNotificationOpenFailed to Res.string.error_notification_open_failed,
                StringKey.ErrorNovelLoadFailed to Res.string.error_novel_load_failed,
                StringKey.ErrorPixivUserIdNotSet to Res.string.error_pixiv_user_id_not_set,
                StringKey.ErrorRateLimited to Res.string.error_rate_limited,
                StringKey.ErrorRelatedUsersLoadFailed to Res.string.error_related_users_load_failed,
                StringKey.ErrorSaveFailed to Res.string.error_save_failed,
                StringKey.ErrorServerError to Res.string.error_server_error,
                StringKey.ErrorTimeout to Res.string.error_timeout,
                StringKey.MsgBookmarkAdded to Res.string.msg_bookmark_added,
                StringKey.MsgBookmarkRemoved to Res.string.msg_bookmark_removed,
                StringKey.MsgCacheDeleted to Res.string.msg_cache_deleted,
                StringKey.MsgDataExported to Res.string.msg_data_exported,
                StringKey.MsgDataImported to Res.string.msg_data_imported,
                StringKey.MsgDownloadHistoryCleared to Res.string.msg_download_history_cleared,
                StringKey.MsgLoggedOut to Res.string.msg_logged_out,
                StringKey.MsgMuteDataDeleted to Res.string.msg_mute_data_deleted,
                StringKey.MsgNoSaveableImages to Res.string.msg_no_saveable_images,
                StringKey.MsgReportFailed to Res.string.msg_report_failed,
                StringKey.MsgReportSubmitted to Res.string.msg_report_submitted,
                StringKey.MsgSaveSkippedDuplicate to Res.string.msg_save_skipped_duplicate,
                StringKey.MsgSaveStarted to Res.string.msg_save_started,
                StringKey.MsgSharedImageReceived to Res.string.msg_shared_image_received,
                StringKey.MsgWatchlistTagAdded to Res.string.msg_watchlist_tag_added,
                StringKey.MsgWatchlistTagRemoved to Res.string.msg_watchlist_tag_removed,
                StringKey.MsgWatchlistTagsDeleted to Res.string.msg_watchlist_tags_deleted,
                StringKey.MsgWebLoginComplete to Res.string.msg_web_login_complete,
                StringKey.NotificationsTargetUnsupported to Res.string.notifications_target_unsupported,
                StringKey.OfflineCacheDisplayed to Res.string.offline_cache_displayed,
                StringKey.OfflineCapacityLimitDesc to Res.string.offline_capacity_limit_desc,
                StringKey.OfflineNoCache to Res.string.offline_no_cache,
                StringKey.OfflineWifiOnlyDesc to Res.string.offline_wifi_only_desc,
                StringKey.PostUpdateNotice to Res.string.post_update_notice,
                StringKey.UpdateCheckFailed to Res.string.update_check_failed,
                StringKey.UpdateDownloadFailed to Res.string.update_download_failed,
                StringKey.UpdateInstallFailed to Res.string.update_install_failed,
                StringKey.UpdateNotificationDownloadedDesc to Res.string.update_notification_downloaded_desc,
                StringKey.UgoiraLoadFailed to Res.string.ugoira_load_failed,
                StringKey.ActionCancel to Res.string.action_cancel,
                StringKey.ActionSave to Res.string.action_save,
                StringKey.AppLockBiometricSubtitle to Res.string.app_lock_biometric_subtitle,
                StringKey.AppLockBiometricTitle to Res.string.app_lock_biometric_title,
                StringKey.AppLockBiometricVerifySubtitle to Res.string.app_lock_biometric_verify_subtitle,
                StringKey.AppLockBiometricVerifyTitle to Res.string.app_lock_biometric_verify_title,
                StringKey.AppName to Res.string.app_name,
                StringKey.DataDeleteCache to Res.string.data_delete_cache,
                StringKey.DataDeleteCacheDesc to Res.string.data_delete_cache_desc,
                StringKey.DataDeleteMuteData to Res.string.data_delete_mute_data,
                StringKey.DataDeleteMuteDataDesc to Res.string.data_delete_mute_data_desc,
                StringKey.DataDeleteSearchHistory to Res.string.data_delete_search_history,
                StringKey.DataDeleteSearchHistoryDesc to Res.string.data_delete_search_history_desc,
                StringKey.DataDeleteViewHistory to Res.string.data_delete_view_history,
                StringKey.DataDeleteViewHistoryDesc to Res.string.data_delete_view_history_desc,
                StringKey.DataDeleteWatchlistTags to Res.string.data_delete_watchlist_tags,
                StringKey.DataDeleteWatchlistTagsDesc to Res.string.data_delete_watchlist_tags_desc,
                StringKey.DataItemsCount to Res.string.data_items_count,
                StringKey.DiscordLoginSuccess to Res.string.discord_login_success,
                StringKey.FlagNovelTtsAudiobookTitle to Res.string.flag_novel_tts_audiobook_title,
                StringKey.LiveWallpaperFolderPermissionFailed to Res.string.live_wallpaper_folder_permission_failed,
                StringKey.MoreAbout to Res.string.more_about,
                StringKey.MoreDownloadList to Res.string.more_download_list,
                StringKey.MoreFavoriteTags to Res.string.more_favorite_tags,
                StringKey.MoreMuteSettings to Res.string.more_mute_settings,
                StringKey.MoreNotifications to Res.string.more_notifications,
                StringKey.MoreSettings to Res.string.more_settings,
                StringKey.MoreViewHistory to Res.string.more_view_history,
                StringKey.NavNovel to Res.string.nav_novel,
                StringKey.SettingsAccount to Res.string.settings_account,
                StringKey.SettingsBookmark to Res.string.settings_bookmark,
                StringKey.SettingsBookmarkSummary to Res.string.settings_bookmark_summary,
                StringKey.SettingsData to Res.string.settings_data,
                StringKey.SettingsGeneral to Res.string.settings_general,
                StringKey.SettingsGeneralSummary to Res.string.settings_general_summary,
                StringKey.SettingsImage to Res.string.settings_image,
                StringKey.SettingsImageSummary to Res.string.settings_image_summary,
                StringKey.SettingsLoggedIn to Res.string.settings_logged_in,
                StringKey.SettingsNotLoggedIn to Res.string.settings_not_logged_in,
                StringKey.TtsChannelDescription to Res.string.tts_channel_description,
                StringKey.TtsChannelName to Res.string.tts_channel_name,
                StringKey.TtsNextParagraph to Res.string.tts_next_paragraph,
                StringKey.TtsPause to Res.string.tts_pause,
                StringKey.TtsPlay to Res.string.tts_play,
                StringKey.TtsPrevParagraph to Res.string.tts_prev_paragraph,
                StringKey.TtsStop to Res.string.tts_stop,
                StringKey.UpdateMethodShizuku to Res.string.update_method_shizuku,
                StringKey.UpdateMethodStandard to Res.string.update_method_standard,
                StringKey.UpdateSettingsSummary to Res.string.update_settings_summary,
                StringKey.UpdateSettingsTitle to Res.string.update_settings_title,
            )
    }
}
