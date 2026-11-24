package com.magnet.id.data.model

import kotlin.math.abs
import kotlin.math.sqrt

enum class MagneticPolarity {
    NORTH,
    SOUTH,
    NEUTRAL
}

/**
 * Immutable representation of a 3-axis magnetic flux density reading in microteslas (µT).
 */
data class MagneticReading(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestampNs: Long = 0L
) {
    /**
     * Total magnitude of the 3D magnetic flux vector: |B| = sqrt(x^2 + y^2 + z^2)
     */
    val magnitude: Float = sqrt(x * x + y * y + z * z)

    /**
     * Polarity relative to the screen plane:
     * In Android sensor coordinates:
     * +Z points outward from the screen toward the user.
     * -Z points into the back of the device.
     */
    val polarity: MagneticPolarity = when {
        z > 15f -> MagneticPolarity.NORTH
        z < -15f -> MagneticPolarity.SOUTH
        else -> MagneticPolarity.NEUTRAL
    }

    /**
     * In-plane rotation (azimuth / yaw) in degrees [0°..360°).
     * 0° = pointing right (+X)
     * 90° = pointing up towards phone earpiece (+Y)
     * 180° = pointing left (-X)
     * 270° = pointing down towards charge port (-Y)
     */
    val rotationAngleDeg: Float = run {
        val inPlaneMag = sqrt(x * x + y * y)
        if (inPlaneMag < 1.5f) {
            0f
        } else {
            val rad = kotlin.math.atan2(y, x)
            val deg = Math.toDegrees(rad.toDouble()).toFloat()
            (deg + 360f) % 360f
        }
    }

    /**
     * Clock heading in degrees [0°..360°) matching physical clockwise stick movement:
     * 0° / 360° = 12 o'clock (pointing to top of phone)
     * 90° = 3 o'clock (pointing to right of phone)
     * 180° = 6 o'clock (pointing to bottom of phone)
     * 270° = 9 o'clock (pointing to left of phone)
     */
    val clockHeadingDeg: Float = run {
        val inPlaneMag = sqrt(x * x + y * y)
        if (inPlaneMag < 1.5f) {
            0f
        } else {
            val canvasRad = kotlin.math.atan2(-y.toDouble(), -x.toDouble())
            val canvasDeg = Math.toDegrees(canvasRad).toFloat()
            val clockDeg = canvasDeg + 90f
            ((clockDeg % 360f) + 360f) % 360f
        }
    }

    /**
     * Out-of-plane plunge / tilt elevation angle in degrees [-90°..+90°].
     * +90° = perpendicular, piercing straight out toward user.
     * -90° = perpendicular, piercing straight into phone back.
     */
    val tiltAngleDeg: Float = run {
        if (magnitude < 1.5f) {
            90f
        } else {
            val sinTheta = (z / magnitude).coerceIn(-1f, 1f)
            Math.toDegrees(kotlin.math.asin(sinTheta).toDouble()).toFloat()
        }
    }

    /**
     * Subtract an ambient baseline reading to isolate net magnet-induced flux.
     */
    fun subtract(ambient: MagneticReading): MagneticReading {
        return MagneticReading(
            x = this.x - ambient.x,
            y = this.y - ambient.y,
            z = this.z - ambient.z,
            timestampNs = this.timestampNs
        )
    }

    /**
     * Euclidean distance in vector space to another reading.
     */
    fun distanceTo(other: MagneticReading): Float {
        val dx = this.x - other.x
        val dy = this.y - other.y
        val dz = this.z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val ZERO = MagneticReading(0f, 0f, 0f, 0L)
    }
}
