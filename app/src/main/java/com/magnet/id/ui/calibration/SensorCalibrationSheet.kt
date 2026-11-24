package com.magnet.id.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magnet.id.data.model.MagneticReading
import com.magnet.id.data.model.SensorCalibration
import com.magnet.id.ui.theme.AmbientGreen
import com.magnet.id.ui.theme.BorderHighlight
import com.magnet.id.ui.theme.DarkBackground
import com.magnet.id.ui.theme.DarkSurface
import com.magnet.id.ui.theme.NorthPoleCyan
import com.magnet.id.ui.theme.SouthPoleRed
import com.magnet.id.ui.theme.TextMuted
import com.magnet.id.ui.theme.TextPrimary
import com.magnet.id.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.pow

data class CalibrationPoint(
    val name: String,
    val normalizedPos: Offset,
    var measuredFlux: Float = 0f,
    var isSampled: Boolean = false
)

@Composable
fun SensorCalibrationSheet(
    currentReading: MagneticReading,
    onCalibrationComplete: (SensorCalibration) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 5 Calibration points on screen (Extreme Top-Left corner, Top-Right, Center, Bottom-Left, Bottom-Right)
    val points = remember {
        mutableStateListOf(
            CalibrationPoint("Top Left", Offset(0.08f, 0.08f)),
            CalibrationPoint("Top Right", Offset(0.85f, 0.08f)),
            CalibrationPoint("Center", Offset(0.50f, 0.50f)),
            CalibrationPoint("Bottom Left", Offset(0.15f, 0.85f)),
            CalibrationPoint("Bottom Right", Offset(0.85f, 0.85f))
        )
    }

    var currentPointIndex by remember { mutableIntStateOf(0) }
    var calculatedSensorPos by remember { mutableStateOf<Offset?>(null) }
    var maxMeasuredFlux by remember { mutableStateOf(0f) }
    var isDone by remember { mutableStateOf(false) }

    // Multi-point trilateration: flux falls off as 1/r^3, so weighting by (flux)^3 pinpoints the chip location
    fun computeTrilateration(): Offset {
        var weightedX = 0f
        var weightedY = 0f
        var totalWeight = 0f

        for (pt in points) {
            val w = pt.measuredFlux.coerceAtLeast(1f).pow(3f)
            weightedX += pt.normalizedPos.x * w
            weightedY += pt.normalizedPos.y * w
            totalWeight += w
            if (pt.measuredFlux > maxMeasuredFlux) {
                maxMeasuredFlux = pt.measuredFlux
            }
        }

        return if (totalWeight > 0.001f) {
            Offset(weightedX / totalWeight, weightedY / totalWeight)
        } else {
            Offset(0.08f, 0.08f)
        }
    }

    // Auto-record when magnet is placed steadily on guided point
    LaunchedEffect(currentReading.magnitude) {
        if (!isDone && currentPointIndex < points.size) {
            val netFlux = currentReading.magnitude
            if (netFlux > 30f) {
                points[currentPointIndex].measuredFlux = netFlux
                points[currentPointIndex].isSampled = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground.copy(alpha = 0.96f))
    ) {
        // Full screen interactive calibration canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (currentPointIndex < points.size) {
                            points[currentPointIndex].measuredFlux = currentReading.magnitude.coerceAtLeast(10f)
                            points[currentPointIndex].isSampled = true
                            if (currentPointIndex < points.size - 1) {
                                currentPointIndex++
                            } else {
                                calculatedSensorPos = computeTrilateration()
                                isDone = true
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Render 5 Target Circles
            points.forEachIndexed { index, pt ->
                val pos = Offset(pt.normalizedPos.x * w, pt.normalizedPos.y * h)
                val isCurrent = index == currentPointIndex && !isDone
                val isSampled = pt.isSampled

                val ringColor = when {
                    isCurrent -> NorthPoleCyan
                    isSampled -> AmbientGreen
                    else -> TextMuted.copy(alpha = 0.35f)
                }

                // Target Outer Ring
                drawCircle(
                    color = ringColor,
                    radius = if (isCurrent) 52f else 36f,
                    center = pos,
                    style = Stroke(width = if (isCurrent) 3.5f else 2f)
                )

                // Target Dot
                drawCircle(
                    color = ringColor,
                    radius = 6f,
                    center = pos
                )
            }

            // Draw Detected Physical Sensor Location once calculated
            calculatedSensorPos?.let { norm ->
                val sensorPixel = Offset(norm.x * w, norm.y * h)

                // Highlighting beacon around detected physical chip
                drawCircle(
                    color = SouthPoleRed.copy(alpha = 0.35f),
                    radius = 80f,
                    center = sensorPixel,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = SouthPoleRed,
                    radius = 55f,
                    center = sensorPixel,
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = SouthPoleRed,
                    radius = 12f,
                    center = sensorPixel
                )
            }
        }

        // TOP BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SENSOR SPOT CALIBRATION",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Locating physical magnetometer chip on this device",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = TextPrimary
                )
            }
        }

        // BOTTOM CONTROLS & INSTRUCTIONS CARD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface.copy(alpha = 0.94f))
                .border(1.dp, BorderHighlight, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isDone) {
                Text(
                    text = "Step ${currentPointIndex + 1} of 5: Place button magnet on ${points[currentPointIndex].name}",
                    color = NorthPoleCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live Flux: %.1f µT • Tap 'Record Point' or tap target".format(Locale.US, currentReading.magnitude),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        points[currentPointIndex].measuredFlux = currentReading.magnitude.coerceAtLeast(10f)
                        points[currentPointIndex].isSampled = true
                        if (currentPointIndex < points.size - 1) {
                            currentPointIndex++
                        } else {
                            calculatedSensorPos = computeTrilateration()
                            isDone = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NorthPoleCyan)
                ) {
                    Text("Record Point", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                // Trilateration complete
                calculatedSensorPos?.let { norm ->
                    Text(
                        text = "Physical Sensor Located!",
                        color = AmbientGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Normalized position: (X: %.2f, Y: %.2f)".format(Locale.US, norm.x, norm.y),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Target reticle will snap directly over the chip for max sensitivity.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onCalibrationComplete(
                                SensorCalibration(
                                    normalizedX = norm.x,
                                    normalizedY = norm.y,
                                    isCalibrated = true,
                                    peakFluxU = maxMeasuredFlux
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmbientGreen)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Snap Target to Sensor Spot", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
