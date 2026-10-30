package com.yunfie.illustia.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yunfie.illustia.models.Restrict
import java.util.concurrent.TimeUnit

/**
 * Enqueues the durable bookmark change with the latest desired state.
 *
 * [ExistingWorkPolicy.REPLACE] makes rapid taps converge on the final state instead of
 * racing several responses. The unique name is per artwork, so unrelated bookmarks run
 * independently.
 */
object BookmarkToggleScheduler {
    private const val UNIQUE_WORK_PREFIX = "bookmark-toggle-"
    private const val BACKOFF_SECONDS = 15L
    private const val TAG = "bookmark-toggle"

    fun enqueue(
        context: Context,
        illustId: Long,
        bookmarked: Boolean,
        restrict: Restrict,
        bookmarkUserId: Long,
        artistId: Long,
        tags: List<String>,
    ) {
        val input =
            workDataOf(
                BookmarkToggleWorker.KEY_ILLUST_ID to illustId,
                BookmarkToggleWorker.KEY_BOOKMARKED to bookmarked,
                BookmarkToggleWorker.KEY_RESTRICT to restrict.name,
                BookmarkToggleWorker.KEY_BOOKMARK_USER_ID to bookmarkUserId,
                BookmarkToggleWorker.KEY_ARTIST_ID to artistId,
                BookmarkToggleWorker.KEY_TAGS to tags.toTypedArray(),
            )
        val request =
            OneTimeWorkRequestBuilder<BookmarkToggleWorker>()
                .setInputData(input)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(illustId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    internal fun uniqueName(illustId: Long): String = "$UNIQUE_WORK_PREFIX$illustId"
}
