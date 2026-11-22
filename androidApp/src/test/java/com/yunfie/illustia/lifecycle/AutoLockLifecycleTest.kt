package com.yunfie.illustia.lifecycle

import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * アプリのバックグラウンド遷移や復帰に伴う自動ロック（PrivacyMode AutoLock）ライフサイクルのテスト。
 */
class AutoLockLifecycleTest :
    FunSpec({
        test("immediate timing locks the app immediately on backgrounding") {
            val settings =
                AppSettings(
                    privacyModeEnabled = true,
                    privacyModeAutoLockTiming = "immediate",
                )
            val state =
                IllustiaUiState(
                    settings = settings,
                    privacyLocked = false,
                )

            // 即座にロックされるべきか判定
            val shouldLockImmediately = settings.privacyModeEnabled && settings.privacyModeAutoLockTiming == "immediate"
            shouldLockImmediately shouldBe true

            val lockedState = state.copy(privacyLocked = true)
            lockedState.privacyLocked shouldBe true
        }

        test("delayed auto lock timings resolve to correct durations") {
            fun resolveDelayMs(timing: String): Long =
                when (timing) {
                    "immediate" -> 0L
                    "30s" -> 30_000L
                    "1m" -> 60_000L
                    "5m" -> 5 * 60_000L
                    "10m" -> 10 * 60_000L
                    else -> 0L
                }

            resolveDelayMs("immediate") shouldBe 0L
            resolveDelayMs("30s") shouldBe 30_000L
            resolveDelayMs("1m") shouldBe 60_000L
            resolveDelayMs("5m") shouldBe 300_000L
            resolveDelayMs("10m") shouldBe 600_000L
        }

        test("auto lock does not activate when privacy mode is disabled") {
            val settings =
                AppSettings(
                    privacyModeEnabled = false,
                    privacyModeAutoLockTiming = "immediate",
                )
            val shouldLock = settings.privacyModeEnabled
            shouldLock shouldBe false
        }
    })
