package com.pulsevisualizer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

class VisualizerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setContent {
            PulseTheme {
                FullScreenVisualizer()
            }
        }
    }
}

@Composable
fun FullScreenVisualizer() {

    val media by MediaRepository.media.collectAsState()

    var phase by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(Unit) {
        while (true) {
            phase += 0.08f
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.BottomCenter
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val bars = 72
            val barWidth = size.width / bars

            for (i in 0 until bars) {

                val wave =
                    (sin(
                        phase * 1.7f +
                            i * 0.28f
                    ) + 1f) / 2f

                val barHeight =
                    size.height *
                        (0.03f + wave * 0.45f)

                drawRoundRect(
                    color = Color(0xFF9B7BFF),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = i * barWidth + 2f,
                        y = size.height - barHeight
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = barWidth - 5f,
                        height = barHeight
                    ),
                    cornerRadius =
                        androidx.compose.ui.geometry.CornerRadius(
                            x = 8f,
                            y = 8f
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = media.title,
                color = Color.White,
                fontSize = 28.sp
            )

            Text(
                text = media.artist,
                color = Color.LightGray,
                fontSize = 17.sp
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Text(
                text = "Pulse Visualizer",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}
