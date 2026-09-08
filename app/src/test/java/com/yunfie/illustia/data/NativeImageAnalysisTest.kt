package com.yunfie.illustia.data

import android.graphics.Bitmap
import android.graphics.Color
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NativeImageAnalysisTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            val rustTargetDir = File("../rust/target/release").canonicalFile
            if (rustTargetDir.exists()) {
                System.setProperty("jna.library.path", rustTargetDir.absolutePath)
            }
        }
    }

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
