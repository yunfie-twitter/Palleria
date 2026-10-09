package com.yunfie.illustia.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.AppHapticMode

val LocalAppHapticMode = compositionLocalOf { AppHapticMode.Rich }

val NoOpHapticFeedback =
    object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }

enum class AppHapticEffect {
    Click,
    Toggle,
    Success,
    Error,
    BookmarkBurst,
    BoundaryLimit,
    ThresholdSnap,
    WheelTick,
    Peek,
    Dismiss,
}

@androidx.compose.runtime.Composable
fun rememberHapticFeedbackAction(): (AppHapticEffect) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticFeedback = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current

    return androidx.compose.runtime.remember(context, hapticFeedback, hapticMode) {
        { effect ->
            performAppHapticFeedback(context, hapticFeedback, hapticMode, effect)
        }
    }
}

fun performAppHapticFeedback(
    context: Context,
    hapticFeedback: HapticFeedback,
    mode: AppHapticMode,
    effect: AppHapticEffect = AppHapticEffect.Click,
) {
    if (mode == AppHapticMode.Off) return

    val vibrator = resolveVibrator(context)

    if (vibrator == null || !vibrator.hasVibrator()) {
        return
    }

    val vibrationPlayed =
        runCatching {
            when {
                PlatformCapabilities.supportsVibrationComposition() &&
                    mode == AppHapticMode.Rich &&
                    vibrator.vibrateCompositionIfSupported(effect) -> {
                    Unit
                }

                PlatformCapabilities.supportsPredefinedVibrationEffect() -> {
                    vibrator.vibrate(VibrationEffect.createPredefined(effect.predefinedEffect(mode)))
                }

                PlatformCapabilities.supportsVibrationEffect() -> {
                    vibrator.vibrate(effect.compatEffect(mode, vibrator.hasAmplitudeControl()))
                }

                else -> {
                    @Suppress("DEPRECATION")
                    when (effect) {
                        AppHapticEffect.Success -> vibrator.vibrate(longArrayOf(0L, 18L, 30L, 28L), -1)

                        AppHapticEffect.BookmarkBurst -> vibrator.vibrate(longArrayOf(0L, 14L, 24L, 30L), -1)

                        AppHapticEffect.Error -> vibrator.vibrate(longArrayOf(0L, 30L, 32L, 42L), -1)

                        AppHapticEffect.BoundaryLimit -> vibrator.vibrate(if (mode == AppHapticMode.Rich) 36L else 24L)

                        AppHapticEffect.ThresholdSnap,
                        AppHapticEffect.WheelTick,
                        AppHapticEffect.Dismiss,
                        -> vibrator.vibrate(if (mode == AppHapticMode.Rich) 12L else 8L)

                        AppHapticEffect.Peek -> vibrator.vibrate(if (mode == AppHapticMode.Rich) 26L else 16L)

                        AppHapticEffect.Click,
                        AppHapticEffect.Toggle,
                        -> vibrator.vibrate(if (mode == AppHapticMode.Rich) 28L else 16L)
                    }
                }
            }
        }.isSuccess

    if (!vibrationPlayed) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

fun isAppHapticsSupported(context: Context): Boolean = resolveVibrator(context)?.hasVibrator() == true

private fun resolveVibrator(context: Context): Vibrator? =
    if (PlatformCapabilities.supportsVibratorManager()) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

@RequiresApi(Build.VERSION_CODES.R)
@Suppress("CyclomaticComplexMethod")
private fun Vibrator.checkPrimitivesSupported(effect: AppHapticEffect): Boolean =
    when (effect) {
        AppHapticEffect.Click -> {
            areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
        }

        AppHapticEffect.Toggle, AppHapticEffect.ThresholdSnap -> {
            areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
        }

        AppHapticEffect.WheelTick -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) ||
                    areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
            } else {
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)
            }
        }

        AppHapticEffect.BoundaryLimit -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD) ||
                    areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            } else {
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)
            }
        }

        AppHapticEffect.Peek, AppHapticEffect.Success -> {
            areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            )
        }

        AppHapticEffect.Dismiss -> {
            areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
        }

        AppHapticEffect.BookmarkBurst -> {
            areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK,
            )
        }

        AppHapticEffect.Error -> {
            areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
                VibrationEffect.Composition.PRIMITIVE_CLICK,
            )
        }
    }

@RequiresApi(Build.VERSION_CODES.R)
@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun Vibrator.composeEffectPrimitives(
    composition: VibrationEffect.Composition,
    effect: AppHapticEffect,
) {
    when (effect) {
        AppHapticEffect.Click -> {
            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.85f)
        }

        AppHapticEffect.Toggle -> {
            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f)
        }

        AppHapticEffect.ThresholdSnap -> {
            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.65f)
        }

        AppHapticEffect.WheelTick -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
            ) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.65f)
            } else {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f)
            }
        }

        AppHapticEffect.BoundaryLimit -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)
            ) {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
            } else {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
            }
        }

        AppHapticEffect.Peek -> {
            composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.55f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.95f, 15)
        }

        AppHapticEffect.Dismiss -> {
            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.55f)
        }

        AppHapticEffect.Success -> {
            composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.95f, 20)
        }

        AppHapticEffect.BookmarkBurst -> {
            composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.40f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 15)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.75f, 30)
        }

        AppHapticEffect.Error -> {
            composition
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 25)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun Vibrator.vibrateCompositionIfSupported(effect: AppHapticEffect): Boolean {
    if (!checkPrimitivesSupported(effect)) return false
    val composition = VibrationEffect.startComposition()
    composeEffectPrimitives(composition, effect)
    vibrate(composition.compose())
    return true
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun AppHapticEffect.predefinedEffect(mode: AppHapticMode): Int =
    when (this) {
        AppHapticEffect.Click -> {
            if (mode == AppHapticMode.Rich) {
                VibrationEffect.EFFECT_HEAVY_CLICK
            } else {
                VibrationEffect.EFFECT_CLICK
            }
        }

        AppHapticEffect.Toggle,
        AppHapticEffect.ThresholdSnap,
        AppHapticEffect.WheelTick,
        AppHapticEffect.Dismiss,
        -> {
            VibrationEffect.EFFECT_TICK
        }

        AppHapticEffect.Success,
        AppHapticEffect.BookmarkBurst,
        AppHapticEffect.BoundaryLimit,
        AppHapticEffect.Peek,
        -> {
            VibrationEffect.EFFECT_HEAVY_CLICK
        }

        AppHapticEffect.Error -> {
            VibrationEffect.EFFECT_DOUBLE_CLICK
        }
    }

@RequiresApi(Build.VERSION_CODES.O)
@Suppress("CyclomaticComplexMethod", "MagicNumber")
private fun AppHapticEffect.compatEffect(
    mode: AppHapticMode,
    hasAmplitudeControl: Boolean,
): VibrationEffect {
    val strongAmplitude = if (hasAmplitudeControl) 220 else VibrationEffect.DEFAULT_AMPLITUDE
    val lightAmplitude = if (hasAmplitudeControl) 120 else VibrationEffect.DEFAULT_AMPLITUDE
    return when (this) {
        AppHapticEffect.Success,
        AppHapticEffect.BookmarkBurst,
        -> {
            if (hasAmplitudeControl) {
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 14L, 24L, 28L),
                    intArrayOf(0, lightAmplitude, 0, strongAmplitude),
                    -1,
                )
            } else {
                VibrationEffect.createWaveform(longArrayOf(0L, 14L, 24L, 28L), -1)
            }
        }

        AppHapticEffect.Error -> {
            if (hasAmplitudeControl) {
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 28L, 30L, 40L),
                    intArrayOf(0, strongAmplitude, 0, strongAmplitude),
                    -1,
                )
            } else {
                VibrationEffect.createWaveform(longArrayOf(0L, 28L, 30L, 40L), -1)
            }
        }

        AppHapticEffect.BoundaryLimit -> {
            VibrationEffect.createOneShot(32L, strongAmplitude)
        }

        AppHapticEffect.Peek -> {
            VibrationEffect.createOneShot(24L, strongAmplitude)
        }

        AppHapticEffect.ThresholdSnap -> {
            VibrationEffect.createOneShot(12L, lightAmplitude)
        }

        AppHapticEffect.WheelTick -> {
            val tickAmplitude = if (hasAmplitudeControl) (lightAmplitude * 0.7f).toInt().coerceAtLeast(1) else lightAmplitude
            VibrationEffect.createOneShot(8L, tickAmplitude)
        }

        AppHapticEffect.Dismiss -> {
            VibrationEffect.createOneShot(10L, lightAmplitude)
        }

        AppHapticEffect.Click,
        AppHapticEffect.Toggle,
        -> {
            VibrationEffect.createOneShot(
                if (mode == AppHapticMode.Rich) 24L else 14L,
                if (mode == AppHapticMode.Rich) strongAmplitude else lightAmplitude,
            )
        }
    }
}
