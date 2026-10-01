package com.yunfie.illustia.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PRIORITY_POWER_SAVING = 1
private const val PRIORITY_NORMAL = 2
private const val PRIORITY_BOOST = 3

/**
 * Operating mode for Adaptive Refresh Rate (ARR) / Dynamic Hz power saving.
 */
enum class DynamicHzMode(
    val priority: Int,
) {
    /** 30Hz / 60Hz: Still image viewing, novel reading without scrolling. */
    PowerSaving(PRIORITY_POWER_SAVING),

    /** 60Hz / System default: General idle UI. */
    Normal(PRIORITY_NORMAL),

    /** 120Hz: Active scrolling, ugoira animation playback, page transitions. */
    Boost(PRIORITY_BOOST),
}

interface DynamicHzController {
    val currentMode: StateFlow<DynamicHzMode>

    fun requestMode(
        requesterKey: Any,
        mode: DynamicHzMode,
    )

    fun releaseMode(requesterKey: Any)
}

class DefaultDynamicHzController : DynamicHzController {
    private val requests = mutableMapOf<Any, DynamicHzMode>()
    private val _currentMode = MutableStateFlow(DynamicHzMode.Normal)
    override val currentMode: StateFlow<DynamicHzMode> = _currentMode.asStateFlow()

    override fun requestMode(
        requesterKey: Any,
        mode: DynamicHzMode,
    ) {
        requests[requesterKey] = mode
        updateEffectiveMode()
    }

    override fun releaseMode(requesterKey: Any) {
        requests.remove(requesterKey)
        updateEffectiveMode()
    }

    private fun updateEffectiveMode() {
        val highest = requests.values.maxByOrNull { it.priority } ?: DynamicHzMode.Normal
        if (_currentMode.value != highest) {
            _currentMode.value = highest
        }
    }
}

val LocalDynamicHzController =
    staticCompositionLocalOf<DynamicHzController> {
        DefaultDynamicHzController()
    }

/**
 * Convenient effect that registers a dynamic Hz mode for the lifecycle of the Composable.
 */
@Composable
fun RequestDynamicHzMode(
    mode: DynamicHzMode,
    key: Any = mode,
) {
    val controller = LocalDynamicHzController.current
    DisposableEffect(key, mode) {
        controller.requestMode(key, mode)
        onDispose {
            controller.releaseMode(key)
        }
    }
}
