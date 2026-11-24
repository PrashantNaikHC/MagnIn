package com.magnet.id.ui.playground.components

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.SoundEffectConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticReading
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Minimalist & Elegant Rotary Dial Component.
 * - Suppresses internal degree text and center concentric circles.
 * - Displays a clean degree readout label outside the dial.
 * - Retains mechanical audio & haptic tick feedback every 2 degrees.
 */
@Composable
fun RetroDialView(
    reading: MagneticReading,
    constraint: KinematicConstraint = KinematicConstraint.DIAL,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Audio & Haptics
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
    var lastTickTimestamp by remember { mutableLongStateOf(0L) }

    // Sound & Haptic Tick Player (2-degree interval)
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
        val inPlaneMag = kotlin.math.sqrt(reading.x * reading.x + reading.y * reading.y)
        if (inPlaneMag >= 2.0f) {
            val targetAngle = reading.clockHeadingDeg
            val diff = ((targetAngle - currentAngleDeg + 540f) % 360f) - 180f
            val newAngle = (currentAngleDeg + diff + 360f) % 360f

            // Trigger tick on every 2-degree detent
            val prevDetent = (currentAngleDeg / 2f).toInt()
            val newDetent = (newAngle / 2f).toInt()

            if (prevDetent != newDetent) {
                val stepCount = abs(newDetent - prevDetent)
                if (stepCount in 1..10) {
                    triggerTick()
                }
            }
            currentAngleDeg = newAngle
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val dialSizePx = min(constraints.maxWidth, constraints.maxHeight) * 0.65f
        val dialSizeDp = (dialSizePx / context.resources.displayMetrics.density).dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // EXTERNAL DEGREE LABEL (Positioned outside the dial face)
            Text(
                text = "${currentAngleDeg.roundToInt()}°",
                color = Color(0xFFF1F5F9).copy(alpha = 0.90f),
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            // MINIMALIST ELEGANT DIAL DISC
            Box(
                modifier = Modifier
                    .size(dialSizeDp)
                    .pointerInput(Unit) {
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
                                val prevDetent = (currentAngleDeg / 2f).toInt()
                                val nextDetent = (newAngle / 2f).toInt()
                                if (prevDetent != nextDetent) {
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

                    // 1. Subtle Outer Drop Ring
                    drawCircle(
                        color = Color(0xFF0F172A).copy(alpha = 0.6f),
                        radius = radius,
                        center = center
                    )

                    // 2. Pure Minimalist Dial Face: Dark Slate Matte
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E293B), // Slate 800
                                Color(0xFF0F172A)  // Slate 900
                            ),
                            center = center,
                            radius = radius * 0.95f
                        ),
                        radius = radius * 0.95f,
                        center = center
                    )

                    // 3. Hairline Outer Boundary Track
                    drawCircle(
                        color = Color(0xFF334155).copy(alpha = 0.5f),
                        radius = radius * 0.95f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )

                    // 4. Hairline Inner Track Ring
                    drawCircle(
                        color = Color(0xFF334155).copy(alpha = 0.3f),
                        radius = radius * 0.82f,
                        center = center,
                        style = Stroke(width = 1.0f)
                    )

                    // 5. Fixed Subtle Index Notch at 12 o'clock (External reference)
                    drawLine(
                        color = Color(0xFF38BDF8), // Electric Cyan
                        start = Offset(center.x, center.y - radius * 0.97f),
                        end = Offset(center.x, center.y - radius * 0.89f),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )

                    // 6. Rotating Minimalist Face
                    rotate(degrees = currentAngleDeg, pivot = center) {
                        // Subtle perimeter tick dashes (every 30 degrees only)
                        for (deg in 0 until 360 step 30) {
                            val angleRad = (deg - 90f) * (PI.toFloat() / 180f)
                            val outerR = radius * 0.87f
                            val innerR = radius * 0.81f

                            val pStart = Offset(
                                center.x + innerR * cos(angleRad),
                                center.y + innerR * sin(angleRad)
                            )
                            val pEnd = Offset(
                                center.x + outerR * cos(angleRad),
                                center.y + outerR * sin(angleRad)
                            )

                            drawLine(
                                color = Color(0xFF64748B).copy(alpha = 0.5f),
                                start = pStart,
                                end = pEnd,
                                strokeWidth = 1.8f,
                                cap = StrokeCap.Round
                            )
                        }

                        // Single Minimalist Indicator Line (Ruby Red pointing to current orientation)
                        val indicatorOuter = radius * 0.88f
                        val indicatorInner = radius * 0.45f
                        drawLine(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFEF4444), // North Red
                                    Color(0xFFEF4444).copy(alpha = 0.4f)
                                )
                            ),
                            start = Offset(center.x, center.y - indicatorInner),
                            end = Offset(center.x, center.y - indicatorOuter),
                            strokeWidth = 3.5f,
                            cap = StrokeCap.Round
                        )

                        // Subtle clean center pivot point
                        drawCircle(
                            color = Color(0xFFEF4444),
                            radius = 4f,
                            center = center
                        )
                    }
                }
            }
        }
    }
}
