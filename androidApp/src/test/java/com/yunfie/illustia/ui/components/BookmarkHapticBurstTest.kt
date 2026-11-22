package com.yunfie.illustia.ui.components

import android.content.Context
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.settings.AppHapticMode
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkHapticBurstTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `AppHapticEffect BookmarkBurst enum value exists`() {
        val effects = AppHapticEffect.entries
        effects.contains(AppHapticEffect.BookmarkBurst) shouldBe true
    }

    @Test
    fun `performAppHapticFeedback does nothing when mode is Off`() {
        var hapticTriggered = false
        val dummyFeedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    hapticTriggered = true
                }
            }

        performAppHapticFeedback(
            context = context,
            hapticFeedback = dummyFeedback,
            mode = AppHapticMode.Off,
            effect = AppHapticEffect.BookmarkBurst,
        )

        hapticTriggered shouldBe false
    }

    @Test
    fun `performAppHapticFeedback executes gracefully with Rich and Subtle modes`() {
        var fallbackCalled = false
        val fallbackFeedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    fallbackCalled = true
                }
            }

        // Must not crash on Robolectric context
        performAppHapticFeedback(
            context = context,
            hapticFeedback = fallbackFeedback,
            mode = AppHapticMode.Rich,
            effect = AppHapticEffect.BookmarkBurst,
        )

        performAppHapticFeedback(
            context = context,
            hapticFeedback = fallbackFeedback,
            mode = AppHapticMode.Clear,
            effect = AppHapticEffect.BookmarkBurst,
        )
    }
}
