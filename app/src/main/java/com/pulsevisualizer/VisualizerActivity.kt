package com.pulsevisualizer

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
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

    companion object {
        private const val MEDIA_PROJECTION_REQUEST = 7001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppContextHolder.context = applicationContext

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        MediaRepository.start(applicationContext)

        setContent {
            VisualizerScreen()
        }

        requestAudioCapture()
    }

    private fun requestAudioCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQUEST
        )
    }

    @Deprecated("Android activity result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode ==
            MEDIA_PROJECTION_REQUEST &&
            resultCode ==
            Activity.RESULT_OK &&
            data != null
        ) {
            AudioCaptureManager.start(
                resultCode,
                data
            )
        }
    }

    override fun onDestroy() {
        AudioCaptureManager.stop()
        super.onDestroy()
    }
}

private const val VISUALIZER_COUNT = 6

@Composable
private fun VisualizerScreen() {

    val media by
        MediaRepository.media.collectAsState()

    val bands by
        AudioCaptureManager.bands.collectAsState()

    var selectedVisualizer by
        remember {
            mutableIntStateOf(0)
        }

    var phase by
        remember {
            mutableFloatStateOf(0f)
        }

    var swipeDistance by
        remember {
            mutableFloatStateOf(0f)
        }

    LaunchedEffect(Unit) {

        while (true) {

            phase +=
                if (media.playing) {
                    0.045f
                } else {
                    0.012f
                }

            if (phase > 1000f) {
                phase = 0f
            }

            delay(16L)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(0xFF241047),
                                Color(0xFF0D0817),
                                Color.Black
                            )
                    )
                )
                .pointerInput(Unit) {

                    detectHorizontalDragGestures(

                        onDragStart = {
                            swipeDistance = 0f
                        },

                        onHorizontalDrag = {
                                _,
                                amount ->
                            swipeDistance += amount
                        },

                        onDragEnd = {

                            if (swipeDistance < -100f) {

                                selectedVisualizer =
                                    (
                                        selectedVisualizer + 1
                                    ) %
                                    VISUALIZER_COUNT

                            } else if (
                                swipeDistance > 100f
                            ) {

                                selectedVisualizer =
                                    (
                                        selectedVisualizer -
                                            1 +
                                            VISUALIZER_COUNT
                                    ) %
                                    VISUALIZER_COUNT
                            }

                            swipeDistance = 0f
                        }
                    )
                }
    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            when (selectedVisualizer) {

                0 ->
                    drawBars(
                        bands,
                        phase
                    )

                1 ->
                    drawRadial(
                        bands,
                        phase
                    )

                2 ->
                    drawWave(
                        bands,
                        phase
                    )

                3 ->
                    drawParticles(
                        bands,
                        phase
                    )

                4 ->
                    drawOrb(
                        bands,
                        phase
                    )

                5 ->
                    drawMirror(
                        bands,
                        phase
                    )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.BottomCenter
                    )
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 24.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    media.title.ifBlank {
                        "Nothing playing"
                    },
                color = Color.White,
                fontSize = 25.sp,
                maxLines = 1
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text = media.artist,
                color =
                    Color(0xFFAAA0B8),
                fontSize = 15.sp,
                maxLines = 1
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {

                repeat(
                    VISUALIZER_COUNT
                ) { index ->

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    if (
                                        index ==
                                        selectedVisualizer
                                    ) {
                                        8.dp
                                    } else {
                                        5.dp
                                    }
                                )
                                .clip(
                                    CircleShape
                                )
                                .background(
                                    if (
                                        index ==
                                        selectedVisualizer
                                    ) {
                                        Color(0xFFB77CFF)
                                    } else {
                                        Color(0xFF50485A)
                                    }
                                )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
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
                    onClick = {
                        MediaRepository.previous()
                    },
                    modifier =
                        Modifier.size(58.dp)
                ) {

                    Text(
                        text = "⏮",
                        color = Color.White,
                        fontSize = 28.sp
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0xFFAA7CFF),
                                            Color(0xFF596CFF)
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
                            fontSize = 29.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        MediaRepository.next()
                    },
                    modifier =
                        Modifier.size(58.dp)
                ) {

                    Text(
                        text = "⏭",
                        color = Color.White,
                        fontSize = 28.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text = "SWIPE LEFT / RIGHT TO CHANGE",
                color =
                    Color(0xFF6F647A),
                fontSize = 8.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

private fun DrawScope.drawBars(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val count =
        minOf(
            bands.size,
            56
        )

    val spacing =
        size.width /
            count.toFloat()

    val centerY =
        size.height * 0.42f

    for (i in 0 until count) {

        val value =
            bands[i]
                .coerceIn(
                    0f,
                    1f
                )

        val x =
            spacing * i +
                spacing / 2f

        val barWidth =
            spacing * 0.58f

        val barHeight =
            size.height *
                (
                    0.015f +
                    value * 0.37f
                )

        val pulse =
            1f +
                sin(
                    phase * 2f +
                        i * 0.18f
                ) * 0.05f

        drawRoundRect(
            color =
                Color(
                    red =
                        0.38f +
                            i.toFloat() /
                            count *
                            0.42f,

                    green =
                        0.22f +
                            value * 0.28f,

                    blue = 1f,

                    alpha =
                        0.55f +
                            value * 0.45f
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x -
                        barWidth / 2f,
                    centerY -
                        barHeight *
                        pulse
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight *
                        pulse *
                        2f
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
        )
    }
}

private fun DrawScope.drawRadial(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.40f

    val baseRadius =
        size.minDimension *
            0.19f

    val points = 180

    val path =
        Path()

    for (i in 0..points) {

        val t =
            i.toFloat() /
                points.toFloat()

        val index =
            (
                t *
                    (bands.size - 1)
            )
                .toInt()
                .coerceIn(
                    0,
                    bands.lastIndex
                )

        val value =
            bands[index]

        val angle =
            t *
                PI.toFloat() *
                2f

        val radius =
            baseRadius *
                (
                    1f +
                        value * 0.75f
                )

        val x =
            cx +
                cos(angle) *
                radius

        val y =
            cy +
                sin(angle) *
                radius

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    path.close()

    drawPath(
        path = path,
        color =
            Color(0xFFB77CFF),
        style =
            Stroke(
                width = 5f
            )
    )

    drawCircle(
        color =
            Color(0xFF9D6CFF)
                .copy(
                    alpha = 0.09f
                ),
        radius =
            baseRadius *
                0.75f,
        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )
}
private fun DrawScope.drawWave(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val centerY =
        size.height * 0.40f

    val path =
        Path()

    val points = 220

    for (i in 0..points) {

        val t =
            i.toFloat() /
                points.toFloat()

        val index =
            (
                t *
                    (bands.size - 1)
            )
                .toInt()
                .coerceIn(
                    0,
                    bands.lastIndex
                )

        val value =
            bands[index]

        val bass =
            bands[
                minOf(
                    index,
                    minOf(
                        7,
                        bands.lastIndex
                    )
                )
            ]

        val frequency =
            8f +
                value * 5f

        val wave =
            sin(
                t *
                    PI.toFloat() *
                    frequency +
                    phase * 2.5f
            )

        val amplitude =
            size.height *
                (
                    0.015f +
                        value * 0.30f +
                        bass * 0.12f
                )

        val x =
            t * size.width

        val y =
            centerY +
                wave * amplitude

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color =
            Color.White.copy(
                alpha = 0.10f
            ),
        style =
            Stroke(
                width = 18f
            )
    )

    drawPath(
        path = path,
        color =
            Color(0xFFB77CFF),
        style =
            Stroke(
                width = 5f
            )
    )
}

private fun DrawScope.drawParticles(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.40f

    val maxRadius =
        size.minDimension * 0.40f

    val count = 180

    for (i in 0 until count) {

        val t =
            i.toFloat() /
                count.toFloat()

        val index =
            (
                t *
                    (bands.size - 1)
            )
                .toInt()
                .coerceIn(
                    0,
                    bands.lastIndex
                )

        val value =
            bands[index]

        val angle =
            t *
                PI.toFloat() *
                2f +
                phase *
                (
                    0.15f +
                        t
                )

        val radius =
            maxRadius *
                (
                    0.15f +
                        t * 0.80f +
                        value * 0.32f
                )

        val x =
            cx +
                cos(angle) *
                radius

        val y =
            cy +
                sin(angle) *
                radius

        val particleSize =
            1.2f +
                value * 6f

        drawCircle(
            color =
                Color(
                    red =
                        0.45f +
                            t * 0.40f,

                    green =
                        0.18f +
                            value * 0.45f,

                    blue = 1f,

                    alpha =
                        0.25f +
                            value * 0.75f
                ),
            radius =
                particleSize,
            center =
                androidx.compose.ui.geometry.Offset(
                    x,
                    y
                )
        )
    }
}

private fun DrawScope.drawOrb(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.40f

    val average =
        bands
            .average()
            .toFloat()
            .coerceIn(
                0f,
                1f
            )

    val bassCount =
        minOf(
            10,
            bands.size
        )

    val bass =
        bands
            .copyOfRange(
                0,
                bassCount
            )
            .average()
            .toFloat()
            .coerceIn(
                0f,
                1f
            )

    val radius =
        size.minDimension *
            (
                0.11f +
                    average * 0.08f +
                    bass * 0.12f
            )

    drawCircle(
        color =
            Color(0xFF7C4DFF)
                .copy(
                    alpha =
                        0.08f +
                            bass * 0.15f
                ),
        radius =
            radius * 3.1f,
        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )

    drawCircle(
        color =
            Color(0xFF9D6CFF)
                .copy(
                    alpha = 0.15f
                ),
        radius =
            radius * 1.8f,
        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )

    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color.White,
                        Color(0xFFD0A8FF),
                        Color(0xFF713BFF),
                        Color(0xFF16082F)
                    )
            ),
        radius = radius,
        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )

    for (ring in 0 until 5) {

        val start =
            ring *
                bands.size /
                5

        val end =
            minOf(
                bands.size,
                (
                    ring + 1
                ) *
                    bands.size /
                    5
            )

        if (end <= start) continue

        val energy =
            bands
                .copyOfRange(
                    start,
                    end
                )
                .average()
                .toFloat()
                .coerceIn(
                    0f,
                    1f
                )

        drawCircle(
            color =
                Color(0xFFB77CFF)
                    .copy(
                        alpha =
                            0.16f +
                                energy * 0.60f
                    ),

            radius =
                radius *
                    (
                        1.25f +
                            ring * 0.22f +
                            energy * 0.45f
                    ),

            center =
                androidx.compose.ui.geometry.Offset(
                    cx,
                    cy
                ),

            style =
                Stroke(
                    width =
                        2f +
                            energy * 5f
                )
        )
    }
}

private fun DrawScope.drawMirror(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val count =
        minOf(
            bands.size,
            64
        )

    val centerY =
        size.height * 0.40f

    val spacing =
        size.width /
            count.toFloat()

    for (i in 0 until count) {

        val value =
            bands[i]
                .coerceIn(
                    0f,
                    1f
                )

        val height =
            size.height *
                (
                    0.015f +
                        value * 0.30f
                )

        val x =
            spacing * i +
                spacing / 2f

        val width =
            spacing * 0.58f

        val color =
            Color(
                red =
                    0.35f +
                        value * 0.35f,

                green =
                    0.18f +
                        i.toFloat() /
                        count *
                        0.35f,

                blue = 1f,

                alpha =
                    0.70f +
                        value * 0.30f
            )

        drawRoundRect(
            color = color,

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x -
                        width / 2f,
                    centerY -
                        height
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    width,
                    height
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    width / 2f,
                    width / 2f
                )
        )

        drawRoundRect(
            color =
                color.copy(
                    alpha = 0.42f
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x -
                        width / 2f,
                    centerY
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    width,
                    height
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    width / 2f,
                    width / 2f
                )
        )
    }

    drawLine(
        color =
            Color.White.copy(
                alpha = 0.10f
            ),

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

        strokeWidth = 1.5f
    )
}
