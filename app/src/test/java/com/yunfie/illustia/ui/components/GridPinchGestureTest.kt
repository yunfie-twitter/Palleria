package com.yunfie.illustia.ui.components

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GridPinchGestureTest :
    FunSpec({
        test("second finger cancels long press before any zoom threshold") {
            val gesture = GridPinchGesture()
            gesture.update(1, 1f) shouldBe GridPinchUpdate(false)
            gesture.update(2, 1f) shouldBe GridPinchUpdate(true)
            repeat(20) { gesture.update(2, 1.001f) shouldBe GridPinchUpdate(true) }
        }
        test("pinch keeps ownership until all fingers lift and next single touch is normal") {
            val gesture = GridPinchGesture()
            gesture.update(2, 1f) shouldBe GridPinchUpdate(true)
            gesture.update(1, 1f) shouldBe GridPinchUpdate(true)
            gesture.update(0, 1f) shouldBe GridPinchUpdate(true)
            gesture.update(1, 1f) shouldBe GridPinchUpdate(false)
            gesture.update(0, 1f) shouldBe GridPinchUpdate(false)
        }
        test("column thresholds accumulate and reset while retaining ownership") {
            val gesture = GridPinchGesture()
            gesture.update(2, 1.2f) shouldBe GridPinchUpdate(true)
            gesture.update(2, 1.1f) shouldBe GridPinchUpdate(true, -1)
            gesture.update(2, 1f) shouldBe GridPinchUpdate(true)
            gesture.update(2, 0.7f) shouldBe GridPinchUpdate(true, 1)
        }
        test("invalid distances cannot poison subsequent zoom gestures") {
            val gesture = GridPinchGesture()
            gesture.update(2, Float.NaN) shouldBe GridPinchUpdate(true)
            gesture.update(2, 0f) shouldBe GridPinchUpdate(true)
            gesture.update(2, 1.3f) shouldBe GridPinchUpdate(true, -1)
        }
    })
