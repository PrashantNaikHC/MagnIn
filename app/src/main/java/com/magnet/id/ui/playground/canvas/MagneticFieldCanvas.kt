package com.magnet.id.ui.playground.canvas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticPolarity
import com.magnet.id.data.model.MagneticReading
import com.magnet.id.data.model.SensorCalibration
import com.magnet.id.ui.theme.AmbientGreen
import com.magnet.id.ui.theme.BorderHighlight
import com.magnet.id.ui.theme.NorthPoleBlue
import com.magnet.id.ui.theme.NorthPoleCyan
import com.magnet.id.ui.theme.SouthPoleAmber
import com.magnet.id.ui.theme.SouthPoleRed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class PlaygroundMode {
    IRON_FILINGS,
    FLUX_RIPPLES,
    GIMBAL_3D,
    DIAL
}

@Composable
fun MagneticFieldCanvas(
    reading: MagneticReading,
    mode: PlaygroundMode,
    magnetCenter: Offset,
    calibration: SensorCalibration = SensorCalibration.DEFAULT,
    constraint: KinematicConstraint = KinematicConstraint.FREE,
    onDragMove: (Offset, Float, Float) -> Unit,
    onTapMove: (Offset, Float, Float) -> Unit,
    onDoubleTapReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flux_wave")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    // Dynamic wave velocity factor based on magnetic field strength
    val fieldIntensity = (reading.magnitude / 200f).coerceIn(0f, 4f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapReset() },
                    onTap = { offset ->
                        onTapMove(offset, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragMove(dragAmount, size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        // Target reticle defaults directly to the phone's physical sensor spot for maximum sensitivity
        val defaultSpot = if (calibration.isCalibrated) {
            calibration.toPixelOffset(size.width, size.height)
        } else {
            Offset(size.width * 0.25f, size.height * 0.18f)
        }
        val center = if (magnetCenter == Offset.Zero || magnetCenter.x <= 0f) {
            defaultSpot
        } else {
            magnetCenter
        }

        when (mode) {
            PlaygroundMode.IRON_FILINGS -> {
                drawIronFilings(reading, center)
                drawPlacementReticle(center, reading)
            }
            PlaygroundMode.FLUX_RIPPLES -> {
                drawFluxRipples(reading, center, pulsePhase, fieldIntensity)
                drawPlacementReticle(center, reading)
            }
            PlaygroundMode.GIMBAL_3D -> {
                draw3dGimbal(reading, center, pulsePhase)
                drawPlacementReticle(center, reading)
            }
            PlaygroundMode.DIAL -> {
                // Plain blank page - no visualizer elements or reticle rendered
            }
        }
    }
}

/**
 * Visualizer Mode 1: Iron Filings Matrix
 * Simulates a microscopic needle grid that physically orientates along magnetic field lines.
 */
private fun DrawScope.drawIronFilings(reading: MagneticReading, center: Offset) {
    val spacing = 52f
    val cols = (size.width / spacing).toInt()
    val rows = (size.height / spacing).toInt()

    val netStrength = reading.magnitude.coerceIn(0f, 600f)
    val normStrength = (netStrength / 400f).coerceIn(0.05f, 1f)

    val primaryColor = when (reading.polarity) {
        MagneticPolarity.NORTH -> NorthPoleCyan
        MagneticPolarity.SOUTH -> SouthPoleRed
        MagneticPolarity.NEUTRAL -> AmbientGreen
    }

    for (r in 0..rows) {
        for (c in 0..cols) {
            val px = c * spacing + spacing / 2f
            val py = r * spacing + spacing / 2f

            val dx = px - center.x
            val dy = py - center.y
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(30f)

            // Dipole approximation: field angle combines radial vector with phone's net Bx, By
            val radialAngle = atan2(dy, dx)
            val phoneVectorAngle = atan2(reading.y, reading.x)
            val blendFactor = (100f / dist).coerceIn(0.1f, 0.9f)
            val needleAngle = radialAngle * (1f - blendFactor) + phoneVectorAngle * blendFactor

            val needleLength = (16f + 18f * (1f / (dist / 120f + 1f)) * normStrength)
            val halfLen = needleLength / 2f

            val alpha = (0.25f + 0.75f * (1f / (dist / 160f + 1f)) * normStrength).coerceIn(0.15f, 0.95f)
            val needleColor = primaryColor.copy(alpha = alpha)

            rotate(degrees = Math.toDegrees(needleAngle.toDouble()).toFloat(), pivot = Offset(px, py)) {
                // North tip (Cyan)
                drawLine(
                    color = NorthPoleCyan.copy(alpha = alpha),
                    start = Offset(px, py),
                    end = Offset(px + halfLen, py),
                    strokeWidth = 3f * normStrength.coerceAtLeast(0.6f),
                    cap = StrokeCap.Round
                )
                // South tip (Red)
                drawLine(
                    color = SouthPoleRed.copy(alpha = alpha),
                    start = Offset(px - halfLen, py),
                    end = Offset(px, py),
                    strokeWidth = 2.5f * normStrength.coerceAtLeast(0.6f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Visualizer Mode 2: Flux Ripples & Radiant Aurora
 * Expanding energy wave pulses with glowing halo rings.
 */
private fun DrawScope.drawFluxRipples(
    reading: MagneticReading,
    center: Offset,
    pulsePhase: Float,
    fieldIntensity: Float
) {
    val maxRadius = min(size.width, size.height) * 0.75f
    val ringCount = 7

    val baseColor = when (reading.polarity) {
        MagneticPolarity.NORTH -> NorthPoleCyan
        MagneticPolarity.SOUTH -> SouthPoleRed
        MagneticPolarity.NEUTRAL -> AmbientGreen
    }

    val glowColor = when (reading.polarity) {
        MagneticPolarity.NORTH -> NorthPoleBlue
        MagneticPolarity.SOUTH -> SouthPoleAmber
        MagneticPolarity.NEUTRAL -> Color(0xFF225533)
    }

    // Dynamic radial glow halo behind the epicenter
    val haloRadius = (90f + 160f * (fieldIntensity / 3f).coerceIn(0f, 1.5f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                baseColor.copy(alpha = 0.45f),
                glowColor.copy(alpha = 0.15f),
                Color.Transparent
            ),
            center = center,
            radius = haloRadius
        ),
        radius = haloRadius,
        center = center
    )

    // Concentric expanding flux wavefronts
    for (i in 0 until ringCount) {
        val ringOffset = (i.toFloat() / ringCount + pulsePhase * (1f + fieldIntensity * 0.5f)) % 1f
        val currentRadius = ringOffset * maxRadius
        val ringAlpha = (1f - ringOffset).coerceIn(0f, 1f) * 0.7f

        drawCircle(
            color = baseColor.copy(alpha = ringAlpha),
            radius = currentRadius,
            center = center,
            style = Stroke(
                width = (3f + 4f * (1f - ringOffset) * fieldIntensity).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        )
    }

    // Orbiting particle sparks along magnetic flux lines
    val sparkCount = 12
    for (s in 0 until sparkCount) {
        val sparkPhase = (pulsePhase * 2f + s.toFloat() / sparkCount) % 1f
        val angle = (s * (2 * PI / sparkCount) + pulsePhase * (PI * (1f + fieldIntensity))).toFloat()
        val radius = 40f + sparkPhase * (maxRadius * 0.8f)

        val sx = center.x + cos(angle) * radius
        val sy = center.y + sin(angle) * radius

        val sparkAlpha = (1f - sparkPhase).coerceIn(0f, 1f) * 0.9f
        drawCircle(
            color = baseColor.copy(alpha = sparkAlpha),
            radius = 3.5f + 2f * fieldIntensity,
            center = Offset(sx, sy)
        )
    }
}

/**
 * Visualizer Mode 3: 3D Vector Gimbal & Scope
 * 3D spherical axes with vector needle showing elevation plunge and azimuth direction.
 */
private fun DrawScope.draw3dGimbal(
    reading: MagneticReading,
    center: Offset,
    pulsePhase: Float
) {
    val sphereRadius = min(size.width, size.height) * 0.32f

    // Background sphere grid
    drawCircle(
        color = BorderHighlight.copy(alpha = 0.4f),
        radius = sphereRadius,
        center = center,
        style = Stroke(width = 2f)
    )

    // Equator ring
    drawOval(
        color = BorderHighlight.copy(alpha = 0.6f),
        topLeft = Offset(center.x - sphereRadius, center.y - sphereRadius * 0.3f),
        size = androidx.compose.ui.geometry.Size(sphereRadius * 2f, sphereRadius * 0.6f),
        style = Stroke(width = 2f)
    )

    // Prime meridian ring
    drawOval(
        color = BorderHighlight.copy(alpha = 0.4f),
        topLeft = Offset(center.x - sphereRadius * 0.3f, center.y - sphereRadius),
        size = androidx.compose.ui.geometry.Size(sphereRadius * 0.6f, sphereRadius * 2f),
        style = Stroke(width = 2f)
    )

    // Normalize magnetic vector for 3D arrow representation
    val mag = reading.magnitude.coerceAtLeast(0.001f)
    val normX = (reading.x / mag).coerceIn(-1f, 1f)
    val normY = (reading.y / mag).coerceIn(-1f, 1f)
    val normZ = (reading.z / mag).coerceIn(-1f, 1f) // Z is into/out of screen

    val arrowLength = sphereRadius * (reading.magnitude / 150f).coerceIn(0.3f, 1.25f)
    val tipX = center.x - normX * arrowLength
    val tipY = center.y - normY * arrowLength

    val vectorColor = when (reading.polarity) {
        MagneticPolarity.NORTH -> NorthPoleCyan
        MagneticPolarity.SOUTH -> SouthPoleRed
        MagneticPolarity.NEUTRAL -> AmbientGreen
    }

    // Depth shadow / projection line onto the XY plane
    drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = center,
        end = Offset(tipX, center.y),
        strokeWidth = 2f
    )
    drawLine(
        color = Color.White.copy(alpha = 0.2f),
        start = Offset(tipX, center.y),
        end = Offset(tipX, tipY),
        strokeWidth = 2f
    )

    // Main 3D Magnetic Vector Arrow
    drawLine(
        color = vectorColor,
        start = center,
        end = Offset(tipX, tipY),
        strokeWidth = 7f,
        cap = StrokeCap.Round
    )

    // Vector Head & Plunge Depth Ring (Radius scales with Z plunge out of screen)
    val plungeRadius = (8f + 16f * ((normZ + 1f) / 2f)).coerceIn(4f, 26f)
    drawCircle(
        color = vectorColor,
        radius = plungeRadius,
        center = Offset(tipX, tipY)
    )
    drawCircle(
        color = Color.White,
        radius = plungeRadius * 0.45f,
        center = Offset(tipX, tipY)
    )
}

/**
 * Visualizer Mode 4: 3D Magnet Stick
 * Replicates the user's physical setup in top-down 3D perspective:
 * A shiny button magnet base anchored at the sweet spot, with the wooden dowel
 * shaft extending upwards. Tilting the physical stick around its anchored base
 * leans and pitches the 3D wooden stick in the exact matching direction.
 */
private fun DrawScope.draw3dMagnetObject(
    reading: MagneticReading,
    center: Offset,
    pulsePhase: Float,
    constraint: KinematicConstraint = KinematicConstraint.FREE
) {
    val magnetRadius = 66f
    val stickRadius = 26f
    val stickLength = 290f

    val netFlux = reading.magnitude.coerceAtLeast(0.01f)
    val rawInPlaneFlux = sqrt(reading.x * reading.x + reading.y * reading.y)

    // Raw direction vector of the tilt in screen plane:
    // Physical stick pivot lean: -reading.x for Screen X (right), -reading.y for Screen Y (down)
    // Preserves clockwise chirality (det = +1) and aligns 1:1 with clock heading
    val rawDirX = if (rawInPlaneFlux > 0.5f) -reading.x / rawInPlaneFlux else 0f
    val rawDirY = if (rawInPlaneFlux > 0.5f) -reading.y / rawInPlaneFlux else 0f

    // Compute constrained direction and tilt based on active kinematic mode
    val (dirX, dirY, tiltRatio) = when (constraint) {
        KinematicConstraint.FREE -> {
            // Unconstrained 2-DoF conical tilt & clock precession
            val ratio = if (netFlux < 25f || rawInPlaneFlux < 3.0f) 0f else (rawInPlaneFlux / netFlux).coerceIn(0f, 0.96f)
            Triple(rawDirX, rawDirY, ratio)
        }
        KinematicConstraint.DIAL -> {
            // 1-DoF Rotary Dial / Clock Precession:
            // Locks lean at fixed 35° tilt angle, rejecting all magnitude & plunge noise
            val fixedRatio = if (netFlux < 25f) 0f else 0.5735f // sin(35°)
            Triple(rawDirX, rawDirY, fixedRatio)
        }
        KinematicConstraint.LEVER -> {
            // 1-DoF Linear Pitch Lever / Throttle:
            // Locks lateral axis to 0, tracking only vertical (12 <-> 6 o'clock) tilt with zero sideways wobble
            val pitchFlux = abs(reading.y)
            val pitchDirY = if (reading.y >= 0f) -1f else 1f
            val ratio = if (netFlux < 25f || pitchFlux < 3.0f) 0f else (pitchFlux / netFlux).coerceIn(0f, 0.96f)
            Triple(0f, pitchDirY, ratio)
        }
        KinematicConstraint.JOYSTICK -> {
            // 2-DoF Gated Joystick:
            // Center deadzone (< 8°) snaps solid upright, plus 8-way soft magnetic directional detents
            val deadzone = 0.139f // sin(8°)
            val unconstrainedRatio = if (netFlux < 25f) 0f else (rawInPlaneFlux / netFlux).coerceIn(0f, 0.96f)
            val gatedRatio = if (unconstrainedRatio < deadzone) {
                0f
            } else {
                ((unconstrainedRatio - deadzone) / (1f - deadzone)).coerceIn(0f, 0.96f)
            }

            if (gatedRatio == 0f || rawInPlaneFlux < 0.5f) {
                Triple(0f, 0f, 0f)
            } else {
                // 8-Way directional detent snapping (every 45°: 12, 1:30, 3, 4:30, 6, 7:30, 9, 10:30)
                val rawAngleDeg = ((Math.toDegrees(atan2(rawDirY.toDouble(), rawDirX.toDouble())) % 360) + 360) % 360
                val nearestDetent = Math.round(rawAngleDeg / 45.0) * 45.0
                val diff = abs(rawAngleDeg - nearestDetent)
                val finalAngle = if (diff < 9.0) {
                    rawAngleDeg + (nearestDetent - rawAngleDeg) * 0.70
                } else {
                    rawAngleDeg
                }
                val rad = Math.toRadians(finalAngle)
                Triple(cos(rad).toFloat(), sin(rad).toFloat(), gatedRatio)
            }
        }
    }

    val tiltAngleRad = kotlin.math.asin(tiltRatio)
    val tiltDeg = Math.toDegrees(tiltAngleRad.toDouble()).toFloat()

    // Top end of the wooden stick in top-down perspective
    val leanDistance = stickLength * sin(tiltAngleRad)
    val topX = center.x + dirX * leanDistance
    val topY = center.y + dirY * leanDistance
    val topCenter = Offset(topX, topY)

    // Top cap radius scales slightly with perspective height
    val topRadius = stickRadius * (1.0f + 0.18f * cos(tiltAngleRad))

    // Polarity theme
    val isNorth = reading.polarity == MagneticPolarity.NORTH || reading.z >= 0f
    val polarityColor = if (isNorth) NorthPoleCyan else SouthPoleRed

    // 1. Soft Ground Contact Shadow (anchors the magnet base to the screen)
    val shadowOffset = Offset(center.x + dirX * 20f, center.y + dirY * 20f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
            center = shadowOffset,
            radius = magnetRadius * 1.4f
        ),
        radius = magnetRadius * 1.4f,
        center = shadowOffset
    )

    // 2. Shiny Nickel / Chrome Button Magnet Base (anchored at center)
    // Outer bevel ring
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF475569), Color(0xFFCBD5E1)),
            start = Offset(center.x - magnetRadius, center.y - magnetRadius),
            end = Offset(center.x + magnetRadius, center.y + magnetRadius)
        ),
        radius = magnetRadius,
        center = center
    )
    // Chrome face
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF334155)),
            center = Offset(center.x - 12f, center.y - 12f),
            radius = magnetRadius * 0.9f
        ),
        radius = magnetRadius * 0.90f,
        center = center
    )
    // Glowing polarity ring on the magnet base
    drawCircle(
        color = polarityColor.copy(alpha = 0.75f),
        radius = magnetRadius * 0.82f,
        center = center,
        style = Stroke(width = 2.5f)
    )

    // 3. Wooden Stick Shaft (connecting magnet base to top handle)
    val perpX = -dirY
    val perpY = dirX

    val b1 = Offset(center.x + perpX * stickRadius, center.y + perpY * stickRadius)
    val b2 = Offset(center.x - perpX * stickRadius, center.y - perpY * stickRadius)
    val t1 = Offset(topCenter.x + perpX * topRadius, topCenter.y + perpY * topRadius)
    val t2 = Offset(topCenter.x - perpX * topRadius, topCenter.y - perpY * topRadius)

    val shaftPath = Path().apply {
        moveTo(b1.x, b1.y)
        lineTo(t1.x, t1.y)
        lineTo(t2.x, t2.y)
        lineTo(b2.x, b2.y)
        close()
    }

    // Wood tones (birch / beech dowel)
    val woodLight = Color(0xFFF5D5B8)
    val woodMid = Color(0xFFD4A373)
    val woodDark = Color(0xFFA66F3E)
    val woodShadow = Color(0xFF6B4423)

    // Render wooden shaft with cylindrical shading
    drawPath(
        path = shaftPath,
        brush = Brush.linearGradient(
            colors = listOf(woodLight, woodMid, woodDark, woodShadow),
            start = Offset(center.x + perpX * stickRadius, center.y + perpY * stickRadius),
            end = Offset(center.x - perpX * stickRadius, center.y - perpY * stickRadius)
        )
    )
    drawPath(
        path = shaftPath,
        color = Color(0xFF523318).copy(alpha = 0.35f),
        style = Stroke(width = 1.2f)
    )

    // Draw longitudinal wood grain lines along the shaft
    if (leanDistance > 15f) {
        for (g in listOf(-0.5f, -0.15f, 0.25f, 0.6f)) {
            val gb = Offset(center.x + perpX * (stickRadius * g), center.y + perpY * (stickRadius * g))
            val gt = Offset(topCenter.x + perpX * (topRadius * g), topCenter.y + perpY * (topRadius * g))
            drawLine(
                color = woodShadow.copy(alpha = 0.25f),
                start = gb,
                end = gt,
                strokeWidth = 1.2f
            )
        }
    }

    // 4. Metallic Ferrule / Glue Collar (where stick enters magnet base)
    val ferruleRadius = stickRadius * 1.15f
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE2E8F0), Color(0xFF64748B)),
            start = Offset(center.x - ferruleRadius, center.y),
            end = Offset(center.x + ferruleRadius, center.y)
        ),
        radius = ferruleRadius,
        center = center,
        style = Stroke(width = 4f)
    )

    // 5. Top Wooden End Cap (facing the viewer)
    // Drop shadow under top cap onto the shaft
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = topRadius + 2f,
        center = Offset(topCenter.x - dirX * 3f, topCenter.y - dirY * 3f)
    )
    // Circular end-grain wood face
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(woodLight, woodMid, woodDark),
            center = Offset(topCenter.x - 4f, topCenter.y - 4f),
            radius = topRadius
        ),
        radius = topRadius,
        center = topCenter
    )
    // Wood growth rings / grain texture on the top cap
    drawCircle(
        color = woodShadow.copy(alpha = 0.30f),
        radius = topRadius * 0.65f,
        center = topCenter,
        style = Stroke(width = 1.2f)
    )
    drawCircle(
        color = woodShadow.copy(alpha = 0.25f),
        radius = topRadius * 0.35f,
        center = topCenter,
        style = Stroke(width = 1.2f)
    )
    // Subtle top rim highlight
    drawCircle(
        color = Color.White.copy(alpha = 0.45f),
        radius = topRadius,
        center = topCenter,
        style = Stroke(width = 1.5f)
    )

    // 6. Live Stick Direction & Tilt Indicator
    if (tiltDeg > 3f) {
        val arrowTip = Offset(topCenter.x + dirX * 38f, topCenter.y + dirY * 38f)
        drawLine(
            color = polarityColor.copy(alpha = 0.85f),
            start = topCenter,
            end = arrowTip,
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        // Arrow head
        val perpArrowX = -dirY
        val perpArrowY = dirX
        drawLine(
            color = polarityColor.copy(alpha = 0.85f),
            start = arrowTip,
            end = Offset(arrowTip.x - dirX * 10f + perpArrowX * 7f, arrowTip.y - dirY * 10f + perpArrowY * 7f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = polarityColor.copy(alpha = 0.85f),
            start = arrowTip,
            end = Offset(arrowTip.x - dirX * 10f - perpArrowX * 7f, arrowTip.y - dirY * 10f - perpArrowY * 7f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Renders the guided placement target circle with crosshairs and live field strength glow.
 */
private fun DrawScope.drawPlacementReticle(center: Offset, reading: MagneticReading) {
    val reticleRadius = 75f
    val activeColor = when (reading.polarity) {
        MagneticPolarity.NORTH -> NorthPoleCyan
        MagneticPolarity.SOUTH -> SouthPoleRed
        MagneticPolarity.NEUTRAL -> AmbientGreen
    }

    // Outer subtle guide ring
    drawCircle(
        color = activeColor.copy(alpha = 0.25f),
        radius = reticleRadius + 20f,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Main calibrator target ring
    drawCircle(
        color = activeColor.copy(alpha = 0.85f),
        radius = reticleRadius,
        center = center,
        style = Stroke(width = 3.5f)
    )

    // Inner target ring
    drawCircle(
        color = activeColor.copy(alpha = 0.45f),
        radius = 26f,
        center = center,
        style = Stroke(width = 2f)
    )

    // Center focal dot
    drawCircle(
        color = activeColor,
        radius = 8f,
        center = center
    )

    // Dynamic Rotating Crosshairs & Directional Pointer matching magnet's in-plane rotation
    val canvasRotationDeg = Math.toDegrees(atan2(-reading.y.toDouble(), -reading.x.toDouble())).toFloat()
    rotate(degrees = canvasRotationDeg, pivot = center) {
        val dashLen = 22f
        val gap = 12f

        // East / Primary Pointer crosshair (elongated with glowing arrow head)
        drawLine(
            color = activeColor,
            start = Offset(center.x + reticleRadius + gap, center.y),
            end = Offset(center.x + reticleRadius + gap + dashLen + 10f, center.y),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
        // Directional pointer chevron
        val tipX = center.x + reticleRadius + gap + dashLen + 10f
        drawLine(
            color = activeColor,
            start = Offset(tipX, center.y),
            end = Offset(tipX - 8f, center.y - 6f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(tipX, center.y),
            end = Offset(tipX - 8f, center.y + 6f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // North crosshair
        drawLine(
            color = activeColor.copy(alpha = 0.5f),
            start = Offset(center.x, center.y - reticleRadius - gap - dashLen),
            end = Offset(center.x, center.y - reticleRadius - gap),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        // South crosshair
        drawLine(
            color = activeColor.copy(alpha = 0.5f),
            start = Offset(center.x, center.y + reticleRadius + gap),
            end = Offset(center.x, center.y + reticleRadius + gap + dashLen),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        // West crosshair
        drawLine(
            color = activeColor.copy(alpha = 0.5f),
            start = Offset(center.x - reticleRadius - gap - dashLen, center.y),
            end = Offset(center.x - reticleRadius - gap, center.y),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}
