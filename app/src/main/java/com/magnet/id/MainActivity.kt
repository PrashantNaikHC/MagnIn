package com.magnet.id

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.magnet.id.ui.playground.MagneticPlaygroundScreen
import com.magnet.id.ui.playground.MagneticPlaygroundViewModel
import com.magnet.id.ui.theme.DarkBackground
import com.magnet.id.ui.theme.MagnetIDTheme

class MainActivity : ComponentActivity() {

    private val playgroundViewModel: MagneticPlaygroundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MagnetIDTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MagneticPlaygroundScreen(
                        viewModel = playgroundViewModel
                    )
                }
            }
        }
    }
}
