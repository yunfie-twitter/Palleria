package com.yunfie.illustia.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NativeImageAnalysisTest {
    @Test
    fun testDominantColorWithNormalBitmap() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                bitmap.setPixel(x, y, Color.RED)
            }
        }
        val dominant = NativeImageAnalysis.dominantColor(bitmap)
        dominant shouldNotBe 0
    }

    @Test
    fun testShouldUseDarkHeaderIconsWithWhiteBitmap() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) {
            for (y in 0 until 40) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }
        NativeImageAnalysis.shouldUseDarkHeaderIcons(bitmap) shouldBe true
    }

    @Test
    fun testShouldUseDarkHeaderIconsWithBlackBitmap() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) {
            for (y in 0 until 40) {
                bitmap.setPixel(x, y, Color.BLACK)
            }
        }
        NativeImageAnalysis.shouldUseDarkHeaderIcons(bitmap) shouldBe false
    }

    @Test
    fun testHandlesRecycledBitmapGracefully() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        NativeImageAnalysis.dominantColor(bitmap) shouldBe Color.BLACK
        NativeImageAnalysis.shouldUseDarkHeaderIcons(bitmap) shouldBe false
    }
}
