package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings

internal data class CachedUserProfile(
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
            selectedUserIllusts = works,
            selectedUserNextUrl = nextUrl,
            selectedUserBookmarks = bookmarks,
            selectedUserBookmarksNextUrl = bookmarkNextUrl,
            selectedUserIllustsLoaded = true,
            selectedUserBookmarksLoaded = bookmarksLoaded,
        )
}

private const val PROFILE_CACHE_TTL_MILLIS = 300_000L
private const val NANOS_PER_MILLI = 1_000_000L

/** Bounded, account/filter-specific cache of complete pages, including their continuation URLs. */
internal class UserProfileCache(
    private val ttlMillis: Long = PROFILE_CACHE_TTL_MILLIS,
    private val maxProfiles: Int = 8,
    private val maxItems: Int = 2_000,
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
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

    private var context: Context? = null
    private val entries = LinkedHashMap<Long, Entry>(8, 0.75f, true)

    @Synchronized
    fun get(
        userId: Long,
        settings: AppSettings,
    ): CachedUserProfile? {
        useContext(settings)
        val entry = entries[userId] ?: return null
        if (clock() - entry.fetchedAt >= ttlMillis) {
            entries.remove(userId)
        }
        return entries[userId]?.content
    }

    @Synchronized
    fun put(state: IllustiaUiState) {
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
            while (entries.size > maxProfiles || entries.values.sumOf { it.content.works.size + it.content.bookmarks.size } > maxItems) {
                entries.remove(entries.keys.first())
            }
        }
    }

    @Synchronized
    fun updateIllust(updated: Illust) {
        entries.replaceAll { _, entry ->
            entry.copy(
                content =
                    entry.content.copy(
                        works = entry.content.works.replaceIllustIfPresent(updated),
                        bookmarks = entry.content.bookmarks.replaceIllustIfPresent(updated),
                    ),
            )
        }
    }

    @Synchronized
    fun updateUser(updated: UserProfile) {
        entries[updated.id]?.let { entry -> entries[updated.id] = entry.copy(content = entry.content.copy(user = updated)) }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        context = null
    }

    private fun useContext(settings: AppSettings) {
        val next = Context(settings.refreshToken, settings.toMuteFilter(), settings.allowR18, settings.allowR18G, settings.hideAiWorks)
        if (context != next) {
            entries.clear()
            context = next
        }
    }
}
