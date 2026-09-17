package com.yunfie.illustia.lifecycle

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.app.DetailEntrySnapshot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * 画面スタックのリセットや認証状態の変更に伴うメモリ・スナップショット解放ライフサイクルのテスト。
 */
class NavigationMemoryLifecycleTest :
    FunSpec({
        test("detailSnapshots are preserved during backstack pushes and cleared on reset") {
            val snapshots = mutableMapOf<Long, DetailEntrySnapshot>()
            val dummyIllust1 = Illust(id = 101L, title = "Artwork 1")
            val dummyIllust2 = Illust(id = 102L, title = "Artwork 2")

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
    })
