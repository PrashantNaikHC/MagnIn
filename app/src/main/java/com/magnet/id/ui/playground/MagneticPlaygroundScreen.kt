package com.magnet.id.ui.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.magnet.id.ui.calibration.SensorCalibrationSheet
import com.magnet.id.ui.playground.canvas.MagneticFieldCanvas
import com.magnet.id.ui.playground.canvas.PlaygroundMode
import com.magnet.id.ui.playground.components.MagneticHudOverlay
import com.magnet.id.ui.playground.components.RetroDialView
import com.magnet.id.ui.theme.DarkBackground
import com.magnet.id.ui.theme.DarkSurface
import com.magnet.id.ui.theme.NorthPoleCyan
import com.magnet.id.ui.theme.SouthPoleRed
import com.magnet.id.ui.theme.SouthPoleAmber
import com.magnet.id.ui.theme.TextMuted
import com.magnet.id.ui.theme.TextPrimary

@Composable
fun MagneticPlaygroundScreen(
    viewModel: MagneticPlaygroundViewModel,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.startListening()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopListening()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val netReading by viewModel.netReading.collectAsStateWithLifecycle()
    val ambientTare by viewModel.ambientTare.collectAsStateWithLifecycle()
    val isStable by viewModel.isStable.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val magnetCenter by viewModel.magnetCenter.collectAsStateWithLifecycle()
    val hasHardwareSensor by viewModel.hasHardwareSensor.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val groundReference by viewModel.groundReference.collectAsStateWithLifecycle()
    val kinematicConstraint by viewModel.kinematicConstraint.collectAsStateWithLifecycle()
    val showCalibrationSheet by viewModel.showCalibrationSheet.collectAsStateWithLifecycle()

    var simulatedZ by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        // High-performance dynamic canvas animation
        MagneticFieldCanvas(
            reading = netReading,
            mode = selectedMode,
            magnetCenter = magnetCenter,
            calibration = calibration,
            constraint = kinematicConstraint,
            onDragMove = { dragAmount, width, height ->
                viewModel.moveMagnetCenter(dragAmount, width, height)
            },
            onTapMove = { tapOffset, width, height ->
                viewModel.setMagnetCenter(tapOffset, width, height)
            },
            onDoubleTapReset = {
                viewModel.snapToSensor(widthPx, heightPx)
            }
        )

        // Retro Mechanical Dial for DIAL mode
        if (selectedMode == PlaygroundMode.DIAL) {
            RetroDialView(
                reading = netReading,
                constraint = kinematicConstraint
            )
        }

        // Superimposed telemetry HUD and controls
        MagneticHudOverlay(
            reading = netReading,
            ambientTare = ambientTare,
            selectedMode = selectedMode,
            isStable = isStable,
            isCalibrated = calibration.isCalibrated,
            isGrounded = groundReference.isGrounded,
            constraint = kinematicConstraint,
            onModeSelected = { mode ->
                viewModel.selectMode(mode)
            },
            onTareClicked = {
                viewModel.tare()
            },
            onResetTareClicked = {
                viewModel.resetTare()
            },
            onGroundClicked = {
                viewModel.groundCurrentOrientation()
            },
            onResetGroundClicked = {
                viewModel.resetGroundOrientation()
            },
            onConstraintClicked = {
                viewModel.cycleKinematicConstraint()
            },
            onRecenterClicked = {
                viewModel.snapToSensor(widthPx, heightPx)
            },
            onCalibrateClicked = {
                viewModel.openCalibration()
            }
        )

        // Sensor Calibration Wizard Screen
        if (showCalibrationSheet) {
            SensorCalibrationSheet(
                currentReading = netReading,
                onCalibrationComplete = { newCal ->
                    viewModel.applyCalibration(newCal, widthPx, heightPx)
                },
                onDismiss = {
                    viewModel.dismissCalibration()
                }
            )
        }

        // Emulator / Sensor fallback simulation toolbar
        if (!hasHardwareSensor) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface.copy(alpha = 0.95f))
                    .border(1.dp, SouthPoleAmber, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "EMULATOR DEMO MODE (No physical sensor)",
                        color = SouthPoleAmber,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Slide to simulate magnet polarity & flux: %.0f µT".format(simulatedZ),
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = simulatedZ,
                        onValueChange = { value ->
                            simulatedZ = value
                            viewModel.injectSimulatedField(
                                x = value * 0.3f,
                                y = value * 0.2f,
                                z = value
                            )
                        },
                        valueRange = -300f..300f,
                        colors = SliderDefaults.colors(
                            thumbColor = if (simulatedZ >= 0) NorthPoleCyan else SouthPoleRed,
                            activeTrackColor = if (simulatedZ >= 0) NorthPoleCyan else SouthPoleRed
                        )
                    )
                }
            }
        }
    }
}
