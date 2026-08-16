package com.pulsevisualizer

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
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

@Composable
fun FullScreenVisualizer() {

    val media by MediaRepository.media.collectAsState()

    var phase by remember {
        mutableFloatStateOf(0f)
    }

    var visualizer by remember {
        mutableIntStateOf(0)
    }

    var drag by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(media.playing) {
        while (true) {
            phase += if (media.playing) 0.045f else 0.01f
            delay(16)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF18102B),
                        Color(0xFF08070D),
                        Color.Black
                    )
                )
            )
            .pointerInput(Unit) {

                detectHorizontalDragGestures(

                    onDragStart = {
                        drag = 0f
                    },

                    onHorizontalDrag = { _, amount ->
                        drag += amount
                    },

                    onDragEnd = {

                        if (drag < -100f) {
                            visualizer =
                                (visualizer + 1) % 6
                        }

                        if (drag > 100f) {
                            visualizer =
                                (visualizer + 5) % 6
                        }

                        drag = 0f
                    }
                )
            }
    ) {

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {

            when (visualizer) {

                0 -> visualizerBars(
                    phase,
                    media.playing
                )

                1 -> visualizerCircle(
                    phase,
                    media.playing
                )

                2 -> visualizerWaves(
                    phase,
                    media.playing
                )

                3 -> visualizerParticles(
                    phase,
                    media.playing
                )

                4 -> visualizerOrb(
                    phase,
                    media.playing
                )

                5 -> visualizerMirror(
                    phase,
                    media.playing
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(28.dp),
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

                repeat(6) { index ->

                    Box(
                        modifier = Modifier
                            .size(
                                if (index == visualizer)
                                    8.dp
                                else
                                    5.dp
                            )
                            .clip(CircleShape)
                            .background(
                                if (index == visualizer)
                                    Color(0xFFB77CFF)
                                else
                                    Color(0xFF45404F)
                            )
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(16.dp)
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
                        "⏮",
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
                            if (media.playing)
                                "Ⅱ"
                            else
                                "▶",
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
                        "⏭",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Text(
                "SWIPE LEFT / RIGHT",
                color = Color(0xFF756A84),
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
        }
    }
}


/* ================================================= */
/* VISUALIZER 1 */
/* ================================================= */

private fun DrawScope.visualizerBars(
    phase: Float,
    playing: Boolean
) {

    val bars = 64
    val gap = 5f

    val width =
        (size.width - gap * (bars + 1)) / bars

    val center =
        size.height * 0.42f

    for (i in 0 until bars) {

        val n =
            i.toFloat() / bars

        val wave =
            (
                sin(
                    phase * 3.5f +
                        n * 18f
                ) + 1f
            ) / 2f

        val envelope =
            sin(n * PI).toFloat()

        val amount =
            if (playing) {
                0.08f +
                    envelope *
                    wave *
                    0.75f
            } else {
                0.03f
            }

        val h =
            size.height *
                amount *
                0.50f

        val x =
            gap +
                i *
                (width + gap)

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFFFF45D1),
                    Color(0xFF9765FF),
                    Color(0xFF43D8FF)
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                x,
                center - h
            ),
            size = androidx.compose.ui.geometry.Size(
                width,
                h
            ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    6f,
                    6f
                )
        )

        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF43D8FF),
                    Color(0xFF596CFF),
                    Color(0xFFFF45D1)
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(
                x,
                center
            ),
            size = androidx.compose.ui.geometry.Size(
                width,
                h
            ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    6f,
                    6f
                )
        )
    }
}


/* ================================================= */
/* VISUALIZER 2 */
/* ================================================= */

private fun DrawScope.visualizerCircle(
    phase: Float,
    playing: Boolean
) {

    val cx = size.width / 2f
    val cy = size.height * 0.40f

    val radius =
        size.minDimension * 0.18f

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFF9B6CFF).copy(alpha = 0.5f),
                Color.Transparent
            )
        ),
        radius = radius * 2.5f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
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
                sin(
                    phase * 3f +
                        i * 0.30f
                ) + 1f
            ) / 2f

        val length =
            if (playing)
                25f + wave * 85f
            else
                15f

        val inner =
            radius + 8f

        val outer =
            inner + length

        drawLine(
            brush = Brush.linearGradient(
                listOf(
                    Color(0xFF43DFFF),
                    Color(0xFF956CFF),
                    Color(0xFFFF55D8)
                )
            ),
            start =
                androidx.compose.ui.geometry.Offset(
                    (
                        cx +
                            cos(angle) *
                            inner
                        ).toFloat(),
                    (
                        cy +
                            sin(angle) *
                            inner
                        ).toFloat()
                ),
            end =
                androidx.compose.ui.geometry.Offset(
                    (
                        cx +
                            cos(angle) *
                            outer
                        ).toFloat(),
                    (
                        cy +
                            sin(angle) *
                            outer
                        ).toFloat()
                ),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }

    drawCircle(
        color = Color.Black,
        radius = radius * 0.70f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
    )

    drawCircle(
        color = Color(0xFFB779FF),
        radius = 8f,
        center = androidx.compose.ui.geometry.Offset(
            cx,
            cy
        )
    )
}


/* ================================================= */
/* VISUALIZER 3 */
/* ================================================= */

private fun DrawScope.visualizerWaves(
    phase: Float,
    playing: Boolean
) {

    repeat(7) { wave ->

        val path = Path()

        val base =
            size.height *
                (
                    0.18f +
                        wave * 0.08f
                )

        for (i in 0..120) {

            val x =
                size.width *
                    i / 120f

            val n =
                x / size.width

            val amplitude =
                size.height *
                    if (playing)
                        0.05f
                    else
                        0.02f

            val y =
                base +
                    sin(
                        n *
                            (7f + wave) *
                            PI *
                            2f +
                            phase *
                            (1.2f + wave * 0.12f)
                    ).toFloat() *
                    amplitude

            if (i == 0)
                path.moveTo(x, y)
            else
                path.lineTo(x, y)
        }

        drawPath(
            path = path,
            brush =
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF43E5FF),
                        Color(0xFF9A68FF),
                        Color(0xFFFF59D8)
                    )
                ),
            style = Stroke(
                width = 4f,
                cap = StrokeCap.Round
            )
        )
    }
}


/* ================================================= */
/* VISUALIZER 4 */
/* ================================================= */

private fun DrawScope.visualizerParticles(
    phase: Float,
    playing: Boolean
) {

    repeat(180) { i ->

        val seed =
            (i * 97 + 13) % 1000

        val xBase =
            (seed % 100) / 100f

        val yBase =
            ((seed / 10) % 100) / 100f

        val movement =
            if (playing) {
                sin(
                    phase +
                        i * 0.2f
                ).toFloat() * 0.03f
            } else {
                0f
            }

        val x =
            (
                (xBase + movement)
                    .let {
                        ((it % 1f) + 1f) % 1f
                    }
            ) * size.width

        val y =
            (
                yBase +
                    sin(
                        phase * 0.5f +
                            i * 0.3f
                    ).toFloat() *
                    0.03f
            ).coerceIn(0f, 1f) *
                size.height

        val pulse =
            (
                sin(
                    phase * 2f +
                        i * 0.4f
                ) + 1f
            ) / 2f

        drawCircle(
            color =
                when (i % 3) {
                    0 -> Color(0xFF54DDFF)
                    1 -> Color(0xFF9D6CFF)
                    else -> Color(0xFFFF61D8)
                }.copy(
                    alpha =
                        0.3f +
                            pulse * 0.6f
                ),
            radius =
                2f +
                    pulse * 3f,
            center =
                androidx.compose.ui.geometry.Offset(
                    x,
                    y
                )
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color(0xFFB36CFF).copy(alpha = 0.5f),
                Color.Transparent
            )
        ),
        radius = 180f,
        center =
            androidx.compose.ui.geometry.Offset(
                size.width / 2f,
                size.height * 0.40f
            )
    )
}


/* ================================================= */
/* VISUALIZER 5 */
/* ================================================= */

private fun DrawScope.visualizerOrb(
    phase: Float,
    playing: Boolean
) {

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.40f

    val base =
        size.minDimension * 0.15f

    val pulse =
        if (playing) {
            1f +
                sin(
                    phase * 3f
                ).toFloat() * 0.12f
        } else {
            1f
        }

    repeat(8) { i ->

        drawCircle(
            color =
                Color(
                    0.5f,
                    0.3f,
                    1f,
                    0.12f
                ),
            radius =
                base *
                    (1f + i * 0.16f) *
                    pulse,
            center =
                androidx.compose.ui.geometry.Offset(
                    cx,
                    cy
                ),
            style = Stroke(2f)
        )
    }

    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                Color.White,
                Color(0xFFE09CFF),
                Color(0xFF805AFF),
                Color(0xFF4D5BFF)
            )
        ),
        radius = base * pulse,
        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )

    repeat(64) { i ->

        val angle =
            i.toFloat() /
                64f *
                2f *
                PI.toFloat()

        val wave =
            (
                sin(
                    phase * 2.5f +
                        i * 0.5f
                ) + 1f
            ) / 2f

        drawLine(
            color =
                Color(0xFF8E7CFF)
                    .copy(alpha = 0.6f),
            start =
                androidx.compose.ui.geometry.Offset(
                    (
                        cx +
                            cos(angle) *
                            base *
                            1.15f
                        ).toFloat(),
                    (
                        cy +
                            sin(angle) *
                            base *
                            1.15f
                        ).toFloat()
                ),
            end =
                androidx.compose.ui.geometry.Offset(
                    (
                        cx +
                            cos(angle) *
                            base *
                            (1.3f + wave * 0.8f)
                        ).toFloat(),
                    (
                        cy +
                            sin(angle) *
                            base *
                            (1.3f + wave * 0.8f)
                        ).toFloat()
                ),
            strokeWidth = 2f,
            cap = StrokeCap.Round
        )
    }
}


/* ================================================= */
/* VISUALIZER 6 */
/* ================================================= */

private fun DrawScope.visualizerMirror(
    phase: Float,
    playing: Boolean
) {

    val bars = 64
    val gap = 4f

    val width =
        (
   
