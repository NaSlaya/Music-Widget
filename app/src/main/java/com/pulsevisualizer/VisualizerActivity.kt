package com.pulsevisualizer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    var visualizerIndex by remember {
        mutableIntStateOf(0)
    }

    var dragAmount by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(media.playing) {

        while (true) {

            if (media.playing) {
                phase += 0.045f
            } else {
                phase += 0.012f
            }

            delay(16)
        }
    }

    val transitionAmount by animateFloatAsState(
        targetValue = visualizerIndex.toFloat(),
        animationSpec = tween(
            durationMillis = 420,
            easing = FastOutSlowInEasing
        ),
        label = "visualizerTransition"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF171126),
                        Color(0xFF080710),
                        Color(0xFF020204)
                    )
                )
            )
            .pointerInput(Unit) {

                detectHorizontalDragGestures(

                    onDragStart = {
                        dragAmount = 0f
                    },

                    onHorizontalDrag = { _, amount ->
                        dragAmount += amount
                    },

                    onDragEnd = {

                        if (abs(dragAmount) > 100f) {

                            if (dragAmount < 0f) {
                                visualizerIndex =
                                    (visualizerIndex + 1) % VISUALIZER_COUNT
                            } else {
                                visualizerIndex =
                                    (visualizerIndex - 1 + VISUALIZER_COUNT) %
                                        VISUALIZER_COUNT
                            }
                        }

                        dragAmount = 0f
                    }
                )
            }
    ) {

        VisualizerBackground(
            type = visualizerIndex,
            phase = phase,
            playing = media.playing,
            transitionAmount = transitionAmount
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    bottom = 32.dp
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
                color = Color(0xFFB7B2C2),
                fontSize = 17.sp,
                maxLines = 1
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            VisualizerIndicator(
                current = visualizerIndex
            )

            Spacer(
                modifier = Modifier.height(18.dp)
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
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFFB66DFF),
                                    Color(0xFF596FFF)
                                )
                            )
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
                modifier = Modifier.height(12.dp)
            )

            Text(
                text = "SWIPE LEFT / RIGHT TO CHANGE VISUALIZER",
                color = Color(0xFF77727F),
                fontSize = 10.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun VisualizerIndicator(
    current: Int
) {

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        repeat(VISUALIZER_COUNT) { index ->

            Box(
                modifier = Modifier
                    .size(
                        if (index == current) 8.dp else 5.dp
                    )
                    .clip(CircleShape)
                    .background(
                        if (index == current) {
                            Color(0xFFB77CFF)
                        } else {
                            Color(0xFF494451)
                        }
                    )
            )
        }
    }
}

@Composable
private fun VisualizerBackground(
    type: Int,
    phase: Float,
    playing: Boolean,
    transitionAmount: Float
) {

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {

        when (type) {

            0 -> drawNeonSpectrum(
                phase = phase,
                playing = playing
            )

            1 -> drawRadialSpectrum(
                phase = phase,
                playing = playing
            )

            2 -> drawWaveRibbons(
                phase = phase,
                playing = playing
            )

            3 -> drawParticleField(
                phase = phase,
                playing = playing
            )

            4 -> drawPulseOrb(
                phase = phase,
                playing = playing
            )

            5 -> drawMirrorSpectrum(
                phase = phase,
                playing = playing
            )
        }
    }
}

/* ------------------------------------------------ */
/* 1. NEON SPECTRUM */
/* ------------------------------------------------ */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeonSpectrum(
    phase: Float,
    playing: Boolean
) {

    val bars = 72
    val gap = size.width * 0.004f

    val barWidth =
        (size.width - gap * (bars + 1)) / bars

    val centerY = size.height * 0.47f

    for (i in 0 until bars) {

        val n = i.toFloat() / bars

        val waveA =
            0.5f +
                0.5f *
                sin(
                    phase * 3.4f +
                        n * 14f
                )

        val waveB =
            0.5f +
                0.5f *
                sin(
                    phase * 1.7f +
                        n * 28f
                )

        val envelope =
            sin(n * PI).toFloat()

        val amount =
            if (playing) {
                0.12f +
                    envelope * (
                        waveA * 0.65f +
                            waveB * 0.25f
                        )
            } else {
                0.04f
            }

        val barHeight =
            size.height * amount * 0.58f

        val x =
            gap + i * (barWidth + gap)

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFF4FD8),
                    Color(0xFF9A6CFF),
                    Color(0xFF4E7BFF),
                    Color(0xFF37DFFF)
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                x,
                centerY - barHeight
            ),
            size = androidx.compose.ui.geometry.Size(
                barWidth,
                barHeight
            ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth,
                    barWidth
                )
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF37DFFF),
                    Color(0xFF596FFF),
                    Color(0xFFFF4FD8)
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                x,
                centerY
            ),
            size = androidx.compose.ui.geometry.Size(
                barWidth,
                barHeight
            ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth,
                    barWidth
                )
        )
    }
}

/* ------------------------------------------------ */
/* 2. RADIAL SPECTRUM */
/* ------------------------------------------------ */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadialSpectrum(
    phase: Float,
    playing: Boolean
) {

    val cx = size.width / 2f
    val cy = size.height * 0.43f

    val radius =
        minOf(size.width, size.height) * 0.20f

    val bars = 100

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFF9E6CFF).copy(alpha = 0.35f),
                Color.Transparent
            )
        ),
        radius = radius * 2.2f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
    )

    drawCircle(
        color = Color(0xFF8F6CFF).copy(alpha = 0.12f),
        radius = radius,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        ),
        style = Stroke(2f)
    )

    for (i in 0 until bars) {

        val angle =
            (i.toFloat() / bars) *
                (2f * PI.toFloat())

        val wave =
            0.5f +
                0.5f *
                sin(
                    phase * 2.8f +
                        i * 0.31f
                )

        val secondary =
            0.5f +
                0.5f *
                sin(
                    phase * 1.1f +
                        i * 0.12f
                )

        val length =
            if (playing) {
                25f +
                    wave * 75f +
                    secondary * 30f
            } else {
                15f
            }

        val inner =
            radius + 8f

        val outer =
            inner + length

        val startX =
            cx + cos(angle) * inner

        val startY =
            cy + sin(angle) * inner

        val endX =
            cx + cos(angle) * outer

        val endY =
            cy + sin(angle) * outer

        drawLine(
            brush = Brush.linearGradient(
                listOf(
                    Color(0xFF4FD8FF),
                    Color(0xFF8B6CFF),
                    Color(0xFFFF55D6)
                )
            ),
            start = androidx.compose.ui.geometry.Offset(
                startX.toFloat(),
                startY.toFloat()
            ),
            end = androidx.compose.ui.geometry.Offset(
                endX.toFloat(),
                endY.toFloat()
            ),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = Color(0xFF050509),
        radius = radius * 0.75f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
    )

    drawCircle(
        color = Color(0xFFB77CFF).copy(alpha = 0.8f),
        radius = radius * 0.08f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
    )
}

/* ------------------------------------------------ */
/* 3. WAVE RIBBONS */
/* ------------------------------------------------ */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveRibbons(
    phase: Float,
    playing: Boolean
) {

    val waves = 7

    for (wave in 0 until waves) {

        val path = Path()

        val yBase =
            size.height * (
                0.20f +
                    wave * 0.085f
                )

        for (xIndex in 0..120) {

            val x =
                size.width *
                    xIndex /
                    120f

            val normalized =
                x / size.width

            val amplitude =
                size.height *
                    if (playing) {
                        0.055f +
                            wave * 0.004f
                    } else {
                        0.025f
                    }

            val frequency =
                7f +
                    wave * 0.8f

            val y =
                yBase +
                    sin(
                        normalized * frequency * PI * 2 +
                            phase * (1.5f + wave * 0.12f)
                    ).toFloat() *
                    amplitude +

                    sin(
                        normalized * 15f -
                            phase * 0.8f
                    ).toFloat() *
                    amplitude *
                    0.35f

            if (xIndex == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF4CE6FF).copy(alpha = 0.25f),
                    Color(0xFF9C6CFF),
                    Color(0xFFFF5EDB).copy(alpha = 0.75f),
                    Color.Transparent
                )
            ),
            style = Stroke(
                width = 4f + wave * 0.4f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/* ------------------------------------------------ */
/* 4. PARTICLE FIELD */
/* ------------------------------------------------ */

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticleField(
    phase: Float,
    playing: Boolean
) {

    val particleCount = 170

    for (i in 0 until particleCount) {

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
                ).toFloat() * 0.025f
            } else {
                0f
            }

        val x =
            (baseX + movement)
                .mod(1f) *
                size.width

        val y =
            (
                baseY +
                    sin(
                        phase * 0.6f +
                            i * 0.31f
                    ).toFloat() *
                    if (playing) 0.035f else 0.008f
                )
                .coerceIn(0f, 1f) *
                size.height

        val pulse =
            0.5f +
                0.5f *
                sin(
                    phase * 2f +
                        i * 0.43f
                )

        val radius =
            if (playing) {
                1.5f +
                    pulse * 4f
            } else {
                2f
            }

        val color =
            when (i % 4) {
                0 -> Color(0xFF55DFFF)
                1 -> Color(0xFF9A70FF)
                2 -> Color(0xFFFF62D9)
                else -> Color(0xFFFFFFFF)
            }

        drawCircle(
            color = color.copy(
                alpha =
                    0.25f +
                        pulse * 0.65f
            ),
            radius = radius,
            center = androidx.compose.ui.geometry.Offset(
                x,
                y
            )
        )
    }

    val cx = size.width / 2f
    val cy = size.height * 0.42f

    val orbPulse =
        if (playing) {
            1f +
                (
                    sin(phase * 3.5f) *
                        0.08f
                    ).toFloat()
        } else {
            1f
        }

    drawCir
