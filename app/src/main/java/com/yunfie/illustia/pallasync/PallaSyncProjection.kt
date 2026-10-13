package com.yunfie.illustia.pallasync

import android.content.Context
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.ProtocolDao
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** §16.5: a bounded display cache derived from authoritative registers. No archive deletion. */
internal class PallaSyncProjection(
    context: Context,
) {
    private val collections = SettingsSyncedCollectionsStore(context.applicationContext)
    private val json = Json

    suspend fun publish(
        chain: String,
        store: PallaSyncLocalStore,
    ) {
        // Read one index revision; settings publication does not hold the Room transaction.
        val (snapshot, pending) =
            store.transaction {
                val dao = store.protocolDao()
                read(chain, dao) to
                    dao.pendingIntentCollections(
                        PallaSyncLocalIntents.journalId(chain),
                        listOf(FAVORITE_TAG_SCHEMA_V3, SEARCH_HISTORY_SCHEMA_V3, MUTE_SETTINGS_SCHEMA_V3, VIEW_HISTORY_SCHEMA_V3),
                    )
            }
        // Paused unsigned edits remain a labelled local preview. Verified remote registers still
        // commit in Room; publication must not overwrite the user's unsent editing surface (§16.4).
        collections.update { current ->
            snapshot.copy(
                favoriteTags = if (FAVORITE_TAG_SCHEMA_V3 in pending) current.favoriteTags else snapshot.favoriteTags,
                searchHistory = if (SEARCH_HISTORY_SCHEMA_V3 in pending) current.searchHistory else snapshot.searchHistory,
                mutedTags = if (MUTE_SETTINGS_SCHEMA_V3 in pending) current.mutedTags else snapshot.mutedTags,
                mutedUsers = if (MUTE_SETTINGS_SCHEMA_V3 in pending) current.mutedUsers else snapshot.mutedUsers,
                mutedIllusts = if (MUTE_SETTINGS_SCHEMA_V3 in pending) current.mutedIllusts else snapshot.mutedIllusts,
                seenFeedIllusts = if (VIEW_HISTORY_SCHEMA_V3 in pending) current.seenFeedIllusts else snapshot.seenFeedIllusts,
                viewHistory = if (VIEW_HISTORY_SCHEMA_V3 in pending) current.viewHistory else snapshot.viewHistory,
            )
        }
    }

    private suspend fun read(
        chain: String,
        dao: ProtocolDao,
    ): SyncedCollectionsSnapshot {
        val favorite = dao.orderedEntities(chain, FAVORITE_TAG_SCHEMA_V3, PALLASYNC_PAGE_SIZE)
        val muted = dao.orderedEntities(chain, MUTE_SETTINGS_SCHEMA_V3, PALLASYNC_PAGE_SIZE)
        val searches = history(dao, chain, SEARCH_HISTORY_SCHEMA_V3, "all")
        val seen = history(dao, chain, VIEW_HISTORY_SCHEMA_V3, "seen")
        val viewed = history(dao, chain, VIEW_HISTORY_SCHEMA_V3, "viewed")
        return SyncedCollectionsSnapshot(
            favoriteTags = favorite.map { json.decodeFromString<FavoriteTagBody>(it.bodyJson).tag },
            searchHistory = searches.map { json.decodeFromString<SearchHistoryBody>(it.bodyJson).query },
            mutedTags = muteValues(muted, "tag:"),
            mutedUsers = muteValues(muted, "user:").map(String::toLong),
            mutedIllusts = muteValues(muted, "illust:").map(String::toLong),
            seenFeedIllusts = seen.map { json.decodeFromString<SeenIllustBody>(it.bodyJson).id },
            viewHistory = viewed.map { json.decodeFromString<ViewedIllustBody>(it.bodyJson).illust() },
        )
    }

    private fun muteValues(
        rows: List<EntityRegisterEntity>,
        prefix: String,
    ): List<String> = rows.filter { it.entityId.startsWith(prefix) }.map { json.decodeFromString<MuteSettingBody>(it.bodyJson).value }

    private suspend fun history(
        dao: ProtocolDao,
        chain: String,
        collection: String,
        scope: String,
    ): List<EntityRegisterEntity> {
        val state = dao.historyProjection(chain, collection, scope)
        val count = minOf(state.projectionLimit, PALLASYNC_PAGE_SIZE.toLong()).toInt()
        return dao
            .historyPage(chain, collection, scope, null, "", count)
            .map { row -> checkNotNull(dao.entity(chain, collection, row.entityId)) }
    }
}

// Presentation placeholders only. The authoritative winner remains the exact body
// in Room; these values never supply defaults to an incoming wire body (§8.4.2).
internal fun ViewedIllustBody.illust() =
    Illust(
        id = id,
        title = title.orEmpty(),
        type = type ?: "illust",
        caption = "",
        artistId = 0,
        artistName = artistName.orEmpty(),
        artistAvatarUrl = null,
        squareImageUrl = "",
        mediumImageUrl = imageUrl.orEmpty(),
        imageUrl = imageUrl.orEmpty(),
        originalImageUrl = null,
        tags = tags.orEmpty(),
        pageCount = pageCount ?: 1,
        isBookmarked = isBookmarked ?: false,
        xRestrict = xRestrict ?: 0,
        illustAiType = illustAiType ?: 0,
        viewHistoryBody = Json.encodeToJsonElement(this).jsonObject,
    )

@Serializable
internal data class PallaSyncRetentionPolicy(
    val scope: String,
    val limit: Long?,
    @SerialName("stop_on_safety_limit") val stopOnSafetyLimit: Boolean,
)
