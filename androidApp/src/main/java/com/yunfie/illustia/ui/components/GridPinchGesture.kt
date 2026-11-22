package com.yunfie.illustia.ui.components

internal data class GridPinchUpdate(
    val consume: Boolean,
    val columnDelta: Int = 0,
)

/** Once a pinch starts, own the entire gesture, including the final finger lift. */
internal class GridPinchGesture {
    private var claimed = false
    private var accumulatedZoom = 1f

    fun update(
        pressedPointers: Int,
        zoom: Float,
    ): GridPinchUpdate {
        if (pressedPointers >= 2) claimed = true
        val consume = claimed
        var delta = 0
        if (pressedPointers >= 2 && zoom.isFinite() && zoom > 0f) {
            accumulatedZoom *= zoom
            delta =
                when {
                    accumulatedZoom > ZOOM_IN_THRESHOLD -> -1
                    accumulatedZoom < ZOOM_OUT_THRESHOLD -> 1
                    else -> 0
                }
            if (delta != 0) accumulatedZoom = 1f
        }
        if (pressedPointers == 0) {
            claimed = false
            accumulatedZoom = 1f
        }
        return GridPinchUpdate(consume, delta)
    }

    private companion object {
        const val ZOOM_IN_THRESHOLD = 1.28f
        const val ZOOM_OUT_THRESHOLD = 0.78f
    }
}
