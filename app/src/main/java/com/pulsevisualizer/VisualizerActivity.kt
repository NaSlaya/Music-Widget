package com.pulsevisualizer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
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

    LaunchedEffect(media.playing) {
        while (media.playing) {
            phase += 0.08f
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            val bars = 80
            val gap = 4f
            val barWidth =
                (size.width - gap * (bars + 1)) / bars

            for (i in 0 until bars) {

                val wave1 =
                    abs(
                        sin(
                            phase * 1.8f +
                                i * 0.21f
                        )
                    )

                val wave2 =
                    abs(
                        sin(
                            phase * 0.72f +
                                i * 0.07f
                        )
                    )

                val wave3 =
                    abs(
                        sin(
                            phase * 0.35f +
                                i * 0.13f
                        )
                    )

                val amount =
                    if (media.playing) {
                        0.04f +
                            wave1 * 0.34f +
                            wave2 * 0.22f +
                            wave3 * 0.18f
                    } else {
                        0.025f
                    }

                val barHeight =
                    size.height * amount

                val x =
                    gap +
                        i * (barWidth + gap)

                val y =
                    (size.height - barHeight) / 2f

                drawRect(
                    color = Color(
                        red = 0.45f,
                        green = 0.32f,
                        blue = 1f,
                        alpha = 0.85f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x,
                        y
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth,
                        barHeight
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    bottom = 35.dp
                )
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = media.title,
                color = Color.White,
                fontSize = 27.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = media.artist,
                color = Color.LightGray,
                fontSize = 17.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        MediaRepository.previous()
                    },
                    modifier = Modifier.size(62.dp)
                ) {
                    Text(
                        text = "⏮",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }

                IconButton(
                    onClick = {
                        MediaRepository.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(
                            Color(0xFF9B7BFF)
                        )
                ) {
                    Text(
                        text =
                            if (media.playing) {
                                "Ⅱ"
                            } else {
                                "▶"
                            },
                        color = Color.White,
                        fontSize = 28.sp
                    )
                }

                IconButton(
                    onClick = {
                        MediaRepository.next()
                    },
                    modifier = Modifier.size(62.dp)
                ) {
                    Text(
                        text = "⏭",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "PULSE VISUALIZER",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
    }
}
