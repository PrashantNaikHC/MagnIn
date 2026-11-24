package com.magnet.id.ui.playground.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticPolarity
import com.magnet.id.data.model.MagneticReading
import com.magnet.id.ui.playground.canvas.PlaygroundMode
import java.util.Locale

@Composable
fun MagneticHudOverlay(
    reading: MagneticReading,
    ambientTare: MagneticReading,
    selectedMode: PlaygroundMode,
    isStable: Boolean,
    isCalibrated: Boolean,
    isGrounded: Boolean = false,
    constraint: KinematicConstraint = KinematicConstraint.FREE,
    onModeSelected: (PlaygroundMode) -> Unit,
    onTareClicked: () -> Unit,
    onResetTareClicked: () -> Unit,
    onGroundClicked: () -> Unit = {},
    onResetGroundClicked: () -> Unit = {},
    onConstraintClicked: () -> Unit = {},
    onRecenterClicked: () -> Unit,
    onCalibrateClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP HUD: PURE TRANSLUCENT GRAY TEXT OVERLAY (NO STANDALONE WIDGET BOXES)
        // Hidden on DIAL page: plain blank canvas without numeric magnetic information
        if (selectedMode != PlaygroundMode.DIAL) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Line 1: Primary Flux & Action Text Triggers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.0fµT %s ⟳%.0f°".format(
                            Locale.US,
                            reading.magnitude,
                            when (reading.polarity) {
                                MagneticPolarity.NORTH -> "N"
                                MagneticPolarity.SOUTH -> "S"
                                MagneticPolarity.NEUTRAL -> "AMB"
                            },
                            reading.clockHeadingDeg
                        ),
                        color = Color(0xFFE2E8F0).copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    )

                    // Translucent Gray Action Triggers
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val constraintLabel = when (constraint) {
                            KinematicConstraint.FREE -> "[FREE]"
                            KinematicConstraint.DIAL -> "[DIAL]"
                            KinematicConstraint.LEVER -> "[LEVER]"
                            KinematicConstraint.JOYSTICK -> "[JOY]"
                        }
                        TranslucentTextTrigger(text = constraintLabel, onClick = onConstraintClicked)
                        TranslucentTextTrigger(text = if (isGrounded) "[GND✓]" else "[GROUND]", onClick = onGroundClicked)
                        TranslucentTextTrigger(text = "[TARE]", onClick = onTareClicked)
                        TranslucentTextTrigger(text = if (isCalibrated) "[CAL✓]" else "[CAL]", onClick = onCalibrateClicked)
                        TranslucentTextTrigger(text = "[SNAP]", onClick = onRecenterClicked)
                    }
                }

                // Line 2: 3-Axis Components & Tilt
                Text(
                    text = "Bx: %+.1f  By: %+.1f  Bz: %+.1f µT  |  TILT: %+.0f°".format(
                        Locale.US,
                        reading.x,
                        reading.y,
                        reading.z,
                        reading.tiltAngleDeg
                    ),
                    color = Color(0xFF94A3B8).copy(alpha = 0.50f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Line 3: Ground status, Kinematic mode, Tare baseline and calibration status
                Text(
                    text = "GND: %s  |  MODE: %s  |  TARE: %.0fµT".format(
                        Locale.US,
                        if (isGrounded) "LOCKED" else "OFF",
                        when (constraint) {
                            KinematicConstraint.FREE -> "FREE (2-DOF)"
                            KinematicConstraint.DIAL -> "DIAL (ROTARY 1-DOF)"
                            KinematicConstraint.LEVER -> "LEVER (THROTTLE 1-DOF)"
                            KinematicConstraint.JOYSTICK -> "JOY (8-WAY GATED)"
                        },
                        ambientTare.magnitude
                    ),
                    color = Color(0xFF64748B).copy(alpha = 0.45f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // BOTTOM: HORIZONTALLY SCROLLABLE MODE SELECTOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TranslucentModeText(
                label = "Filings",
                isSelected = selectedMode == PlaygroundMode.IRON_FILINGS,
                onClick = { onModeSelected(PlaygroundMode.IRON_FILINGS) }
            )
            TranslucentModeText(
                label = "Ripples",
                isSelected = selectedMode == PlaygroundMode.FLUX_RIPPLES,
                onClick = { onModeSelected(PlaygroundMode.FLUX_RIPPLES) }
            )
            TranslucentModeText(
                label = "3D Gimbal",
                isSelected = selectedMode == PlaygroundMode.GIMBAL_3D,
                onClick = { onModeSelected(PlaygroundMode.GIMBAL_3D) }
            )
            TranslucentModeText(
                label = "Dial",
                isSelected = selectedMode == PlaygroundMode.DIAL,
                onClick = { onModeSelected(PlaygroundMode.DIAL) }
            )
        }
    }
}

@Composable
private fun TranslucentTextTrigger(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = Color(0xFFCBD5E1).copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
    )
}

@Composable
private fun TranslucentModeText(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isSelected) {
        Color(0xFFF8FAFC).copy(alpha = 0.85f)
    } else {
        Color(0xFF94A3B8).copy(alpha = 0.40f)
    }

    Text(
        text = if (isSelected) "[ $label ]" else label,
        color = textColor,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    )
}
