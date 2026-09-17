package com.yunfie.illustia.lifecycle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * うごイラ再生中のフレームキャッシング・ライフサイクル（スライディングウィンドウ）のテスト。
 * 再生インデックスの進行に伴い、必要なフレームのみが保持され、遠いフレームが正しくパージされることを検証する。
 */
class UgoiraSlidingWindowLifecycleTest :
    FunSpec({
        val keepBehind = 4
        val prefetchAhead = 12

        fun computeRetainedFrames(
            currentFrame: Int,
            totalFrames: Int,
        ): Set<Int> =
            (-keepBehind..prefetchAhead)
                .map { (currentFrame + it + totalFrames) % totalFrames }
                .toSet()

        test("retained window covers both past and future frames around playback position") {
            val totalFrames = 50
            val currentFrame = 20

            val retained = computeRetainedFrames(currentFrame, totalFrames)

            // 再生位置とその前後が含まれる
            retained shouldContain 20
            retained shouldContain 16 // current - keepBehind
            retained shouldContain 32 // current + prefetchAhead

            // 範囲外は含まれない
            retained shouldNotContain 10
            retained shouldNotContain 40
            retained.size shouldBe (keepBehind + prefetchAhead + 1)
        }

        test("window correctly wraps around the loop boundary at the start") {
            val totalFrames = 50
            val currentFrame = 2

            val retained = computeRetainedFrames(currentFrame, totalFrames)

            retained shouldContain 2
            retained shouldContain 1
            retained shouldContain 0
            // 後方ラップ（50 - 2 = 48, 50 - 1 = 49）
            retained shouldContain 49
            retained shouldContain 48
            retained shouldContain 14 // 2 + 12
        }

        test("window correctly wraps around the loop boundary at the end") {
            val totalFrames = 50
            val currentFrame = 48

            val retained = computeRetainedFrames(currentFrame, totalFrames)

            retained shouldContain 48
            retained shouldContain 49
            // 前方ラップ（0, 1, 2...）
            retained shouldContain 0
            retained shouldContain 1
            retained shouldContain 10 // (48 + 12) % 50
        }

        test("purging old frames preserves only currently needed window") {
            val inMemoryBitmaps = (0 until 50).associateWith { "bitmap_$it" }.toMutableMap()
            val needed = computeRetainedFrames(currentFrame = 25, totalFrames = 50)

            inMemoryBitmaps.keys.retainAll(needed)

            inMemoryBitmaps.size shouldBe needed.size
            inMemoryBitmaps.containsKey(25) shouldBe true
            inMemoryBitmaps.containsKey(0) shouldBe false
        }
    })
