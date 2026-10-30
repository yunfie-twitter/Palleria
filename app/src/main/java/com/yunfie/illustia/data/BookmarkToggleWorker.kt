package com.yunfie.illustia.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Applies a bookmark change as a durable WorkManager job.
 *
 * The optimistic UI update happens immediately, but the request itself must not be
 * tied to the activity/ViewModel scope: closing the app used to cancel it and the
 * bookmark was never sent. WorkManager persists the desired state, retries transient
 * failures, and runs after the process is recreated.
 */
class BookmarkToggleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val illustId = inputData.getLong(KEY_ILLUST_ID, 0L)
            if (illustId <= 0L) return@withContext Result.failure()

            val bookmarked = inputData.getBoolean(KEY_BOOKMARKED, false)
            val restrict =
                inputData
                    .getString(KEY_RESTRICT)
                    ?.let { runCatching { Restrict.valueOf(it) }.getOrNull() }
                    ?: Restrict.Public
            val expectedUserId = inputData.getLong(KEY_BOOKMARK_USER_ID, 0L)
            val artistId = inputData.getLong(KEY_ARTIST_ID, 0L)
            val tags = inputData.getStringArray(KEY_TAGS)?.toList().orEmpty()

            val settingsStore = SettingsStore(applicationContext)
            val settings =
                try {
                    settingsStore.read()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    return@withContext retryOrGiveUp()
                }

            // Never apply a queued change to a different account after the user switches.
            if (expectedUserId > 0L && settings.bookmarkUserId != expectedUserId) {
                return@withContext Result.failure()
            }
            if (settings.refreshToken.isBlank()) {
                return@withContext Result.failure()
            }

            val repository = IllustiaRepository(settingsStore)
            try {
                repository.setBookmarkState(illustId, bookmarked, restrict)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PixivApiException) {
                return@withContext if (failure.statusCode == 429 || failure.statusCode >= 500) {
                    retryOrGiveUp()
                } else {
                    Result.failure()
                }
            } catch (_: IOException) {
                return@withContext retryOrGiveUp()
            } catch (_: Exception) {
                return@withContext Result.failure()
            }

            if (bookmarked) {
                runCatching {
                    if (settings.followOnLike && artistId > 0L) {
                        repository.followUser(artistId, settings.bookmarkRestrict)
                    }
                }
                runCatching {
                    if (settings.autoTagOnBookmark && tags.isNotEmpty()) {
                        val latest = settingsStore.read()
                        val nextTags = (tags.take(3) + latest.favoriteTags).distinct().take(24)
                        if (nextTags != latest.favoriteTags) {
                            repository.saveSettings(latest.copy(favoriteTags = nextTags), latest)
                        }
                    }
                }
                // autoDownloadOnBookmark needs the artwork download pipeline and stays a
                // foreground/UI follow-up; the bookmark itself is already durable here.
            }
            Result.success()
        }

    private fun retryOrGiveUp(): Result = if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()

    companion object {
        internal const val KEY_ILLUST_ID = "illust_id"
        internal const val KEY_BOOKMARKED = "bookmarked"
        internal const val KEY_RESTRICT = "restrict"
        internal const val KEY_BOOKMARK_USER_ID = "bookmark_user_id"
        internal const val KEY_ARTIST_ID = "artist_id"
        internal const val KEY_TAGS = "tags"

        private const val MAX_ATTEMPTS = 5
    }
}
