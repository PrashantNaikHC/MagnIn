package com.magnet.id.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.magnet.id.data.model.GroundReference
import com.magnet.id.data.model.MagneticReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sin

class MagnetometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var magnetometer: Sensor? = null
    private var isUncalibrated = false

    private val _rawReading = MutableStateFlow(MagneticReading.ZERO)
    val rawReading: StateFlow<MagneticReading> = _rawReading.asStateFlow()

    private val _netReading = MutableStateFlow(MagneticReading.ZERO)
    val netReading: StateFlow<MagneticReading> = _netReading.asStateFlow()

    private val _ambientTare = MutableStateFlow(MagneticReading.ZERO)
    val ambientTare: StateFlow<MagneticReading> = _ambientTare.asStateFlow()

    private val _groundReference = MutableStateFlow(GroundReference.ZERO)
    val groundReference: StateFlow<GroundReference> = _groundReference.asStateFlow()

    private val _isStable = MutableStateFlow(false)
    val isStable: StateFlow<Boolean> = _isStable.asStateFlow()

    private val _hasHardwareSensor = MutableStateFlow(false)
    val hasHardwareSensor: StateFlow<Boolean> = _hasHardwareSensor.asStateFlow()

    // EMA smoothing filter coefficient (0.25 gives quick response while eliminating EM jitter)
    private val alpha = 0.28f
    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    private var isFirstSample = true

    // Rolling window for stability detection (15 samples ~ 300ms)
    private val recentMagnitudes = ArrayDeque<Float>(20)
    private val windowSize = 15
    private val stabilityVarianceThreshold = 3.5f // µT variance threshold

    init {
        findBestSensor()
    }

    private fun findBestSensor() {
        if (sensorManager == null) {
            _hasHardwareSensor.value = false
            return
        }

        // Prefer uncalibrated to avoid OS hard-iron auto-calibration shifts during testing
        val uncalibrated = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED)
        if (uncalibrated != null) {
            magnetometer = uncalibrated
            isUncalibrated = true
            _hasHardwareSensor.value = true
        } else {
            val standard = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (standard != null) {
                magnetometer = standard
                isUncalibrated = false
                _hasHardwareSensor.value = true
            } else {
                _hasHardwareSensor.value = false
            }
        }
    }

    fun startListening() {
        magnetometer?.let { sensor ->
            // SENSOR_DELAY_GAME (~50-60 Hz) for smooth 60fps animations
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Tares the current ambient field to zero.
     * Future net readings will represent field flux above this ambient baseline.
     */
    fun tare() {
        _ambientTare.value = _rawReading.value
        val currentGround = _groundReference.value
        _netReading.value = MagneticReading(
            x = 0f - currentGround.offsetX,
            y = 0f - currentGround.offsetY,
            z = 0f,
            timestampNs = _rawReading.value.timestampNs
        )
    }

    /**
     * Resets tare baseline back to absolute zero.
     */
    fun resetTare() {
        _ambientTare.value = MagneticReading.ZERO
        val currentRaw = _rawReading.value
        val currentGround = _groundReference.value
        _netReading.value = MagneticReading(
            x = currentRaw.x - currentGround.offsetX,
            y = currentRaw.y - currentGround.offsetY,
            z = currentRaw.z,
            timestampNs = currentRaw.timestampNs
        )
    }

    /**
     * Sets in-plane reference offset so current stick orientation is treated as 100% perpendicular to the ground.
     */
    fun setGroundReference(offsetX: Float, offsetY: Float) {
        _groundReference.value = GroundReference(offsetX, offsetY, true)
        val currentRaw = _rawReading.value
        val currentTare = _ambientTare.value
        _netReading.value = MagneticReading(
            x = currentRaw.x - currentTare.x - offsetX,
            y = currentRaw.y - currentTare.y - offsetY,
            z = currentRaw.z - currentTare.z,
            timestampNs = currentRaw.timestampNs
        )
    }

    /**
     * Resets ground reference offset back to default ungrounded (0, 0).
     */
    fun resetGroundReference() {
        _groundReference.value = GroundReference.ZERO
        val currentRaw = _rawReading.value
        val currentTare = _ambientTare.value
        _netReading.value = MagneticReading(
            x = currentRaw.x - currentTare.x,
            y = currentRaw.y - currentTare.y,
            z = currentRaw.z - currentTare.z,
            timestampNs = currentRaw.timestampNs
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        // Low-pass Exponential Moving Average (EMA) filtering
        if (isFirstSample) {
            filteredX = rawX
            filteredY = rawY
            filteredZ = rawZ
            isFirstSample = false
            // Auto-tare initial ambient reading on startup
            _ambientTare.value = MagneticReading(filteredX, filteredY, filteredZ, event.timestamp)
        } else {
            filteredX = alpha * rawX + (1f - alpha) * filteredX
            filteredY = alpha * rawY + (1f - alpha) * filteredY
            filteredZ = alpha * rawZ + (1f - alpha) * filteredZ
        }

        val currentRaw = MagneticReading(
            x = filteredX,
            y = filteredY,
            z = filteredZ,
            timestampNs = event.timestamp
        )
        _rawReading.value = currentRaw
        val currentTare = _ambientTare.value
        val currentGround = _groundReference.value
        _netReading.value = MagneticReading(
            x = currentRaw.x - currentTare.x - currentGround.offsetX,
            y = currentRaw.y - currentTare.y - currentGround.offsetY,
            z = currentRaw.z - currentTare.z,
            timestampNs = event.timestamp
        )

        updateStability(currentRaw.magnitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun updateStability(magnitude: Float) {
        if (recentMagnitudes.size >= windowSize) {
            recentMagnitudes.removeFirst()
        }
        recentMagnitudes.addLast(magnitude)

        if (recentMagnitudes.size >= windowSize) {
            val mean = recentMagnitudes.average().toFloat()
            var varianceSum = 0f
            for (m in recentMagnitudes) {
                varianceSum += (m - mean) * (m - mean)
            }
            val variance = varianceSum / recentMagnitudes.size
            _isStable.value = variance < stabilityVarianceThreshold
        }
    }

    /**
     * Simulation feeder for emulators without physical magnetometers.
     */
    fun injectSimulatedReading(x: Float, y: Float, z: Float) {
        val reading = MagneticReading(x, y, z, System.nanoTime())
        _rawReading.value = reading
        _netReading.value = reading.subtract(_ambientTare.value)
        updateStability(reading.magnitude)
    }
}
