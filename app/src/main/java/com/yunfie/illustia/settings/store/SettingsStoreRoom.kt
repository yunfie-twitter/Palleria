package com.yunfie.illustia.settings.store

import androidx.datastore.preferences.core.Preferences
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.db.AccountEntity
import com.yunfie.illustia.settings.db.FavoriteTagEntity
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SearchHistoryEntity
import com.yunfie.illustia.settings.db.SettingsDao
import com.yunfie.illustia.settings.db.ViewHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun readRoomSettingsData(
    dao: SettingsDao,
    viewHistoryLimit: Int? = null,
): RoomSettingsData =
    withContext(Dispatchers.IO) {
        RoomSettingsData(
            searchHistory = dao.getSearchHistory(),
            favoriteTags = dao.getFavoriteTags(),
            viewHistory = if (viewHistoryLimit != null) dao.getViewHistory(viewHistoryLimit) else dao.getViewHistory(),
            accounts = dao.getAccounts(),
        )
    }

@Suppress("LongMethod")
internal suspend fun writeRoomSettingsData(
    database: IllustiaDatabase,
    dao: SettingsDao,
    settings: AppSettings,
    baseSettings: AppSettings? = null,
): Unit =
    withContext(Dispatchers.IO) {
        val searchHistoryChanged = baseSettings == null || baseSettings.searchHistory != settings.searchHistory
        val favoriteTagsChanged = baseSettings == null || baseSettings.favoriteTags != settings.favoriteTags
        val viewHistoryChanged = baseSettings == null || baseSettings.viewHistory != settings.viewHistory
        val accountsChanged = baseSettings == null || baseSettings.accounts != settings.accounts

        val historyChanged = searchHistoryChanged || favoriteTagsChanged
        val dataChanged = viewHistoryChanged || accountsChanged

        if (!historyChanged && !dataChanged) {
            return@withContext
        }

        database.runInTransaction(
            Runnable {
                if (searchHistoryChanged) {
                    dao.clearSearchHistory()
                    dao.insertSearchHistory(
                        settings.searchHistory.take(MAX_SEARCH_HISTORY).mapIndexed { index, query ->
                            SearchHistoryEntity(query, index)
                        },
                    )
                }

                if (favoriteTagsChanged) {
                    dao.clearFavoriteTags()
                    dao.insertFavoriteTags(
                        settings.favoriteTags.mapIndexed { index, tag ->
                            FavoriteTagEntity(tag, index)
                        },
                    )
                }

                if (viewHistoryChanged) {
                    dao.clearViewHistory()
                    dao.insertViewHistory(
                        settings.viewHistory.take(MAX_VIEW_HISTORY).mapIndexed { index, illust ->
                            ViewHistoryEntity(
                                illust.id,
                                illust.title,
                                illust.artistName,
                                illust.imageUrl,
                                illust.pageCount,
                                illust.type,
                                index,
                                illust.isBookmarked,
                                illust.xRestrict,
                                encodeStringList(illust.tags),
                                illust.illustAiType,
                            )
                        },
                    )
                }

                if (accountsChanged) {
                    dao.clearAccounts()
                    dao.insertAccounts(
                        settings.accounts.mapIndexed { index, account ->
                            AccountEntity(
                                account.userId,
                                account.name,
                                account.account,
                                account.profileImageUrl,
                                index,
                            )
                        },
                    )
                }
            },
        )
    }

/** Writes only PallaSync-owned Room collections, leaving accounts and other data untouched. */
internal suspend fun writeSyncedRoomSettingsData(
    database: IllustiaDatabase,
    dao: SettingsDao,
    synced: SyncedCollectionsSnapshot,
): Unit =
    withContext(Dispatchers.IO) {
        database.runInTransaction {
            dao.clearSearchHistory()
            dao.insertSearchHistory(
                synced.searchHistory.take(MAX_SEARCH_HISTORY).mapIndexed { index, query ->
                    SearchHistoryEntity(query, index)
                },
            )

            dao.clearFavoriteTags()
            dao.insertFavoriteTags(
                synced.favoriteTags.mapIndexed { index, tag -> FavoriteTagEntity(tag, index) },
            )

            dao.clearViewHistory()
            dao.insertViewHistory(
                synced.viewHistory.take(MAX_VIEW_HISTORY).mapIndexed { index, illust ->
                    ViewHistoryEntity(
                        illust.id,
                        illust.title,
                        illust.artistName,
                        illust.imageUrl,
                        illust.pageCount,
                        illust.type,
                        index,
                        illust.isBookmarked,
                        illust.xRestrict,
                        encodeStringList(illust.tags),
                        illust.illustAiType,
                    )
                },
            )
        }
    }

internal fun savedIllustStorageBytes(directory: File): Long {
    if (!directory.exists()) return 0L
    return directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
