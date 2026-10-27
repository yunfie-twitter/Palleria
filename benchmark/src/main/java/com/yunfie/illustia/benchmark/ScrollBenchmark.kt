package com.yunfie.illustia.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * スクロール時のフレーム時間を測る。StartupBenchmark は起動しか見ていないため、一覧を動かした
 * ときのジャンクはこれまで一度も計測されていなかった。
 *
 * 特定の要素を探してから操作する方式は、フィードが空だったり言語設定が違うと失敗する。ここでは
 * 画面の中央をドラッグするだけにして、対象がぶれないようにしている。
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun feedScrollWithoutProfile() =
        measureFeedScroll(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable))

    @Test
    fun feedScrollWithBaselineProfile() =
        measureFeedScroll(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun measureFeedScroll(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 10,
            setupBlock = {
                launchFromHome()
                // 最初のフレームが揃うまで待つ。取得失敗時も空の一覧は描画されるので待ち続けない。
                device.waitForIdle()
            },
            measureBlock = {
                repeat(SCROLL_SWIPES) { scrollUp() }
            },
        )
    }

    /** 画面中央を上方向へドラッグして一覧を送る。 */
    private fun MacrobenchmarkScope.scrollUp() {
        val centerX = device.displayWidth / 2
        val centerY = device.displayHeight / 2
        device.swipe(centerX, centerY + SWIPE_OFFSET, centerX, centerY - SWIPE_OFFSET, SWIPE_STEPS)
        device.waitForIdle()
    }

    private companion object {
        const val SCROLL_SWIPES = 4
        const val SWIPE_OFFSET = 400
        const val SWIPE_STEPS = 20
    }
}
