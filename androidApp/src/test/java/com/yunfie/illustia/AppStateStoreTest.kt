package com.yunfie.illustia

import com.yunfie.illustia.settings.AppSettings
import io.kotest.matchers.shouldBe
import org.junit.Test

class AppStateStoreTest {
    @Test
    fun `state updates preserve unrelated slices`() {
        val store =
            AppStateStore(
                IllustiaUiState(
                    settings = AppSettings(themeMode = "dark"),
                    searchDraft = "before",
                    appLocked = true,
                ),
            )
        val securityBefore = store.slices().settingsSecurity

        store.update { it.copy(searchDraft = "after") }

        store.slices().feedSearch.searchDraft shouldBe "after"
        store.slices().settingsSecurity shouldBe securityBefore
        store.state.value.searchDraft shouldBe "after"
    }
}
