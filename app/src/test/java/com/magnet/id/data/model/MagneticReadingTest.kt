package com.magnet.id.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagneticReadingTest {

    @Test
    fun testMagnitudeCalculation() {
        val reading = MagneticReading(x = 3f, y = 4f, z = 0f)
        assertEquals(5f, reading.magnitude, 0.001f)

        val reading3D = MagneticReading(x = 2f, y = 3f, z = 6f)
        assertEquals(7f, reading3D.magnitude, 0.001f)
    }

    @Test
    fun testPolarityClassification() {
        val northPole = MagneticReading(x = 10f, y = 10f, z = 85f)
        assertEquals(MagneticPolarity.NORTH, northPole.polarity)

        val southPole = MagneticReading(x = 10f, y = 10f, z = -85f)
        assertEquals(MagneticPolarity.SOUTH, southPole.polarity)

        val ambientNeutral = MagneticReading(x = 5f, y = 5f, z = 2f)
        assertEquals(MagneticPolarity.NEUTRAL, ambientNeutral.polarity)
    }

    @Test
    fun testTareSubtraction() {
        val ambient = MagneticReading(x = 18f, y = -22f, z = 44f)
        val activeWithMagnet = MagneticReading(x = 118f, y = 78f, z = 244f)

        val net = activeWithMagnet.subtract(ambient)
        assertEquals(100f, net.x, 0.001f)
        assertEquals(100f, net.y, 0.001f)
        assertEquals(200f, net.z, 0.001f)
    }

    @Test
    fun testDistanceTo() {
        val readingA = MagneticReading(x = 10f, y = 20f, z = 30f)
        val readingB = MagneticReading(x = 10f, y = 23f, z = 34f)

        // dy = 3, dz = 4 => distance = 5
        assertEquals(5f, readingA.distanceTo(readingB), 0.001f)
    }

    @Test
    fun testRotationCalculation() {
        // Pointing East (+X) -> 0°
        val east = MagneticReading(x = 100f, y = 0f, z = 10f)
        assertEquals(0f, east.rotationAngleDeg, 0.1f)

        // Pointing North (+Y) -> 90°
        val north = MagneticReading(x = 0f, y = 100f, z = 10f)
        assertEquals(90f, north.rotationAngleDeg, 0.1f)

        // Pointing West (-X) -> 180°
        val west = MagneticReading(x = -100f, y = 0f, z = 10f)
        assertEquals(180f, west.rotationAngleDeg, 0.1f)

        // Pointing South (-Y) -> 270°
        val south = MagneticReading(x = 0f, y = -100f, z = 10f)
        assertEquals(270f, south.rotationAngleDeg, 0.1f)
    }

    @Test
    fun testClockHeadingCalculation() {
        // 12 o'clock (tilted toward phone top: y > 0) -> 0°
        val top = MagneticReading(x = 0f, y = 100f, z = 10f)
        assertEquals(0f, top.clockHeadingDeg, 0.1f)

        // 3 o'clock (tilted toward phone right: x < 0 in dipole field) -> 90°
        val right = MagneticReading(x = -100f, y = 0f, z = 10f)
        assertEquals(90f, right.clockHeadingDeg, 0.1f)

        // 6 o'clock (tilted toward phone bottom: y < 0) -> 180°
        val bottom = MagneticReading(x = 0f, y = -100f, z = 10f)
        assertEquals(180f, bottom.clockHeadingDeg, 0.1f)

        // 9 o'clock (tilted toward phone left: x > 0 in dipole field) -> 270°
        val left = MagneticReading(x = 100f, y = 0f, z = 10f)
        assertEquals(270f, left.clockHeadingDeg, 0.1f)
    }
}
