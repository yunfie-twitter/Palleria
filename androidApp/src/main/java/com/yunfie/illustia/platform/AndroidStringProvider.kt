package com.yunfie.illustia.platform

import android.content.Context
import com.yunfie.illustia.R

/** 既存の `Application` ベースの解決。移行期間中の既定実装。 */
class AndroidStringProvider(
    private val context: Context,
) : StringProvider {
    override fun get(key: StringKey): String = context.getString(key.resId())

    override fun get(
        key: StringKey,
        vararg args: Any,
    ): String = context.getString(key.resId(), *args)

    @Suppress("CyclomaticComplexMethod")
    private fun StringKey.resId(): Int =
        when (this) {
            StringKey.AppLockRecoveryAccountMismatch -> R.string.app_lock_recovery_account_mismatch
            StringKey.DetailSaveOffline -> R.string.detail_save_offline
            StringKey.DownloadQueueWaiting -> R.string.download_queue_waiting
            StringKey.ErrorBadRequest -> R.string.error_bad_request
            StringKey.ErrorBookmarkFailed -> R.string.error_bookmark_failed
            StringKey.ErrorCacheDeleteFailed -> R.string.error_cache_delete_failed
            StringKey.ErrorDataExportFailed -> R.string.error_data_export_failed
            StringKey.ErrorDataImportFailed -> R.string.error_data_import_failed
            StringKey.ErrorForbidden -> R.string.error_forbidden
            StringKey.ErrorGeneric -> R.string.error_generic
            StringKey.ErrorIllustNotFound -> R.string.error_illust_not_found
            StringKey.ErrorLoadArtistFailed -> R.string.error_load_artist_failed
            StringKey.ErrorLoadDetailFailed -> R.string.error_load_detail_failed
            StringKey.ErrorNotFound -> R.string.error_not_found
            StringKey.ErrorNotificationsLoadFailed -> R.string.error_notifications_load_failed
            StringKey.ErrorNotificationOpenFailed -> R.string.error_notification_open_failed
            StringKey.ErrorNovelLoadFailed -> R.string.error_novel_load_failed
            StringKey.ErrorPixivUserIdNotSet -> R.string.error_pixiv_user_id_not_set
            StringKey.ErrorRateLimited -> R.string.error_rate_limited
            StringKey.ErrorRelatedUsersLoadFailed -> R.string.error_related_users_load_failed
            StringKey.ErrorSaveFailed -> R.string.error_save_failed
            StringKey.ErrorServerError -> R.string.error_server_error
            StringKey.ErrorTimeout -> R.string.error_timeout
            StringKey.MsgBookmarkAdded -> R.string.msg_bookmark_added
            StringKey.MsgBookmarkRemoved -> R.string.msg_bookmark_removed
            StringKey.MsgCacheDeleted -> R.string.msg_cache_deleted
            StringKey.MsgDataExported -> R.string.msg_data_exported
            StringKey.MsgDataImported -> R.string.msg_data_imported
            StringKey.MsgDownloadHistoryCleared -> R.string.msg_download_history_cleared
            StringKey.MsgLoggedOut -> R.string.msg_logged_out
            StringKey.MsgMuteDataDeleted -> R.string.msg_mute_data_deleted
            StringKey.MsgNoSaveableImages -> R.string.msg_no_saveable_images
            StringKey.MsgReportFailed -> R.string.msg_report_failed
            StringKey.MsgReportSubmitted -> R.string.msg_report_submitted
            StringKey.MsgSaveSkippedDuplicate -> R.string.msg_save_skipped_duplicate
            StringKey.MsgSaveStarted -> R.string.msg_save_started
            StringKey.MsgSharedImageReceived -> R.string.msg_shared_image_received
            StringKey.MsgWatchlistTagAdded -> R.string.msg_watchlist_tag_added
            StringKey.MsgWatchlistTagRemoved -> R.string.msg_watchlist_tag_removed
            StringKey.MsgWatchlistTagsDeleted -> R.string.msg_watchlist_tags_deleted
            StringKey.MsgWebLoginComplete -> R.string.msg_web_login_complete
            StringKey.NotificationsTargetUnsupported -> R.string.notifications_target_unsupported
            StringKey.OfflineCacheDisplayed -> R.string.offline_cache_displayed
            StringKey.OfflineCapacityLimitDesc -> R.string.offline_capacity_limit_desc
            StringKey.OfflineNoCache -> R.string.offline_no_cache
            StringKey.OfflineWifiOnlyDesc -> R.string.offline_wifi_only_desc
            StringKey.PostUpdateNotice -> R.string.post_update_notice
            StringKey.UpdateCheckFailed -> R.string.update_check_failed
            StringKey.UpdateDownloadFailed -> R.string.update_download_failed
            StringKey.UpdateInstallFailed -> R.string.update_install_failed
            StringKey.UpdateNotificationDownloadedDesc -> R.string.update_notification_downloaded_desc
            StringKey.UgoiraLoadFailed -> R.string.ugoira_load_failed
        }
}
