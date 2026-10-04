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

    @Test fun emptyQueryListsCommandsWithoutEmptySearch() {
        assertFalse(matchingPaletteActions("  ", labels).contains(PaletteAction.Search))
        assertEquals(PaletteAction.entries.size - 1, matchingPaletteActions("", labels).size)
    }
}
