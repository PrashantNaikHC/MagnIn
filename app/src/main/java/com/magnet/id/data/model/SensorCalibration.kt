package com.magnet.id.data.model

import androidx.compose.ui.geometry.Offset

/**
 * Stores the detected physical magnetometer sensor location on this device.
 * Coordinates are normalized in [0.0..1.0] relative to screen width and height.
 */
data class SensorCalibration(
    val normalizedX: Float = 0.08f,
    val normalizedY: Float = 0.08f, // Physical magnetometer location on Galaxy A72: extreme top-left corner
    val isCalibrated: Boolean = false,
    val peakFluxU: Float = 0f
) {
    /**
     * Converts normalized coordinates to actual screen pixel Offset.
     */
    fun toPixelOffset(screenWidth: Float, screenHeight: Float): Offset {
        return Offset(
            x = (normalizedX * screenWidth).coerceIn(40f, screenWidth - 40f),
            y = (normalizedY * screenHeight).coerceIn(80f, screenHeight - 100f)
        )
    }

    companion object {
        val DEFAULT = SensorCalibration(
            normalizedX = 0.08f,
            normalizedY = 0.08f,
            isCalibrated = false,
            peakFluxU = 0f
        )
    }
}
