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
            gesture.update(2, -1f) shouldBe GridPinchUpdate(true)
            gesture.update(2, Float.POSITIVE_INFINITY) shouldBe GridPinchUpdate(true)
            gesture.update(2, 1.3f) shouldBe GridPinchUpdate(true, -1)
        }
        test("continuous small zoom in events accumulate and trigger column reduction") {
            val gesture = GridPinchGesture()
            // 1.05^6 ~= 1.34 > 1.28
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 1.05f) shouldBe GridPinchUpdate(true, -1)
        }
        test("continuous small zoom out events accumulate and trigger column addition") {
            val gesture = GridPinchGesture()
            // 0.95^5 ~= 0.773 < 0.78
            gesture.update(2, 0.95f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 0.95f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 0.95f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 0.95f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(2, 0.95f) shouldBe GridPinchUpdate(true, 1)
        }
        test("three or more fingers trigger claim and work properly") {
            val gesture = GridPinchGesture()
            gesture.update(3, 1f) shouldBe GridPinchUpdate(true, 0)
            gesture.update(4, 1.3f) shouldBe GridPinchUpdate(true, -1)
            // One finger lifted -> 3 fingers
            gesture.update(3, 1f) shouldBe GridPinchUpdate(true, 0)
            // Lift all fingers
            gesture.update(0, 1f) shouldBe GridPinchUpdate(true, 0)
            // Next 1-finger touch is unowned
            gesture.update(1, 1f) shouldBe GridPinchUpdate(false, 0)
        }
        test("multiple threshold triggers without lifting fingers") {
            val gesture = GridPinchGesture()
            gesture.update(2, 1.3f) shouldBe GridPinchUpdate(true, -1)
            gesture.update(2, 1.3f) shouldBe GridPinchUpdate(true, -1)
            gesture.update(2, 0.75f) shouldBe GridPinchUpdate(true, 1)
        }
    })
