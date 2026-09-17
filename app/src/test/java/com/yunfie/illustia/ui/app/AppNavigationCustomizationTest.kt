package com.yunfie.illustia.ui.app

import com.yunfie.illustia.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationCustomizationTest {
    @Test
    fun `navigation order and visibility follow experimental settings`() {
        val settings =
            AppSettings(
                navigationOrder = listOf("ranking", "home", "more", "search", "bookmarks"),
                hiddenNavigationTabs = listOf("more"),
            )

        assertEquals(
            listOf(AppTab.Ranking, AppTab.Home, AppTab.More, AppTab.Search, AppTab.Bookmarks),
            mainTabs(settings),
        )
        assertEquals(
            listOf(AppTab.Ranking, AppTab.Home, AppTab.Search, AppTab.Bookmarks),
            visibleTabs(settings),
        )
    }

    @Test
    fun `shorts feed keeps the customized search position`() {
        val settings =
            AppSettings(
                shortsFeedEnabled = true,
                navigationOrder = listOf("ranking", "search", "home", "bookmarks", "more"),
            )

        assertEquals(AppTab.ShortsFeed, mainTabs(settings)[1])
    }

    @Test
    fun `at least two navigation destinations stay reachable`() {
        val settings =
            AppSettings(
                hiddenNavigationTabs = listOf("home", "search", "bookmarks", "ranking", "more"),
            )

        assertEquals(2, visibleTabs(settings).size)
    }

    @Test
    fun `hideHomeNovelButton is true by default`() {
        assertEquals(true, AppSettings().hideHomeNovelButton)
    }
}
