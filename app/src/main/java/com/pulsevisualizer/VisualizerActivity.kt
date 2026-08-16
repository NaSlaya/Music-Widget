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
import androidx.compose.ui.graphics.drawscope.DrawScope
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

    var horizontalDrag by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(media.playing) {

        while (true) {

            phase += if (media.playing) {
                0.045f
            } else {
                0.008f
            }

            delay(16L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF21143D),
                        Color(0xFF0C0914),
                        Color.Black
                    )
                )
            )
            .pointerInput(Unit) {

                detectHorizontalDragGestures(

                    onDragStart = {
                        horizontalDrag = 0f
                    },

                    onHorizontalDrag = { _, amount ->
                        horizontalDrag += amount
                    },

                    onDragEnd = {

                        if (horizontalDrag < -100f) {

                            visualizer =
                                (visualizer + 1) %
                                    VISUALIZER_COUNT

                        } else if (horizontalDrag > 100f) {

                            visualizer =
                                (
                                    visualizer -
                                        1 +
                                        VISUALIZER_COUNT
                                    ) %
                                    VISUALIZER_COUNT
                        }

                        horizontalDrag = 0f
                    }
                )
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            when (visualizer) {

                0 -> drawNeonSpectrum(
                    phase,
                    media.playing
                )

                1 -> drawCircularSpectrum(
                    phase,
                    media.playing
                )

                2 -> drawWaveTunnel(
                    phase,
                    media.playing
                )

                3 -> drawParticleField(
                    phase,
                    media.playing
                )

                4 -> drawPulseOrb(
                    phase,
                    media.playing
                )

                5 -> drawMirrorSpectrum(
                    phase,
                    media.playing
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(
                    start = 28.dp,
                    end = 28.dp,
                    bottom = 24.dp
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally
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
                modifier = Modifier.height(15.dp)
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
                                    Color(0xFF494251)
                                }
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(15.dp)
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
                                colors = listOf(
                                    Color(0xFFB278FF),
                                    Color(0xFF586DFF)
                                )
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
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
                text = "SWIPE LEFT / RIGHT TO CHANGE",
                color = Color(0xFF756A84),
                fontSize = 9.sp,
                letterSpacing = 1.8.sp
            )
        }
    }
}


/* =========================================================
   VISUALIZER 1 — NEON SPECTRUM
   ========================================================= */

private fun DrawScope.drawNeonSpectrum(
    phase: Float,
    playing: Boolean
) {

    val barCount = 72
    val gap = 4f

    val barWidth =
        (
            size.width -
                gap * (barCount + 1)
            ) / barCount

    val centerY =
        size.height * 0.39f

    for (i in 0 until barCount) {

        val n =
            i.toFloat() /
                (barCount - 1).coerceAtLeast(1)

        val waveA =
            (
                sin(
                    phase * 3.6f +
                        n * 18f
                ) + 1f
            ) / 2f

        val waveB =
            (
                sin(
                    phase * 1.7f +
                        n * 41f
                ) + 1f
            ) / 2f

        val envelope =
            sin(n * PI).toFloat()
                .coerceAtLeast(0f)

        val amount =
            if (playing) {

                0.055f +
                    envelope *
                    (
                        waveA * 0.55f +
                            waveB * 0.25f
                        )

            } else {

                0.025f
            }

        val barHeight =
            size.height *
                amount *
                0.70f

        val x =
            gap +
                i *
                (barWidth + gap)

        drawRoundRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF4CD8),
                        Color(0xFF9D66FF),
                        Color(0xFF45DFFF)
                    )
                ),
            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x,
                    centerY - barHeight
                ),
            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    5f,
                    5f
                )
        )

        drawRoundRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF45DFFF),
                        Color(0xFF686DFF),
                        Color(0xFFFF4CD8)
                    )
                ),
            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x,
                    centerY
                ),
            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    5f,
                    5f
                )
        )
    }

    drawLine(
        color =
            Color.White.copy(alpha = 0.08f),
        start =
            androidx.compose.ui.geometry.Offset(
                0f,
                centerY
            ),
        end =
            androidx.compose.ui.geometry.Offset(
                size.width,
                centerY
            ),
        strokeWidth = 1f
    )
}


/* =========================================================
   VISUALIZER 2 — CIRCULAR SPECTRUM
   ========================================================= */

private fun DrawScope.drawCircularSpectrum(
    phase: Float,
    playing: Boolean
) {

    val centerX =
        size.width / 2f

    val centerY =
        size.height * 0.39f

    val radius =
        size.minDimension * 0.17f

    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF9D6CFF)
                        .copy(alpha = 0.40f),
                    Color(0xFF5E65FF)
                        .copy(alpha = 0.12f),
                    Color.Transparent
                )
            ),
        radius = radius * 2.5f,
        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            )
    )

    drawCircle(
        color =
            Color(0xFFB277FF)
                .copy(alpha = 0.28f),
        radius = radius,
        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            ),
        style = Stroke(2f)
    )

    val bars = 112

    for (i in 0 until bars) {

        val angle =
            i.toFloat() /
                bars *
                2f *
                PI.toFloat()

        val waveA =
            (
                sin(
                    phase * 3.2f +
                        i * 0.31f
                ) + 1f
            ) / 2f

        val waveB =
            (
                cos(
                    phase * 1.8f +
                        i * 0.19f
                ) + 1f
            ) / 2f

        val length =
            if (playing) {
                18f +
                    waveA * 65f +
                    waveB * 25f
            } else {
                12f
            }

        val inner =
            radius + 12f

        val outer =
            inner + length

        val start =
            androidx.compose.ui.geometry.Offset(
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
            androidx.compose.ui.geometry.Offset(
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
            brush =
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF45E4FF),
                        Color(0xFF956BFF),
                        Color(0xFFFF55D8)
                    )
                ),
            start = start,
            end = end,
            strokeWidth = 3.5f,
            cap =
                androidx.compose.ui.graphics.StrokeCap.Round
        )
    }

    drawCircle(
        color = Color(0xFF09070F),
        radius = radius * 0.68f,
        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            )
    )

    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color(0xFFD28CFF),
                    Color(0xFF785DFF),
                    Color.Transparent
                )
            ),
        radius = 16f,
        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            )
    )
}
/* =========================================================
   VISUALIZER 3 — WAVE TUNNEL
   ========================================================= */

private fun DrawScope.drawWaveTunnel(
    phase: Float,
    playing: Boolean
) {

    val waveCount = 9

    for (wave in 0 until waveCount) {

        val path = Path()

        val baseY =
            size.height *
                (0.12f + wave * 0.075f)

        for (point in 0..140) {

            val x =
                size.width *
                    point /
                    140f

            val normalized =
                x / size.width

            val amplitude =
                size.height *
                    if (playing) {
                        0.035f +
                            wave * 0.003f
                    } else {
                        0.015f
                    }

            val frequency =
                5.5f +
                    wave * 0.75f

            val y =
                baseY +

                    sin(
                        normalized *
                            frequency *
                            PI *
                            2f +
                            phase *
                            (1.1f +
                                wave * 0.12f)
                    ).toFloat() *
                    amplitude +

                    sin(
                        normalized * 18f -
                            phase * 0.8f
                    ).toFloat() *
                    amplitude *
                    0.25f

            if (point == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        val alpha =
            0.28f +
                wave * 0.055f

        drawPath(
            path = path,
            brush =
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF45E5FF)
                            .copy(alpha = alpha),

                        Color(0xFF946AFF)
                            .copy(alpha = alpha),

                        Color(0xFFFF59D8)
                            .copy(alpha = alpha),

                        Color.Transparent
                    )
                ),
            style =
                Stroke(
                    if (wave < 3) {
                        4f
                    } else {
                        2.5f
                    }
                )
        )
    }
}


/* =========================================================
   VISUALIZER 4 — PARTICLE FIELD
   ========================================================= */

private fun DrawScope.drawParticleField(
    phase: Float,
    playing: Boolean
) {

    val particleCount = 190

    for (i in 0 until particleCount) {

        val seed =
            (i * 97 + 31) % 1000

        val baseX =
            (seed % 100) / 100f

        val baseY =
            ((seed / 10) % 100) / 100f

        val movementX =
            if (playing) {

                sin(
                    phase * 0.9f +
                        i * 0.21f
                ).toFloat() *
                    0.025f

            } else {
                0f
            }

        val movementY =
            if (playing) {

                cos(
                    phase * 0.7f +
                        i * 0.17f
                ).toFloat() *
                    0.025f

            } else {
                0f
            }

        val normalizedX =
            (
                (baseX + movementX) %
                    1f +
                    1f
                ) % 1f

        val normalizedY =
            (
                baseY +
                    movementY
                ).coerceIn(
                    0f,
                    1f
                )

        val x =
            normalizedX *
                size.width

        val y =
            normalizedY *
                size.height

        val pulse =
            (
                sin(
                    phase * 2f +
                        i * 0.43f
                ) + 1f
            ) / 2f

        val particleColor =
            when (i % 4) {

                0 ->
                    Color(0xFF51DFFF)

                1 ->
                    Color(0xFF9C70FF)

                2 ->
                    Color(0xFFFF61D9)

                else ->
                    Color.White
            }

        drawCircle(
            color =
                particleColor.copy(
                    alpha =
                        0.20f +
                            pulse * 0.65f
                ),
            radius =
                1.5f +
                    pulse * 3.5f,
            center =
                androidx.compose.ui.geometry.Offset(
                    x,
                    y
                )
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFB16DFF)
                        .copy(alpha = 0.42f),

                    Color(0xFF586CFF)
                        .copy(alpha = 0.10f),

                    Color.Transparent
                )
            ),
        radius = 210f,
        center =
            androidx.compose.ui.geometry.Offset(
                size.width / 2f,
                size.height * 0.39f
            )
    )

    drawCircle(
        color =
            Color(0xFFE4C5FF)
                .copy(alpha = 0.85f),
        radius = 7f,
        center =
            androidx.compose.ui.geometry.Offset(
                size.width / 2f,
                size.height * 0.39f
            )
    )
}


/* =========================================================
   VISUALIZER 5 — PULSE ORB
   ========================================================= */

private fun DrawScope.drawPulseOrb(
    phase: Float,
    playing: Boolean
) {

    val centerX =
        size.width / 2f

    val centerY =
        size.height * 0.39f

    val baseRadius =
        size.minDimension * 0.145f

    val pulse =
        if (playing) {

            1f +
                sin(
                    phase * 3.1f
                ).toFloat() *
                0.11f

        } else {
            1f
        }

    for (ring in 0 until 9) {

        val ringPulse =
            if (playing) {

                sin(
                    phase * 1.8f -
                        ring * 0.55f
                ).toFloat() *
                    0.04f

            } else {
                0f
            }

        drawCircle(
            color =
                Color(0xFF8C59FF)
                    .copy(
                        alpha =
                            0.14f -
                                ring * 0.012f
                    ),

            radius =
                baseRadius *
                    (
                        1f +
                            ring * 0.17f +
                            ringPulse
                        ),

            center =
                androidx.compose.ui.geometry.Offset(
                    centerX,
                    centerY
                ),

            style =
                Stroke(2f)
        )
    }

    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(
                    Color.White,

                    Color(0xFFE4A5FF),

                    Color(0xFF8960FF),

                    Color(0xFF4B5FFF),

                    Color.Transparent
                )
            ),

        radius =
            baseRadius *
                1.35f *
                pulse,

        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            )
    )

    val rayCount = 72

    for (i in 0 until rayCount) {

        val angle =
            i.toFloat() /
                rayCount *
                2f *
                PI.toFloat()

        val wave =
            (
                sin(
                    phase * 2.7f +
                        i * 0.44f
                ) + 1f
            ) / 2f

        val inner =
            baseRadius *
                (
                    1.32f +
                        wave * 0.05f
                    )

        val outer =
            baseRadius *
                (
                    1.45f +
                        wave * 0.75f
                    )

        drawLine(

            color =
                Color(0xFF927CFF)
                    .copy(
                        alpha =
                            0.30f +
                                wave * 0.50f
                    ),

            start =
                androidx.compose.ui.geometry.Offset(
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
                ),

            end =
                androidx.compose.ui.geometry.Offset(
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
                ),

            strokeWidth = 2f,

            cap =
                androidx.compose.ui.graphics.StrokeCap.Round
        )
    }

    drawCircle(
        color = Color.White,
        radius = 5f * pulse,
        center =
            androidx.compose.ui.geometry.Offset(
                centerX,
                centerY
            )
    )
}


/* =========================================================
   VISUALIZER 6 — MIRROR SPECTRUM
   ========================================================= */

private fun DrawScope.drawMirrorSpectrum(
    phase: Float,
    playing: Boolean
) {

    val barCount = 64
    val gap = 5f

    val barWidth =
        (
            size.width -
                gap * (barCount + 1)
            ) / barCount

    val centerY =
        size.height * 0.39f

    for (i in 0 until barCount) {

        val normalized =
            i.toFloat() /
                (barCount - 1)
                    .coerceAtLeast(1)

        val waveA =
            (
                sin(
                    phase * 4.0f +
                        normalized * 20f
                ) + 1f
            ) / 2f

        val waveB =
            (
                cos(
                    phase * 1.5f +
                        normalized * 37f
                ) + 1f
            ) / 2f

        val edge =
            sin(
                normalized * PI
            ).toFloat()
                .coerceAtLeast(0f)

        val amount =
            if (playing) {

                0.045f +
                    edge *
                    (
                        waveA * 0.65f +
                            waveB * 0.25f
                        )

            } else {

                0.022f
            }

        val barHeight =
            size.height *
                amount *
                0.62f

        val x =
            gap +
                i *
                (barWidth + gap)

        drawRoundRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF4CCC),
                        Color(0xFF9665FF),
                        Color(0xFF4AD8FF)
                    )
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x,
                    centerY - barHeight
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    5f,
                    5f
                )
        )

        drawRoundRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4AD8FF),
                        Color(0xFF9665FF),
                        Color(0xFFFF4CCC)
                    )
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x,
                    centerY
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    5f,
                    5f
                )
        )
    }

    drawLine(
        color =
            Color.White.copy(alpha = 0.10f),

        start =
            androidx.compose.ui.geometry.Offset(
                0f,
                centerY
            ),

        end =
            androidx.compose.ui.geometry.Offset(
                size.width,
                centerY
            ),

        strokeWidth = 1f
    )
}
