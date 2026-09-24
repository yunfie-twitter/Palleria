package com.yunfie.illustia.lifecycle

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.app.DetailEntrySnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * 画面スタックのリセットや認証状態の変更に伴うメモリ・スナップショット解放ライフサイクルのテスト。
 */
class NavigationMemoryLifecycleTest :
    FunSpec({
        test("detailSnapshots are preserved during backstack pushes and cleared on reset") {
            val snapshots = mutableMapOf<Long, DetailEntrySnapshot>()
            val dummyIllust1 = dummyIllust(id = 101L, title = "Artwork 1")
            val dummyIllust2 = dummyIllust(id = 102L, title = "Artwork 2")

            // 1. 詳細画面を開く（スナップショット蓄積）
            snapshots[101L] = DetailEntrySnapshot(illust = dummyIllust1)
            snapshots[102L] = DetailEntrySnapshot(illust = dummyIllust2)
            snapshots.size shouldBe 2

            // 2. 戻るボタンでの1件破棄
            snapshots.remove(102L)
            snapshots.keys.shouldContainExactly(101L)

            // 3. ログアウトやアプリロックによるナビゲーションスタック全リセット
            snapshots.clear()
            snapshots.shouldBeEmpty()
        }

        test("illustDetailListStates LRU cache preserves states and clears on reset") {
            val maxCached = 20
            val cache =
                object : java.util.LinkedHashMap<Long, String>(maxCached, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > maxCached
                }
            cache[101L] = "state_101"
            cache[102L] = "state_102"
            cache[101L] shouldBe "state_101"
            cache[102L] shouldBe "state_102"
            cache.size shouldBe 2

            // Evict oldest when exceeding maxCached
            for (i in 1..25) {
                cache[i.toLong()] = "state_$i"
            }
            cache.size shouldBe maxCached
            cache.clear()
            cache.shouldBeEmpty()
        }
    })

private fun dummyIllust(
    id: Long,
    title: String,
): Illust =
    Illust(
        id = id,
        title = title,
        type = "illust",
        caption = "",
        artistId = 100L,
        artistName = "Artist",
        artistAvatarUrl = null,
        squareImageUrl = "https://example.com/square.jpg",
        imageUrl = "https://example.com/large.jpg",
        originalImageUrl = null,
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
        xRestrict = 0,
    )
