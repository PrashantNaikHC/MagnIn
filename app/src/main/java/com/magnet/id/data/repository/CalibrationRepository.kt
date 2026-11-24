package com.magnet.id.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.magnet.id.data.model.GroundReference
import com.magnet.id.data.model.SensorCalibration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalibrationRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("magnet_calibration", Context.MODE_PRIVATE)

    private val _calibration = MutableStateFlow(loadCalibration())
    val calibration: StateFlow<SensorCalibration> = _calibration.asStateFlow()

    private val _groundReference = MutableStateFlow(loadGroundReference())
    val groundReference: StateFlow<GroundReference> = _groundReference.asStateFlow()

    private fun loadCalibration(): SensorCalibration {
        val hasCalibration = prefs.getBoolean(KEY_IS_CALIBRATED, false)
        if (!hasCalibration) return SensorCalibration.DEFAULT

        return SensorCalibration(
            normalizedX = prefs.getFloat(KEY_NORM_X, 0.5f),
            normalizedY = prefs.getFloat(KEY_NORM_Y, 0.22f),
            isCalibrated = true,
            peakFluxU = prefs.getFloat(KEY_PEAK_FLUX, 0f)
        )
    }

    private fun loadGroundReference(): GroundReference {
        val isGrounded = prefs.getBoolean(KEY_IS_GROUNDED, false)
        if (!isGrounded) return GroundReference.ZERO
        return GroundReference(
            offsetX = prefs.getFloat(KEY_GROUND_X, 0f),
            offsetY = prefs.getFloat(KEY_GROUND_Y, 0f),
            isGrounded = true
        )
    }

    fun saveCalibration(calibration: SensorCalibration) {
        prefs.edit()
            .putBoolean(KEY_IS_CALIBRATED, calibration.isCalibrated)
            .putFloat(KEY_NORM_X, calibration.normalizedX)
            .putFloat(KEY_NORM_Y, calibration.normalizedY)
            .putFloat(KEY_PEAK_FLUX, calibration.peakFluxU)
            .apply()
        _calibration.value = calibration
    }

    fun resetCalibration() {
        prefs.edit()
            .remove(KEY_IS_CALIBRATED)
            .remove(KEY_NORM_X)
            .remove(KEY_NORM_Y)
            .remove(KEY_PEAK_FLUX)
            .apply()
        _calibration.value = SensorCalibration.DEFAULT
    }

    fun saveGroundReference(offsetX: Float, offsetY: Float) {
        prefs.edit()
            .putBoolean(KEY_IS_GROUNDED, true)
            .putFloat(KEY_GROUND_X, offsetX)
            .putFloat(KEY_GROUND_Y, offsetY)
            .apply()
        _groundReference.value = GroundReference(offsetX, offsetY, true)
    }

    fun resetGroundReference() {
        prefs.edit()
            .remove(KEY_IS_GROUNDED)
            .remove(KEY_GROUND_X)
            .remove(KEY_GROUND_Y)
            .apply()
        _groundReference.value = GroundReference.ZERO
    }

    companion object {
        private const val KEY_IS_CALIBRATED = "is_calibrated"
        private const val KEY_NORM_X = "norm_x"
        private const val KEY_NORM_Y = "norm_y"
        private const val KEY_PEAK_FLUX = "peak_flux"

        private const val KEY_IS_GROUNDED = "is_grounded"
        private const val KEY_GROUND_X = "ground_x"
        private const val KEY_GROUND_Y = "ground_y"
    }
}
