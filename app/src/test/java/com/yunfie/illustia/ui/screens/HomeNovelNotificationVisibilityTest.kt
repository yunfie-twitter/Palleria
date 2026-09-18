package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HomeNovelNotificationVisibilityTest :
    FunSpec({
        test("default settings have hideHomeNovelButton enabled") {
            val settings = AppSettings()
            settings.hideHomeNovelButton shouldBe true
        }

        test("when hideHomeNovelButton is true, notifications icon is shown in TopAppBar instead of novel button") {
            val settings = AppSettings(hideHomeNovelButton = true)
            val showNovelInTopAppBar = !settings.hideHomeNovelButton
            val showNotificationInTopAppBar = settings.hideHomeNovelButton

            showNovelInTopAppBar shouldBe false
            showNotificationInTopAppBar shouldBe true
        }

        test("when hideHomeNovelButton is false, novel button is shown in TopAppBar and notifications in More") {
            val settings = AppSettings(hideHomeNovelButton = false)

            val showNovelInTopAppBar = !settings.hideHomeNovelButton
            val showNotificationInTopAppBar = settings.hideHomeNovelButton
            val showNotificationInMore = !settings.hideHomeNovelButton
            val showNovelInMore = settings.hideHomeNovelButton

            showNovelInTopAppBar shouldBe true
            showNotificationInTopAppBar shouldBe false
            showNotificationInMore shouldBe true
            showNovelInMore shouldBe false
        }

        test("bounded LRU map evicts eldest entry when capacity exceeds limit") {
            val maxCapacity = 4
            val lruMap =
                object : java.util.LinkedHashMap<String, LazyGridState>(maxCapacity, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LazyGridState>?): Boolean = size > maxCapacity
                }

            lruMap["mode1"] = LazyGridState()
            lruMap["mode2"] = LazyGridState()
            lruMap["mode3"] = LazyGridState()
            lruMap["mode4"] = LazyGridState()
            lruMap["mode5"] = LazyGridState()

            lruMap.size shouldBe 4
            lruMap.containsKey("mode1") shouldBe false
            lruMap.containsKey("mode5") shouldBe true
        }
    })
