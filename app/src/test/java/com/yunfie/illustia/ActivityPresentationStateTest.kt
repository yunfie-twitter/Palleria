package com.yunfie.illustia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityPresentationStateTest {
    @Test fun ignoresDownloadSearchDraftAndHistoryUpdates() {
        val state = IllustiaUiState()
        val changed =
            state.copy(
                activeDownloads = 3,
                searchDraft = "typing",
                settings = state.settings.copy(searchHistory = listOf("query")),
            )
        assertEquals(state.activityPresentation(), changed.activityPresentation())
    }

    @Test fun observesThemeLockAndWindowTitleChanges() {
        val state = IllustiaUiState()
        assertNotEquals(state.activityPresentation(), state.copy(appLocked = true).activityPresentation())
        assertNotEquals(state.activityPresentation(), state.copy(activeSearchWord = "query").activityPresentation())
        assertNotEquals(state.activityPresentation(), state.copy(settings = state.settings.copy(themeMode = "dark")).activityPresentation())
    }
}
