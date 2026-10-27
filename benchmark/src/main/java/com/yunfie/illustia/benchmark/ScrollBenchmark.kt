package com.yunfie.illustia.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * スクロール時のフレーム時間を測る。StartupBenchmark は起動しか見ていないため、一覧を動かした
 * ときのジャンクはこれまで一度も計測されていなかった。
 *
 * フィードが実際に描画されていることを確認してから測り始める。初回起動直後はオンボーディングが
 * 出ていることがあり、そのままドラッグするとオンボーディングの操作を測ってしまうため。
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun feedScrollWithoutProfile() = measureFeedScroll(CompilationMode.None())

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

                // シェルが出ていない（未ログイン・オンボーディング中）なら計測しない。
                // ここで落とさずスキップするのは、認証状態は端末や CI の都合で変わり、
                // ベンチマークの失敗と区別できないため。
                assumeTrue("App shell is not reachable; skipped instead of measuring onboarding.", appShellReady())

                // フィードの一覧が描画されるまで待つ。取得に失敗しても空の一覧は出るので、
                // 長く待ち続けはしない。
                device.wait(Until.hasObject(By.scrollable(true)), FEED_TIMEOUT_MS)
            },
            measureBlock = {
                repeat(SCROLL_SWIPES) { scrollUp() }
            },
        )
    }

    /**
     * ボトムナビゲーションのホームが現れるまで待つ。オンボーディングにはタブが無いので、
     * これが見えた時点でアプリ本体のシェルが描画されていると判断できる。
     */
    private fun MacrobenchmarkScope.appShellReady(): Boolean {
        device.wait(Until.hasObject(By.text(HOME_TAB_LABEL)), FEED_TIMEOUT_MS)
        return device.hasObject(By.text(HOME_TAB_LABEL))
    }

    /** 画面中央を上方向へドラッグして一覧を送る。 */
    private fun MacrobenchmarkScope.scrollUp() {
        val centerX = device.displayWidth / 2
        val centerY = device.displayHeight / 2
        device.swipe(centerX, centerY + SWIPE_OFFSET, centerX, centerY - SWIPE_OFFSET, SWIPE_STEPS)
        device.waitForIdle()
    }

    private companion object {
        /** `nav_home` の既定ロケール（日本語）表記。 */
        const val HOME_TAB_LABEL = "ホーム"
        const val SCROLL_SWIPES = 4
        const val SWIPE_OFFSET = 400
        const val SWIPE_STEPS = 20
        const val FEED_TIMEOUT_MS = 10_000L
    }
}
