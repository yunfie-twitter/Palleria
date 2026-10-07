package com.yunfie.illustia.ui.components

import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InteractiveGuideTest :
    FunSpec({
        test("default settings start with interactive guides uncompleted") {
            val settings = AppSettings()
            settings.hasCompletedQuickPeekGuide shouldBe false
            settings.hasCompletedPinchGridGuide shouldBe false
        }

        test("completing quick peek guide updates state independently") {
            val initial = AppSettings()
            val updated = initial.copy(hasCompletedQuickPeekGuide = true)
            updated.hasCompletedQuickPeekGuide shouldBe true
            updated.hasCompletedPinchGridGuide shouldBe false
        }

        test("completing pinch grid guide updates state independently") {
            val initial = AppSettings()
            val updated = initial.copy(hasCompletedPinchGridGuide = true)
            updated.hasCompletedQuickPeekGuide shouldBe false
            updated.hasCompletedPinchGridGuide shouldBe true
        }

        test("dismissing interactive guides marks both as completed") {
            val initial = AppSettings()
            val dismissed =
                initial.copy(
                    hasCompletedQuickPeekGuide = true,
                    hasCompletedPinchGridGuide = true,
                )
            dismissed.hasCompletedQuickPeekGuide shouldBe true
            dismissed.hasCompletedPinchGridGuide shouldBe true
        }
    })
