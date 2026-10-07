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
class TactileHapticEnhancementsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `new tactile haptic effect enum entries exist`() {
        val effects = AppHapticEffect.entries
        effects.contains(AppHapticEffect.BoundaryLimit) shouldBe true
        effects.contains(AppHapticEffect.ThresholdSnap) shouldBe true
        effects.contains(AppHapticEffect.WheelTick) shouldBe true
        effects.contains(AppHapticEffect.Peek) shouldBe true
        effects.contains(AppHapticEffect.Dismiss) shouldBe true
    }

    @Test
    fun `performAppHapticFeedback respects AppHapticMode Off for all new effects`() {
        var hapticTriggered = false
        val dummyFeedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    hapticTriggered = true
                }
            }

        listOf(
            AppHapticEffect.BoundaryLimit,
            AppHapticEffect.ThresholdSnap,
            AppHapticEffect.WheelTick,
            AppHapticEffect.Peek,
            AppHapticEffect.Dismiss,
        ).forEach { effect ->
            performAppHapticFeedback(
                context = context,
                hapticFeedback = dummyFeedback,
                mode = AppHapticMode.Off,
                effect = effect,
            )
        }

        hapticTriggered shouldBe false
    }

    @Test
    fun `performAppHapticFeedback handles Rich and Clear modes without crashing`() {
        val dummyFeedback =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    // No-op for test validation
                }
            }

        listOf(
            AppHapticEffect.BoundaryLimit,
            AppHapticEffect.ThresholdSnap,
            AppHapticEffect.WheelTick,
            AppHapticEffect.Peek,
            AppHapticEffect.Dismiss,
        ).forEach { effect ->
            performAppHapticFeedback(
                context = context,
                hapticFeedback = dummyFeedback,
                mode = AppHapticMode.Rich,
                effect = effect,
            )
            performAppHapticFeedback(
                context = context,
                hapticFeedback = dummyFeedback,
                mode = AppHapticMode.Clear,
                effect = effect,
            )
        }
    }
}
