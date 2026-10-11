package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe

class PallaSyncSettingsEventsTest :
    StringSpec({
        "unrelated device-local settings do not create sync records" {
            val previous = AppSettings(themeMode = "light", favoriteTags = listOf("kept"))
            val next = previous.copy(themeMode = "dark", imageCacheSizeMb = 500)

            buildSettingsSyncEvents(previous, next) shouldBe emptyList()
        }

        "all four domains emit item-level upserts and deletes" {
            val previous =
                AppSettings(
                    favoriteTags = listOf("old"),
                    searchHistory = listOf("old query"),
                    mutedTags = listOf("old mute"),
                    mutedUsers = listOf(1L),
                    mutedIllusts = listOf(2L),
                    seenFeedIllusts = listOf(3L),
                    viewHistory = listOf(syncIllust(4L, "Old")),
                )
            val next =
                AppSettings(
                    favoriteTags = listOf("new"),
                    searchHistory = listOf("new query"),
                    mutedTags = listOf("new mute"),
                    mutedUsers = listOf(11L),
                    mutedIllusts = listOf(12L),
                    seenFeedIllusts = listOf(13L),
                    viewHistory = listOf(syncIllust(14L, "New")),
                )

            val operations =
                buildSettingsSyncEvents(previous, next)
                    .map { Triple(it.schema, it.entityId, it.operation) }

            operations.shouldContainAll(
                Triple(FAVORITE_TAG_SCHEMA_V3, "old", SYNC_OPERATION_DELETE),
                Triple(FAVORITE_TAG_SCHEMA_V3, "new", SYNC_OPERATION_UPSERT),
                Triple(SEARCH_HISTORY_SCHEMA_V3, "old query", SYNC_OPERATION_DELETE),
                Triple(SEARCH_HISTORY_SCHEMA_V3, "new query", SYNC_OPERATION_UPSERT),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "tag:old mute", SYNC_OPERATION_DELETE),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "tag:new mute", SYNC_OPERATION_UPSERT),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "user:1", SYNC_OPERATION_DELETE),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "user:11", SYNC_OPERATION_UPSERT),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "illust:2", SYNC_OPERATION_DELETE),
                Triple(MUTE_SETTINGS_SCHEMA_V3, "illust:12", SYNC_OPERATION_UPSERT),
                Triple(VIEW_HISTORY_SCHEMA_V3, "seen:3", SYNC_OPERATION_DELETE),
                Triple(VIEW_HISTORY_SCHEMA_V3, "seen:13", SYNC_OPERATION_UPSERT),
                Triple(VIEW_HISTORY_SCHEMA_V3, "viewed:4", SYNC_OPERATION_DELETE),
                Triple(VIEW_HISTORY_SCHEMA_V3, "viewed:14", SYNC_OPERATION_UPSERT),
            )
        }

        "history reorder emits only the item that must move to the front" {
            val previous = AppSettings(searchHistory = listOf("alpha", "beta", "gamma"))
            val next = previous.copy(searchHistory = listOf("gamma", "alpha", "beta"))

            val historyEvents =
                buildSettingsSyncEvents(previous, next)
                    .filter { it.schema == SEARCH_HISTORY_SCHEMA_V3 }

            historyEvents.map { it.operation } shouldBe listOf(SYNC_OPERATION_UPSERT)
            historyEvents.map { it.entityId } shouldBe listOf("gamma")
        }

        "new search and viewed item do not rewrite unchanged history items" {
            val oldView = syncIllust(40L, "Old")
            val newView = syncIllust(41L, "New")
            val previous =
                AppSettings(
                    searchHistory = listOf("alpha", "beta"),
                    viewHistory = listOf(oldView),
                )
            val next =
                previous.copy(
                    searchHistory = listOf("new", "alpha", "beta"),
                    viewHistory = listOf(newView, oldView),
                )

            buildSettingsSyncEvents(previous, next)
                .map { Triple(it.schema, it.entityId, it.operation) } shouldBe
                listOf(
                    Triple(SEARCH_HISTORY_SCHEMA_V3, "new", SYNC_OPERATION_UPSERT),
                    Triple(VIEW_HISTORY_SCHEMA_V3, "viewed:41", SYNC_OPERATION_UPSERT),
                )
        }

        "seen history uses reverse initial upserts and a minimal reorder upsert" {
            val initial =
                buildSettingsSyncEvents(
                    SyncedCollectionsSnapshot(),
                    SyncedCollectionsSnapshot(seenFeedIllusts = listOf(3L, 2L, 1L)),
                ).filter { it.entityId.startsWith("seen:") }
            initial.map { it.entityId } shouldBe listOf("seen:1", "seen:2", "seen:3")

            val reordered =
                buildSettingsSyncEvents(
                    SyncedCollectionsSnapshot(seenFeedIllusts = listOf(3L, 2L, 1L)),
                    SyncedCollectionsSnapshot(seenFeedIllusts = listOf(2L, 3L, 1L)),
                ).filter { it.entityId.startsWith("seen:") }
            reordered.map { it.entityId } shouldBe listOf("seen:2")
        }
    })

private fun syncIllust(
    id: Long,
    title: String,
): Illust =
    Illust(
        id = id,
        title = title,
        type = "illust",
        caption = "",
        artistId = 0L,
        artistName = "Artist",
        artistAvatarUrl = null,
        squareImageUrl = "image",
        imageUrl = "image",
        originalImageUrl = null,
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
    )
