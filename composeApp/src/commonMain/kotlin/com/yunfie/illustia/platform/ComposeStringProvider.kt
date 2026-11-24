package com.yunfie.illustia.platform

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.app_lock_recovery_account_mismatch
import com.yunfie.illustia.resources.detail_save_offline
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
import com.yunfie.illustia.resources.notifications_target_unsupported
import com.yunfie.illustia.resources.offline_cache_displayed
import com.yunfie.illustia.resources.offline_capacity_limit_desc
import com.yunfie.illustia.resources.offline_no_cache
import com.yunfie.illustia.resources.offline_wifi_only_desc
import com.yunfie.illustia.resources.post_update_notice
import com.yunfie.illustia.resources.ugoira_load_failed
import com.yunfie.illustia.resources.update_check_failed
import com.yunfie.illustia.resources.update_download_failed
import com.yunfie.illustia.resources.update_install_failed
import com.yunfie.illustia.resources.update_notification_downloaded_desc
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
    private fun StringKey.resource(): StringResource =
        when (this) {
            StringKey.AppLockRecoveryAccountMismatch -> Res.string.app_lock_recovery_account_mismatch
            StringKey.DetailSaveOffline -> Res.string.detail_save_offline
            StringKey.DownloadQueueWaiting -> Res.string.download_queue_waiting
            StringKey.ErrorBadRequest -> Res.string.error_bad_request
            StringKey.ErrorBookmarkFailed -> Res.string.error_bookmark_failed
            StringKey.ErrorCacheDeleteFailed -> Res.string.error_cache_delete_failed
            StringKey.ErrorDataExportFailed -> Res.string.error_data_export_failed
            StringKey.ErrorDataImportFailed -> Res.string.error_data_import_failed
            StringKey.ErrorForbidden -> Res.string.error_forbidden
            StringKey.ErrorGeneric -> Res.string.error_generic
            StringKey.ErrorIllustNotFound -> Res.string.error_illust_not_found
            StringKey.ErrorLoadArtistFailed -> Res.string.error_load_artist_failed
            StringKey.ErrorLoadDetailFailed -> Res.string.error_load_detail_failed
            StringKey.ErrorNotFound -> Res.string.error_not_found
            StringKey.ErrorNotificationsLoadFailed -> Res.string.error_notifications_load_failed
            StringKey.ErrorNotificationOpenFailed -> Res.string.error_notification_open_failed
            StringKey.ErrorNovelLoadFailed -> Res.string.error_novel_load_failed
            StringKey.ErrorPixivUserIdNotSet -> Res.string.error_pixiv_user_id_not_set
            StringKey.ErrorRateLimited -> Res.string.error_rate_limited
            StringKey.ErrorRelatedUsersLoadFailed -> Res.string.error_related_users_load_failed
            StringKey.ErrorSaveFailed -> Res.string.error_save_failed
            StringKey.ErrorServerError -> Res.string.error_server_error
            StringKey.ErrorTimeout -> Res.string.error_timeout
            StringKey.MsgBookmarkAdded -> Res.string.msg_bookmark_added
            StringKey.MsgBookmarkRemoved -> Res.string.msg_bookmark_removed
            StringKey.MsgCacheDeleted -> Res.string.msg_cache_deleted
            StringKey.MsgDataExported -> Res.string.msg_data_exported
            StringKey.MsgDataImported -> Res.string.msg_data_imported
            StringKey.MsgDownloadHistoryCleared -> Res.string.msg_download_history_cleared
            StringKey.MsgLoggedOut -> Res.string.msg_logged_out
            StringKey.MsgMuteDataDeleted -> Res.string.msg_mute_data_deleted
            StringKey.MsgNoSaveableImages -> Res.string.msg_no_saveable_images
            StringKey.MsgReportFailed -> Res.string.msg_report_failed
            StringKey.MsgReportSubmitted -> Res.string.msg_report_submitted
            StringKey.MsgSaveSkippedDuplicate -> Res.string.msg_save_skipped_duplicate
            StringKey.MsgSaveStarted -> Res.string.msg_save_started
            StringKey.MsgSharedImageReceived -> Res.string.msg_shared_image_received
            StringKey.MsgWatchlistTagAdded -> Res.string.msg_watchlist_tag_added
            StringKey.MsgWatchlistTagRemoved -> Res.string.msg_watchlist_tag_removed
            StringKey.MsgWatchlistTagsDeleted -> Res.string.msg_watchlist_tags_deleted
            StringKey.MsgWebLoginComplete -> Res.string.msg_web_login_complete
            StringKey.NotificationsTargetUnsupported -> Res.string.notifications_target_unsupported
            StringKey.OfflineCacheDisplayed -> Res.string.offline_cache_displayed
            StringKey.OfflineCapacityLimitDesc -> Res.string.offline_capacity_limit_desc
            StringKey.OfflineNoCache -> Res.string.offline_no_cache
            StringKey.OfflineWifiOnlyDesc -> Res.string.offline_wifi_only_desc
            StringKey.PostUpdateNotice -> Res.string.post_update_notice
            StringKey.UpdateCheckFailed -> Res.string.update_check_failed
            StringKey.UpdateDownloadFailed -> Res.string.update_download_failed
            StringKey.UpdateInstallFailed -> Res.string.update_install_failed
            StringKey.UpdateNotificationDownloadedDesc -> Res.string.update_notification_downloaded_desc
            StringKey.UgoiraLoadFailed -> Res.string.ugoira_load_failed
        }
}
