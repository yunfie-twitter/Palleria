package com.yunfie.illustia.ui.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommandPaletteTest {
    private val labels = PaletteAction.entries.associateWith { it.name }

    @Test fun filtersCommandsAndOffersSearchForArbitraryText() {
        assertEquals(listOf(PaletteAction.Dark, PaletteAction.Search), matchingPaletteActions("  DARK  ", labels))
        assertEquals(listOf(PaletteAction.Search), matchingPaletteActions("作品の検索語", labels))
    }

    @Test fun filtersCommandsByKeywords() {
        val syncActions = matchingPaletteActions("sync", labels)
        org.junit.Assert.assertTrue(syncActions.contains(PaletteAction.PallaSyncDevices))
        org.junit.Assert.assertTrue(syncActions.contains(PaletteAction.PallaSyncSync))
        org.junit.Assert.assertTrue(syncActions.contains(PaletteAction.RefreshFeed))

        val aiActions = matchingPaletteActions("ai", labels)
        org.junit.Assert.assertTrue(aiActions.contains(PaletteAction.ToggleAi))

        val dlActions = matchingPaletteActions("dl", labels)
        org.junit.Assert.assertTrue(dlActions.contains(PaletteAction.DownloadQueue))

        val historyActions = matchingPaletteActions("rireki", labels)
        org.junit.Assert.assertTrue(historyActions.contains(PaletteAction.ViewHistory))
    }

    @Test fun emptyQueryListsCommandsWithoutEmptySearch() {
        assertFalse(matchingPaletteActions("  ", labels).contains(PaletteAction.Search))
        assertEquals(PaletteAction.entries.size - 1, matchingPaletteActions("", labels).size)
    }
}
