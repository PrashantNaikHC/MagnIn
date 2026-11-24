package com.magnet.id.ui.playground.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticReading
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * High-precision Retro-Themed Rotational Dial Component.
 * Responds to magnetic precession angle and provides mechanical audio & haptic tick feedback on degree transitions.
 */
@Composable
fun RetroDialView(
    reading: MagneticReading,
    constraint: KinematicConstraint = KinematicConstraint.DIAL,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Haptics & Audio Controllers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val vibrator: Vibrator? = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var currentAngleDeg by remember { mutableFloatStateOf(0f) }
    var lastTickAngle by remember { mutableIntStateOf(0) }
    var lastTickTimestamp by remember { mutableLongStateOf(0L) }

    // Sound & Haptic Tick Player (throttled to avoid buffer overrun while preserving tactile fidelity)
    val triggerTick = remember {
        {
            val now = System.currentTimeMillis()
            if (now - lastTickTimestamp >= 12L) {
                lastTickTimestamp = now

                // Audio Tick: System Click
                try {
                    audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.85f)
                    view.playSoundEffect(SoundEffectConstants.CLICK)
                } catch (_: Exception) {}

                // Haptic Tick
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createOneShot(8L, 120))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(8L)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // React to live magnetic rotation
    LaunchedEffect(reading.clockHeadingDeg, reading.magnitude) {
        // Only track when magnetic signal is distinct from ambient noise (> 2.0 uT in-plane)
        val inPlaneMag = kotlin.math.sqrt(reading.x * reading.x + reading.y * reading.y)
        if (inPlaneMag >= 2.0f) {
            val targetAngle = reading.clockHeadingDeg
            val diff = ((targetAngle - currentAngleDeg + 540f) % 360f) - 180f
            val newAngle = (currentAngleDeg + diff + 360f) % 360f

            val prevRounded = currentAngleDeg.roundToInt()
            val newRounded = newAngle.roundToInt()

            if (prevRounded != newRounded) {
                val stepCount = abs(newRounded - prevRounded)
                if (stepCount in 1..20) {
                    triggerTick()
                }
                lastTickAngle = newRounded
            }
            currentAngleDeg = newAngle
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val dialSizePx = min(constraints.maxWidth, constraints.maxHeight) * 0.78f
        val dialSizeDp = (dialSizePx / context.resources.displayMetrics.density).dp

        Box(
            modifier = Modifier
                .size(dialSizeDp)
                .pointerInput(Unit) {
                    // Also supports direct tactile touch rotation
                    var lastTouchAngle = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            lastTouchAngle = Math.toDegrees(
                                atan2(offset.y - center.y, offset.x - center.x).toDouble()
                            ).toFloat()
                        },
                        onDrag = { change, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val touchAngle = Math.toDegrees(
                                atan2(change.position.y - center.y, change.position.x - center.x).toDouble()
                            ).toFloat()
                            val delta = touchAngle - lastTouchAngle
                            lastTouchAngle = touchAngle

                            val newAngle = (currentAngleDeg + delta + 360f) % 360f
                            val prevDeg = currentAngleDeg.roundToInt()
                            val nextDeg = newAngle.roundToInt()
                            if (prevDeg != nextDeg) {
                                triggerTick()
                            }
                            currentAngleDeg = newAngle
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // 1. Outer Heavy Bakelite & Knurled Bezel Shadow
                drawCircle(
                    color = Color(0xFF070A0F),
                    radius = radius,
                    center = center
                )

                // 2. Brass Bezel Ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD4AF37), // Polished Brass
                            Color(0xFFA67C1E), // Deep Gold
                            Color(0xFF5B4310), // Antiqued Bronze
                            Color(0xFF1E1708)
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius * 0.98f,
                    center = center
                )

                // 3. Knurling teeth around perimeter (36 tactile notches)
                val notchCount = 72
                for (i in 0 until notchCount) {
                    val angleRad = (i * (360f / notchCount)) * (PI.toFloat() / 180f)
                    val p1 = Offset(
                        center.x + (radius * 0.91f) * cos(angleRad),
                        center.y + (radius * 0.91f) * sin(angleRad)
                    )
                    val p2 = Offset(
                        center.x + (radius * 0.97f) * cos(angleRad),
                        center.y + (radius * 0.97f) * sin(angleRad)
                    )
                    drawLine(
                        color = Color(0xFF1B1405),
                        start = p1,
                        end = p2,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }

                // 4. Inner Gunmetal Bezel Trench
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1E242B),
                            Color(0xFF0F1317),
                            Color(0xFF28313A)
                        ),
                        center = center,
                        radius = radius * 0.88f
                    ),
                    radius = radius * 0.88f,
                    center = center
                )

                // 5. Vintage Parchment / Cream Dial Face
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF222933),
                            Color(0xFF151B22),
                            Color(0xFF0D1117)
                        ),
                        center = center,
                        radius = radius * 0.84f
                    ),
                    radius = radius * 0.84f,
                    center = center
                )

                // Inner hairline golden track
                drawCircle(
                    color = Color(0xFFD4AF37).copy(alpha = 0.35f),
                    radius = radius * 0.82f,
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                // 6. Rotating Face: Dial Graduations (Ticks & Numbers)
                rotate(degrees = currentAngleDeg, pivot = center) {
                    // Draw 120 radial tick marks
                    for (deg in 0 until 360 step 3) {
                        val isMajor = deg % 30 == 0
                        val isSemi = deg % 15 == 0 && !isMajor
                        val tickLen = when {
                            isMajor -> radius * 0.12f
                            isSemi -> radius * 0.08f
                            else -> radius * 0.045f
                        }
                        val strokeW = when {
                            isMajor -> 3.5f
                            isSemi -> 2f
                            else -> 1.2f
                        }
                        val tickColor = when {
                            isMajor -> Color(0xFFE2E8F0)
                            isSemi -> Color(0xFFD4AF37)
                            else -> Color(0xFF64748B).copy(alpha = 0.6f)
                        }

                        val angleRad = (deg - 90f) * (PI.toFloat() / 180f)
                        val outerR = radius * 0.81f
                        val innerR = outerR - tickLen

                        val pStart = Offset(
                            center.x + innerR * cos(angleRad),
                            center.y + innerR * sin(angleRad)
                        )
                        val pEnd = Offset(
                            center.x + outerR * cos(angleRad),
                            center.y + outerR * sin(angleRad)
                        )

                        drawLine(
                            color = tickColor,
                            start = pStart,
                            end = pEnd,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }

                    // Vintage Numerals at major 30-degree intervals
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(230, 241, 245, 249)
                        textSize = radius * 0.085f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.MONOSPACE,
                            android.graphics.Typeface.BOLD
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    for (deg in 0 until 360 step 30) {
                        val angleRad = (deg - 90f) * (PI.toFloat() / 180f)
                        val numR = radius * 0.62f
                        val nx = center.x + numR * cos(angleRad)
                        val ny = center.y + numR * sin(angleRad) + (paint.textSize * 0.35f)

                        drawContext.canvas.nativeCanvas.drawText(
                            "$deg°",
                            nx,
                            ny,
                            paint
                        )
                    }

                    // Retro Indicator Needle / Marker on Dial Top
                    val needlePath = Path().apply {
                        val tip = Offset(center.x, center.y - radius * 0.78f)
                        val leftBase = Offset(center.x - 7f, center.y - radius * 0.40f)
                        val rightBase = Offset(center.x + 7f, center.y - radius * 0.40f)
                        moveTo(tip.x, tip.y)
                        lineTo(rightBase.x, rightBase.y)
                        lineTo(leftBase.x, leftBase.y)
                        close()
                    }
                    drawPath(
                        path = needlePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFEF4444), // North Pole Ruby Red
                                Color(0xFFB91C1C)
                            )
                        )
                    )
                }

                // 7. Center Spindle Hub & Machined Cap
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFD4AF37),
                            Color(0xFF785718),
                            Color(0xFF1E1708)
                        ),
                        center = center,
                        radius = radius * 0.28f
                    ),
                    radius = radius * 0.28f,
                    center = center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF334155),
                            Color(0xFF0F172A)
                        ),
                        center = center,
                        radius = radius * 0.21f
                    ),
                    radius = radius * 0.21f,
                    center = center
                )

                // Center Specular Glow Jewel
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFEF4444).copy(alpha = 0.9f),
                            Color(0xFF991B1B).copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius * 0.12f
                    ),
                    radius = radius * 0.12f,
                    center = center
                )

                // Top Fixed Reference Index Marker (Precision Notch at 12 o'clock)
                val fixedMarkerPath = Path().apply {
                    val pTop = Offset(center.x, center.y - radius * 0.88f)
                    val pLeft = Offset(center.x - 9f, center.y - radius * 0.96f)
                    val pRight = Offset(center.x + 9f, center.y - radius * 0.96f)
                    moveTo(pTop.x, pTop.y)
                    lineTo(pRight.x, pRight.y)
                    lineTo(pLeft.x, pLeft.y)
                    close()
                }
                drawPath(
                    path = fixedMarkerPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF38BDF8), // Electric Cyan
                            Color(0xFF0284C7)
                        )
                    )
                )
            }
        }
    }
}
