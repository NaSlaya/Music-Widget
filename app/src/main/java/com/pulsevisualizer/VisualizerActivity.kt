package com.pulsevisualizer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
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

private const val VISUALIZER_COUNT = 6

@Composable
fun FullScreenVisualizer() {

    val media by MediaRepository.media.collectAsState()

    var phase by remember {
        mutableFloatStateOf(0f)
    }

    var visualizer by remember {
        mutableIntStateOf(0)
    }

    var swipeDistance by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(media.playing, media.title) {

        while (true) {

            phase += if (media.playing) {
                0.045f
            } else {
                0.012f
            }

            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF19112E),
                        Color(0xFF08070D),
                        Color.Black
                    )
                )
            )
            .pointerInput(Unit) {

                detectHorizontalDragGestures(

                    onDragStart = {
                        swipeDistance = 0f
                    },

                    onHorizontalDrag = { _, amount ->
                        swipeDistance += amount
                    },

                    onDragEnd = {

                        if (swipeDistance < -100f) {

                            visualizer =
                                (visualizer + 1) %
                                    VISUALIZER_COUNT

                        } else if (swipeDistance > 100f) {

                            visualizer =
                                (visualizer - 1 +
                                    VISUALIZER_COUNT) %
                                    VISUALIZER_COUNT
                        }

                        swipeDistance = 0f
                    }
                )
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            when (visualizer) {

                0 -> drawNeonBars(
                    phase = phase,
                    playing = media.playing
                )

                1 -> drawCircularSpectrum(
                    phase = phase,
                    playing = media.playing
                )

                2 -> drawWaveRibbons(
                    phase = phase,
                    playing = media.playing
                )

                3 -> drawParticles(
                    phase = phase,
                    playing = media.playing
                )

                4 -> drawPulseOrb(
                    phase = phase,
                    playing = media.playing
                )

                5 -> drawMirrorSpectrum(
                    phase = phase,
                    playing = media.playing
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(28.dp),
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
                color = Color(0xFFB8B1C3),
                fontSize = 16.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {

                repeat(VISUALIZER_COUNT) { index ->

                    Box(
                        modifier = Modifier
                            .size(
                                if (index == visualizer) {
                                    8.dp
                                } else {
                                    5.dp
                                }
                            )
                            .clip(CircleShape)
                            .background(
                                if (index == visualizer) {
                                    Color(0xFFB77CFF)
                                } else {
                                    Color(0xFF45404F)
                                }
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        MediaRepository.previous()
                    },
                    modifier = Modifier.size(64.dp)
                ) {

                    Text(
                        text = "⏮",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }

                Box(
                    modifier = Modifier
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
                    contentAlignment = Alignment.Center
                ) {

                    IconButton(
                        onClick = {
                            MediaRepository
                                .togglePlayPause()
                        }
                    ) {

                        Text(
                            text =
                                if (media.playing) {
                                    "Ⅱ"
                                } else {
                                    "▶"
                                },
                            color = Color.White,
                            fontSize = 30.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        MediaRepository.next()
                    },
                    modifier = Modifier.size(64.dp)
                ) {

                    Text(
                        text = "⏭",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = "SWIPE TO CHANGE VISUALIZER",
                color = Color(0xFF756A84),
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
        }
    }
}


/* ================================================= */
/* 1 — NEON BARS */
/* ================================================= */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeonBars(
    phase: Float,
    playing: Boolean
) {

    val bars = 64
    val gap = 5f

    val barWidth =
        (size.width - gap * (bars + 1)) /
            bars

    val centerY =
        size.height * 0.42f

    for (i in 0 until bars) {

        val n =
            i.toFloat() / bars

        val wave1 =
            (
                0.5f +
                    0.5f *
                    sin(
                        phase * 3.4f +
                            n * 19f
                    )
                ).toFloat()

        val wave2 =
            (
                0.5f +
                    0.5f *
                    sin(
                        phase * 1.6f +
                            n * 37f
                    )
                ).toFloat()

        val envelope =
            sin(n * PI).toFloat()

        val amount =
            if (playing) {

                0.10f +
                    envelope *
                    (
                        wave1 * 0.65f +
                            wave2 * 0.25f
                        )

            } else {
                0.035f
            }

        val height =
            size.height *
                amount *
                0.55f

        val x =
            gap +
                i *
                (barWidth + gap)

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFF48D2),
                    Color(0xFF9C65FF),
                    Color(0xFF43D8FF)
                )
            ),
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(
                        x,
                        centerY - height
                    ),
            size =
                androidx.compose.ui.geometry
                    .Size(
                        barWidth,
                        height
                    ),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(
                        6f,
                        6f
                    )
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF43D8FF),
                    Color(0xFF596CFF),
                    Color(0xFFFF48D2)
                )
            ),
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(
                        x,
                        centerY
                    ),
            size =
                androidx.compose.ui.geometry
                    .Size(
                        barWidth,
                        height
                    ),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(
                        6f,
                        6f
                    )
        )
    }
}


/* ================================================= */
/* 2 — CIRCULAR SPECTRUM */
/* ================================================= */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircularSpectrum(
    phase: Float,
    playing: Boolean
) {

    val centerX =
        size.width / 2f

    val centerY =
        size.height * 0.40f

    val radius =
        size.minDimension * 0.19f

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFF9B6CFF).copy(alpha = 0.45f),
                Color.Transparent
            )
        ),
        radius = radius * 2.3f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    centerX,
                    centerY
                )
    )

    drawCircle(
        color =
            Color(0xFF9B6CFF)
                .copy(alpha = 0.18f),
        radius = radius,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    centerX,
                    centerY
                ),
        style = Stroke(2f)
    )

    val bars = 96

    for (i in 0 until bars) {

        val angle =
            i.toFloat() /
                bars *
                2f *
                PI.toFloat()

        val wave =
            (
                0.5f +
                    0.5f *
                    sin(
                        phase * 2.7f +
                            i * 0.32f
                    )
                ).toFloat()

        val wave2 =
            (
                0.5f +
                    0.5f *
                    cos(
                        phase * 1.4f +
                            i * 0.17f
                    )
                ).toFloat()

        val length =
            if (playing) {

                20f +
                    wave * 70f +
                    wave2 * 25f

            } else {
                12f
            }

        val inner =
            radius + 10f

        val outer =
            inner + length

        val start =
            androidx.compose.ui.geometry
                .Offset(
                    (
                        centerX +
                            cos(angle) *
                            inner
                        ).toFloat(),
                    (
                        centerY +
                            sin(angle) *
                            inner
                        ).toFloat()
                )

        val end =
            androidx.compose.ui.geometry
                .Offset(
                    (
                        centerX +
                            cos(angle) *
                            outer
                        ).toFloat(),
                    (
                        centerY +
                            sin(angle) *
                            outer
                        ).toFloat()
                )

        drawLine(
            brush = Brush.linearGradient(
                listOf(
                    Color(0xFF45DEFF),
                    Color(0xFF966BFF),
                    Color(0xFFFF52D7)
                )
            ),
            start = start,
            end = end,
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = Color(0xFF050509),
        radius = radius * 0.72f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    centerX,
                    centerY
                )
    )

    drawCircle(
        color = Color(0xFFBD82FF),
        radius = 8f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    centerX,
                    centerY
                )
    )
}


/* ================================================= */
/* 3 — WAVE RIBBONS */
/* ================================================= */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveRibbons(
    phase: Float,
    playing: Boolean
) {

    val waveCount = 7

    for (wave in 0 until waveCount) {

        val path = Path()

        val baseY =
            size.height *
                (
                    0.18f +
                        wave * 0.085f
                    )

        for (point in 0..120) {

            val x =
                size.width *
                    point /
                    120f

            val normalized =
                x / size.width

            val amplitude =
                size.height *
                    if (playing) {
                        0.045f +
                            wave * 0.004f
                    } else {
                        0.020f
                    }

            val y =
                baseY +

                    sin(
                        normalized *
                            (7f + wave) *
                            PI * 2f +
                            phase *
                            (1.3f + wave * 0.13f)
                    ).toFloat() *
                    amplitude +

                    sin(
                        normalized *
                            17f -
                            phase
                    ).toFloat() *
                    amplitude *
                    0.35f

            if (point == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            brush =
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF45E5FF)
                            .copy(alpha = 0.20f),
                        Color(0xFF9A68FF),
                        Color(0xFFFF59D8)
                            .copy(alpha = 0.75f),
                        Color.Transparent
                    )
                ),
            style =
                Stroke(
                    width = 4f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
        )
    }
}


/* ================================================= */
/* 4 — PARTICLE FIELD */
/* ================================================= */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    phase: Float,
    playing: Boolean
) {

    val particles = 180

    for (i in 0 until particles) {

        val seed =
            (i * 97 + 13) % 1000

        val baseX =
            (seed % 100) / 100f

        val baseY =
            ((seed / 10) % 100) / 100f

        val movement =
            if (playing) {

                sin(
                    phase * 0.8f +
                        i * 0.17f
                ).toFloat() *
                    0.025f

            } else {
                0f
            }

        val x =
            (
                baseX +
                    movement
                ).let {
                    ((it % 1f) + 1f) % 1f
                } *
                size.width

        val y =
            (
                baseY +
                    sin(
                        phase * 0.6f +
                            i * 0.31f
                    ).toFloat() *
                    if (playing) {
                        0.035f
                    } else {
                        0.008f
                    }
           
