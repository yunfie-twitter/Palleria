package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.platform.currentTimeMillis
import com.yunfie.illustia.settings.AppSettings
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

data class CachedUserProfile(
    val user: UserProfile,
    val works: List<Illust>,
    val nextUrl: String?,
    val bookmarks: List<Illust>,
    val bookmarkNextUrl: String?,
    val bookmarksLoaded: Boolean,
) {
    fun restore(state: IllustiaUiState): IllustiaUiState =
        state.copy(
            selectedUser = user,
            selectedUserError = null,
            selectedUserIllusts = works,
            selectedUserNextUrl = nextUrl,
            selectedUserBookmarks = bookmarks,
            selectedUserBookmarksNextUrl = bookmarkNextUrl,
            selectedUserIllustsLoaded = true,
            selectedUserBookmarksLoaded = bookmarksLoaded,
            selectedUserIllustsError = null,
            selectedUserBookmarksError = null,
        )
}

private const val PROFILE_CACHE_TTL_MILLIS = 300_000L

/** Bounded, account/filter-specific cache of complete pages, including their continuation URLs. */
class UserProfileCache(
    private val ttlMillis: Long = PROFILE_CACHE_TTL_MILLIS,
    private val maxProfiles: Int = 8,
    private val maxItems: Int = 2_000,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    private data class Context(
        val accountKey: String,
        val muteFilter: MuteFilter,
        val allowR18: Boolean,
        val allowR18G: Boolean,
        val hideAi: Boolean,
    )

    private data class Entry(
        val content: CachedUserProfile,
        val fetchedAt: Long,
    )

    private val lock = SynchronizedObject()
    private var context: Context? = null

    // Kotlin/Native の LinkedHashMap には access-order モードが無いため、保持順は使うたびに
    // 末尾へ入れ直して近似する（get 時に remove → put し直す）。
    private val entries = LinkedHashMap<Long, Entry>()

    fun get(
        userId: Long,
        settings: AppSettings,
    ): CachedUserProfile? =
        synchronized(lock) {
            useContext(settings)
            val entry = entries.remove(userId) ?: return@synchronized null
            if (clock() - entry.fetchedAt >= ttlMillis) return@synchronized null
            entries[userId] = entry
            entry.content
        }

    fun put(state: IllustiaUiState) {
        synchronized(lock) {
            useContext(state.settings)
            val user = state.selectedUser ?: return
            if (state.selectedUserIllustsLoaded) {
                val content =
                    CachedUserProfile(
                        user,
                        state.selectedUserIllusts,
                        state.selectedUserNextUrl,
                        state.selectedUserBookmarks,
                        state.selectedUserBookmarksNextUrl,
                        state.selectedUserBookmarksLoaded,
                    )
                entries.remove(user.id)
                // Do not truncate pages: a truncated list paired with its old cursor would skip works.
                if (content.works.size + content.bookmarks.size > maxItems) return
                entries[user.id] = Entry(content, clock())
                while (entries.size > maxProfiles ||
                    entries.values.sumOf { it.content.works.size + it.content.bookmarks.size } > maxItems
                ) {
                    val eldest = entries.keys.firstOrNull() ?: break
                    entries.remove(eldest)
                }
            }
        }
    }

    fun updateIllust(updated: Illust) {
        synchronized(lock) {
            val keys = entries.keys.toList()
            for (key in keys) {
                val entry = entries[key] ?: continue
                entries[key] =
                    entry.copy(
                        content =
                            entry.content.copy(
                                works = entry.content.works.replaceIllustIfPresent(updated),
                                bookmarks = entry.content.bookmarks.replaceIllustIfPresent(updated),
                            ),
                    )
            }
        }
    }

    fun updateUser(updated: UserProfile) {
        synchronized(lock) {
            val entry = entries[updated.id] ?: return
            entries[updated.id] = entry.copy(content = entry.content.copy(user = updated))
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            context = null
        }
    }

    private fun useContext(settings: AppSettings) {
        val next = Context(settings.refreshToken, settings.toMuteFilter(), settings.allowR18, settings.allowR18G, settings.hideAiWorks)
        if (context != next) {
            entries.clear()
            context = next
        }
    }
}
