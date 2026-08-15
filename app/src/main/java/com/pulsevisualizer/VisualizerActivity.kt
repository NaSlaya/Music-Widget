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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class VisualizerActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
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

    val media by
        MediaRepository.media.collectAsState()

    var phase by
        remember {
            mutableFloatStateOf(0f)
        }

    LaunchedEffect(
        media.playing,
        media.title
    ) {

        while (media.playing) {

            phase += 0.035f

            delay(16)
        }
    }

    val trackSeed =
        kotlin.math.abs(
            (
                media.title +
                    media.artist
                ).hashCode()
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF17102B),
                            Color(0xFF07060B),
                            Color.Black
                        )
                    )
                )
    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            val centerX =
                size.width / 2f

            val centerY =
                size.height / 2f

            val radius =
                size.minDimension * 0.28f

            val points = 96

            for (
                i in 0 until points
            ) {

                val angle =
                    2f *
                        PI.toFloat() *
                        i /
                        points

                val normalized =
                    i.toFloat() /
                        points

                val waveA =
                    sin(
                        phase * 4f +
                            normalized *
                            (
                                7f +
                                    trackSeed %
                                    9
                                )
                    )

                val waveB =
                    cos(
                        phase * 2.2f +
                            normalized * 19f
                    )

                val amount =
                    if (media.playing) {
                        1f +
                            waveA * 0.13f +
                            waveB * 0.08f
                    } else {
                        1f
                    }

                val r =
                    radius * amount

                val x =
                    centerX +
                        cos(angle) * r

                val y =
                    centerY +
                        sin(angle) * r

                drawCircle(
                    color =
                        Color(
                            0.55f,
                            0.40f,
                            1f,
                            0.7f
                        ),
                    radius = 3.5f,
                    center =
                        androidx.compose.ui.geometry
                            .Offset(x, y)
                )
            }

            drawCircle(
                color =
                    Color(0xFF9C6CFF)
                        .copy(alpha = 0.16f),
                radius = radius,
                center =
                    androidx.compose.ui.geometry
                        .Offset(
                            centerX,
                            centerY
                        ),
                style =
                    Stroke(2f)
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.BottomCenter
                    )
                    .padding(
                        28.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    media.title,
                color = Color.White,
                fontSize = 28.sp,
                maxLines = 1
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text =
                    media.artist,
                color =
                    Color(0xFFB0AABA),
                fontSize = 16.sp,
                maxLines = 1
            )

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick =
                        {
                            MediaRepository
                                .previous()
                        },
                    modifier =
                        Modifier.size(64.dp)
                ) {
                    Text(
                        "⏮",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(82.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFFAA7CFF),
                                        Color(0xFF586DFF)
                                    )
                                )
                            ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    IconButton(
                        onClick =
                            {
                                MediaRepository
                                    .togglePlayPause()
                            }
                    ) {

                        Text(
                            if (
                                media.playing
                            ) {
                                "Ⅱ"
                            } else {
                                "▶"
                            },
                            color =
                                Color.White,
                            fontSize = 30.sp
                        )
                    }
                }

                IconButton(
                    onClick =
                        {
                            MediaRepository.next()
                        },
                    modifier =
                        Modifier.size(64.dp)
                ) {
                    Text(
                        "⏭",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Text(
                text = "PULSE",
                color =
                    Color(0xFF756A84),
                fontSize = 10.sp,
                letterSpacing = 4.sp
            )
        }
    }
}
