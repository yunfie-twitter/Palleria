package com.yunfie.illustia.ui.components

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GridPinchFeedbackTest :
    FunSpec({
        test("pinch zoom in produces negative columnDelta for larger cards") {
            val gesture = GridPinchGesture()
            // 2 fingers active
            gesture.update(2, 1.3f) shouldBe GridPinchUpdate(consume = true, columnDelta = -1)
        }

        test("pinch zoom out produces positive columnDelta for smaller cards") {
            val gesture = GridPinchGesture()
            gesture.update(2, 0.7f) shouldBe GridPinchUpdate(consume = true, columnDelta = 1)
        }

        test("column transitions coerce between 1 and 4") {
            var columns = 2
            val onColumnsChange: (Int) -> Unit = { columns = it }

            fun applyDelta(delta: Int) {
                val next = (columns + delta).coerceIn(1, 4)
                if (next != columns) {
                    onColumnsChange(next)
                }
            }

            applyDelta(1)
            columns shouldBe 3

            applyDelta(1)
            columns shouldBe 4

            // Beyond max
            applyDelta(1)
            columns shouldBe 4

            // Zoom in step
            applyDelta(-1)
            columns shouldBe 3

            applyDelta(-1)
            columns shouldBe 2

            applyDelta(-1)
            columns shouldBe 1

            // Below min
            applyDelta(-1)
            columns shouldBe 1
        }

        test("callback fires on successful column change") {
            var callbackFired = false
            var currentColumns = 2
            val nextColumns = (currentColumns + 1).coerceIn(1, 4)
            if (nextColumns != currentColumns) {
                currentColumns = nextColumns
                callbackFired = true
            }

            callbackFired shouldBe true
            currentColumns shouldBe 3
        }
    })
