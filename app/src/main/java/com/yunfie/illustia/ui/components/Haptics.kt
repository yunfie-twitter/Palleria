package com.yunfie.illustia.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.yunfie.illustia.settings.AppHapticMode

val LocalAppHapticMode = compositionLocalOf { AppHapticMode.Rich }

fun performAppHapticFeedback(
    context: Context,
    hapticFeedback: HapticFeedback,
    mode: AppHapticMode,
) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    if (vibrator == null || !vibrator.hasVibrator()) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        return
    }

    val effect = when (mode) {
        AppHapticMode.Rich -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        AppHapticMode.Clear -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    }
    vibrator.vibrate(effect)
}
