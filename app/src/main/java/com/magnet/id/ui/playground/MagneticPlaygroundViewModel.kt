package com.magnet.id.ui.playground

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.magnet.id.data.model.GroundReference
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticReading
import com.magnet.id.data.model.SensorCalibration
import com.magnet.id.data.repository.CalibrationRepository
import com.magnet.id.sensor.MagnetometerManager
import com.magnet.id.ui.feedback.FeedbackController
import com.magnet.id.ui.playground.canvas.PlaygroundMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MagneticPlaygroundViewModel(application: Application) : AndroidViewModel(application) {

    private val sensorManager = MagnetometerManager(application)
    private val feedbackController = FeedbackController(application)
    private val calibrationRepo = CalibrationRepository(application)

    val netReading: StateFlow<MagneticReading> = sensorManager.netReading
    val rawReading: StateFlow<MagneticReading> = sensorManager.rawReading
    val ambientTare: StateFlow<MagneticReading> = sensorManager.ambientTare
    val isStable: StateFlow<Boolean> = sensorManager.isStable
    val hasHardwareSensor: StateFlow<Boolean> = sensorManager.hasHardwareSensor
    val calibration: StateFlow<SensorCalibration> = calibrationRepo.calibration
    val groundReference: StateFlow<GroundReference> = calibrationRepo.groundReference

    private val _kinematicConstraint = MutableStateFlow(KinematicConstraint.FREE)
    val kinematicConstraint: StateFlow<KinematicConstraint> = _kinematicConstraint.asStateFlow()

    fun cycleKinematicConstraint() {
        val next = when (_kinematicConstraint.value) {
            KinematicConstraint.FREE -> KinematicConstraint.DIAL
            KinematicConstraint.DIAL -> KinematicConstraint.LEVER
            KinematicConstraint.LEVER -> KinematicConstraint.JOYSTICK
            KinematicConstraint.JOYSTICK -> KinematicConstraint.FREE
        }
        _kinematicConstraint.value = next
        feedbackController.playTareFeedback()
    }

    fun setKinematicConstraint(constraint: KinematicConstraint) {
        _kinematicConstraint.value = constraint
        feedbackController.playTareFeedback()
    }

    private val _showCalibrationSheet = MutableStateFlow(false)
    val showCalibrationSheet: StateFlow<Boolean> = _showCalibrationSheet.asStateFlow()

    private val _selectedMode = MutableStateFlow(PlaygroundMode.IRON_FILINGS)
    val selectedMode: StateFlow<PlaygroundMode> = _selectedMode.asStateFlow()

    private val _magnetCenter = MutableStateFlow(Offset.Zero)
    val magnetCenter: StateFlow<Offset> = _magnetCenter.asStateFlow()

    fun openCalibration() {
        _showCalibrationSheet.value = true
    }

    fun dismissCalibration() {
        _showCalibrationSheet.value = false
    }

    fun applyCalibration(newCalibration: SensorCalibration, screenWidth: Float, screenHeight: Float) {
        calibrationRepo.saveCalibration(newCalibration)
        _showCalibrationSheet.value = false
        // Snap target reticle directly over the detected physical sensor chip for max sensitivity
        _magnetCenter.value = newCalibration.toPixelOffset(screenWidth, screenHeight)
        feedbackController.playIdentificationFeedback()
    }

    fun resetCalibration() {
        calibrationRepo.resetCalibration()
        _magnetCenter.value = Offset.Zero
    }

    fun snapToSensor(screenWidth: Float, screenHeight: Float) {
        val cal = calibration.value
        _magnetCenter.value = if (cal.isCalibrated) {
            cal.toPixelOffset(screenWidth, screenHeight)
        } else {
            Offset(screenWidth * 0.08f, screenHeight * 0.08f)
        }
        feedbackController.playTareFeedback()
    }

    fun moveMagnetCenter(dragAmount: Offset, screenWidth: Float, screenHeight: Float) {
        val cal = calibration.value
        val defaultSpot = if (cal.isCalibrated) cal.toPixelOffset(screenWidth, screenHeight) else Offset(screenWidth * 0.08f, screenHeight * 0.08f)
        val current = if (_magnetCenter.value == Offset.Zero || _magnetCenter.value.x <= 0f) {
            defaultSpot
        } else {
            _magnetCenter.value
        }
        val clampedX = (current.x + dragAmount.x).coerceIn(40f, screenWidth - 40f)
        val clampedY = (current.y + dragAmount.y).coerceIn(80f, screenHeight - 120f)
        _magnetCenter.value = Offset(clampedX, clampedY)
    }

    fun setMagnetCenter(target: Offset, screenWidth: Float, screenHeight: Float) {
        // Disregard accidental taps in the bottom mode bar
        if (target.y > screenHeight - 180f) return
        val clampedX = target.x.coerceIn(40f, screenWidth - 40f)
        val clampedY = target.y.coerceIn(80f, screenHeight - 120f)
        _magnetCenter.value = Offset(clampedX, clampedY)
    }

    /**
     * Locks the current reticle position as the device's calibrated sensor location.
     */
    fun lockCurrentReticleAsSensor(screenWidth: Float, screenHeight: Float) {
        val current = if (_magnetCenter.value == Offset.Zero) {
            Offset(screenWidth * 0.08f, screenHeight * 0.08f)
        } else {
            _magnetCenter.value
        }
        val normX = (current.x / screenWidth).coerceIn(0.02f, 0.98f)
        val normY = (current.y / screenHeight).coerceIn(0.02f, 0.98f)
        val newCal = SensorCalibration(
            normalizedX = normX,
            normalizedY = normY,
            isCalibrated = true,
            peakFluxU = netReading.value.magnitude
        )
        calibrationRepo.saveCalibration(newCal)
        _magnetCenter.value = current
        feedbackController.playIdentificationFeedback()
    }

    fun resetMagnetCenter() {
        _magnetCenter.value = Offset.Zero
    }

    private var wasMagnetPresent = false
    private val magnetPresenceThreshold = 25f // µT above tare to qualify as magnet placed

    init {
        viewModelScope.launch {
            calibrationRepo.groundReference.collect { ref ->
                if (ref.isGrounded) {
                    sensorManager.setGroundReference(ref.offsetX, ref.offsetY)
                } else {
                    sensorManager.resetGroundReference()
                }
            }
        }

        viewModelScope.launch {
            sensorManager.netReading.collect { reading ->
                val isMagnetPresent = reading.magnitude > magnetPresenceThreshold
                if (isMagnetPresent && !wasMagnetPresent) {
                    feedbackController.playMagnetDetectedFeedback()
                }
                wasMagnetPresent = isMagnetPresent
            }
        }
    }

    fun startListening() {
        sensorManager.startListening()
    }

    fun stopListening() {
        sensorManager.stopListening()
    }

    fun selectMode(mode: PlaygroundMode) {
        _selectedMode.value = mode
    }

    fun updateMagnetCenter(center: Offset) {
        _magnetCenter.value = center
    }

    fun tare() {
        sensorManager.tare()
        feedbackController.playTareFeedback()
    }

    fun resetTare() {
        sensorManager.resetTare()
        feedbackController.playTareFeedback()
    }

    /**
     * Calibrates the current stick orientation as 100% perpendicular to the ground (0° tilt).
     * Eliminates phone surface resting inclination and sensor mounting offsets.
     */
    fun groundCurrentOrientation() {
        val currentNet = netReading.value
        val existingGround = groundReference.value
        val newOffsetX = existingGround.offsetX + currentNet.x
        val newOffsetY = existingGround.offsetY + currentNet.y
        calibrationRepo.saveGroundReference(newOffsetX, newOffsetY)
        sensorManager.setGroundReference(newOffsetX, newOffsetY)
        feedbackController.playTareFeedback()
    }

    /**
     * Resets ground orientation back to ungrounded raw plane.
     */
    fun resetGroundOrientation() {
        calibrationRepo.resetGroundReference()
        sensorManager.resetGroundReference()
        feedbackController.playTareFeedback()
    }

    fun injectSimulatedField(x: Float, y: Float, z: Float) {
        sensorManager.injectSimulatedReading(x, y, z)
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.stopListening()
        feedbackController.release()
    }
}
