package com.magnet.id.ui.playground.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.magnet.id.data.model.KinematicConstraint
import com.magnet.id.data.model.MagneticReading
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the true 3D GLB model of the user's wooden dowel and neodymium button magnet
 * using hardware-accelerated WebGL with PBR metallic reflections and directional shadows.
 *
 * Supports real-time orientation tracking, all 4 kinematic constraint modes (FREE, DIAL, LEVER, JOYSTICK),
 * and touch-orbiting to inspect the 3D model from any vantage angle.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Stick3DModelView(
    reading: MagneticReading,
    constraint: KinematicConstraint,
    modifier: Modifier = Modifier
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Compute constrained tilt and direction matching our physical coordinate model
    val netFlux = reading.magnitude.coerceAtLeast(0.01f)
    val rawInPlaneFlux = sqrt(reading.x * reading.x + reading.y * reading.y)
    val rawDirX = if (rawInPlaneFlux > 0.5f) -reading.x / rawInPlaneFlux else 0f
    val rawDirY = if (rawInPlaneFlux > 0.5f) -reading.y / rawInPlaneFlux else 0f

    val (dirX, dirY, tiltRatio) = when (constraint) {
        KinematicConstraint.FREE -> {
            val ratio = if (netFlux < 25f || rawInPlaneFlux < 3.0f) 0f else (rawInPlaneFlux / netFlux).coerceIn(0f, 0.96f)
            Triple(rawDirX, rawDirY, ratio)
        }
        KinematicConstraint.DIAL -> {
            val fixedRatio = if (netFlux < 25f) 0f else 0.5735f // sin(35°)
            Triple(rawDirX, rawDirY, fixedRatio)
        }
        KinematicConstraint.LEVER -> {
            val pitchFlux = abs(reading.y)
            val pitchDirY = if (reading.y >= 0f) -1f else 1f
            val ratio = if (netFlux < 25f || pitchFlux < 3.0f) 0f else (pitchFlux / netFlux).coerceIn(0f, 0.96f)
            Triple(0f, pitchDirY, ratio)
        }
        KinematicConstraint.JOYSTICK -> {
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
                val rawAngleDeg = ((Math.toDegrees(kotlin.math.atan2(rawDirY.toDouble(), rawDirX.toDouble())) % 360) + 360) % 360
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

    val tiltRad = asin(tiltRatio)

    // Send orientation updates to Three.js in real time
    LaunchedEffect(isLoaded, tiltRad, dirX, dirY) {
        if (isLoaded) {
            val js = String.format(Locale.US, "window.updateOrientation(%.4f, %.4f, %.4f);", tiltRad, dirX, dirY)
            webViewRef?.evaluateJavascript(js, null)
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_NO_CACHE
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                    }
                }
                loadUrl("file:///android_asset/three/stick_viewer.html")
                webViewRef = this
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
