package com.yunfie.illustia.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {
    private val labels = PaletteAction.entries.associateWith { it.name }

    @Test fun filtersCommandsAndOffersSearchForArbitraryText() {
        assertEquals(listOf(PaletteAction.Dark, PaletteAction.Search), matchingPaletteActions("  DARK  ", labels))
        assertEquals(listOf(PaletteAction.Search), matchingPaletteActions("作品の検索語", labels))
    }

    @Test fun filtersCommandsByKeywords() {
        val syncActions = matchingPaletteActions("sync", labels)
        assertTrue(syncActions.contains(PaletteAction.PallaSyncDevices))
        assertTrue(syncActions.contains(PaletteAction.PallaSyncSync))
        assertTrue(syncActions.contains(PaletteAction.RefreshFeed))

        val aiActions = matchingPaletteActions("ai", labels)
        assertTrue(aiActions.contains(PaletteAction.ToggleAi))

        val dlActions = matchingPaletteActions("dl", labels)
        assertTrue(dlActions.contains(PaletteAction.DownloadQueue))

        val historyActions = matchingPaletteActions("rireki", labels)
        assertTrue(historyActions.contains(PaletteAction.ViewHistory))
    }

    @Test fun matchesExtendedPaletteActionKeywords() {
        // SwitchAccount
        assertTrue(matchingPaletteActions("account", labels).contains(PaletteAction.SwitchAccount))
        assertTrue(matchingPaletteActions("switch user", labels).contains(PaletteAction.SwitchAccount))
        assertTrue(matchingPaletteActions("login", labels).contains(PaletteAction.SwitchAccount))

        // ClearCache
        assertTrue(matchingPaletteActions("clear cache", labels).contains(PaletteAction.ClearCache))
        assertTrue(matchingPaletteActions("purge", labels).contains(PaletteAction.ClearCache))
        assertTrue(matchingPaletteActions("gc", labels).contains(PaletteAction.ClearCache))

        // RefreshFeed
        assertTrue(matchingPaletteActions("reload", labels).contains(PaletteAction.RefreshFeed))

        // OpenFromClipboard
        assertTrue(matchingPaletteActions("pixiv.net", labels).contains(PaletteAction.OpenFromClipboard))
        assertTrue(matchingPaletteActions("jump", labels).contains(PaletteAction.OpenFromClipboard))

        // ToggleAi
        assertTrue(matchingPaletteActions("toggle ai", labels).contains(PaletteAction.ToggleAi))
        assertTrue(matchingPaletteActions("filter", labels).contains(PaletteAction.ToggleAi))

        // ToggleR18
        assertTrue(matchingPaletteActions("r18", labels).contains(PaletteAction.ToggleR18))
        assertTrue(matchingPaletteActions("nsfw", labels).contains(PaletteAction.ToggleR18))
        assertTrue(matchingPaletteActions("safe", labels).contains(PaletteAction.ToggleR18))

        // ToggleGridColumns
        assertTrue(matchingPaletteActions("grid", labels).contains(PaletteAction.ToggleGridColumns))
        assertTrue(matchingPaletteActions("columns", labels).contains(PaletteAction.ToggleGridColumns))
        assertTrue(matchingPaletteActions("layout", labels).contains(PaletteAction.ToggleGridColumns))

        // ToggleImageQuality
        assertTrue(matchingPaletteActions("quality", labels).contains(PaletteAction.ToggleImageQuality))
        assertTrue(matchingPaletteActions("hd", labels).contains(PaletteAction.ToggleImageQuality))
        assertTrue(matchingPaletteActions("original", labels).contains(PaletteAction.ToggleImageQuality))

        // ToggleUgoiraAutoPlay
        assertTrue(matchingPaletteActions("ugoira", labels).contains(PaletteAction.ToggleUgoiraAutoPlay))
        assertTrue(matchingPaletteActions("autoplay", labels).contains(PaletteAction.ToggleUgoiraAutoPlay))
        assertTrue(matchingPaletteActions("gif", labels).contains(PaletteAction.ToggleUgoiraAutoPlay))

        // FavoriteTags
        assertTrue(matchingPaletteActions("favtags", labels).contains(PaletteAction.FavoriteTags))

        // OfflineLibrary
        assertTrue(matchingPaletteActions("offline", labels).contains(PaletteAction.OfflineLibrary))
        assertTrue(matchingPaletteActions("hozon", labels).contains(PaletteAction.OfflineLibrary))

        // Notifications
        assertTrue(matchingPaletteActions("notifications", labels).contains(PaletteAction.Notifications))
        assertTrue(matchingPaletteActions("tsuuchi", labels).contains(PaletteAction.Notifications))

        // MuteSettings
        assertTrue(matchingPaletteActions("mute", labels).contains(PaletteAction.MuteSettings))
        assertTrue(matchingPaletteActions("blocked", labels).contains(PaletteAction.MuteSettings))
        assertTrue(matchingPaletteActions("ng", labels).contains(PaletteAction.MuteSettings))

        // AppData
        assertTrue(matchingPaletteActions("storage", labels).contains(PaletteAction.AppData))
        assertTrue(matchingPaletteActions("appdata", labels).contains(PaletteAction.AppData))

        // ShortsFeed
        assertTrue(matchingPaletteActions("shorts", labels).contains(PaletteAction.ShortsFeed))
        assertTrue(matchingPaletteActions("tiktok", labels).contains(PaletteAction.ShortsFeed))
        assertTrue(matchingPaletteActions("reel", labels).contains(PaletteAction.ShortsFeed))
    }

    @Test fun emptyQueryListsCommandsWithoutEmptySearch() {
        assertFalse(matchingPaletteActions("  ", labels).contains(PaletteAction.Search))
        assertEquals(PaletteAction.entries.size - 1, matchingPaletteActions("", labels).size)
    }

    @Test fun allPaletteActionsHaveValidResources() {
        PaletteAction.entries.forEach { action ->
            assertTrue(action.title != 0)
            assertTrue(action.categoryRes != 0)
        }
    }
}
