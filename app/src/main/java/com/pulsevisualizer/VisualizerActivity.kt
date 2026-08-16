package com.pulsevisualizer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class VisualizerActivity :
    ComponentActivity() {

    companion object {

        private const val CAPTURE_REQUEST =
            9001

        private const val AUDIO_REQUEST =
            9002
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        AppContextHolder.context =
            applicationContext

        requestAudioAndCapture()

        setContent {
            PulseTheme {
                FullScreenVisualizer()
            }
        }
    }

    private fun requestAudioAndCapture() {

        if (
            android.os.Build.VERSION.SDK_INT >=
            android.os.Build.VERSION_CODES.M
        ) {

            if (
                checkSelfPermission(
                    Manifest.permission.RECORD_AUDIO
                ) !=
                PackageManager.PERMISSION_GRANTED
            ) {

                requestPermissions(
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO
                    ),
                    AUDIO_REQUEST
                )

                return
            }
        }

        requestMediaProjection()
    }

    private fun requestMediaProjection() {

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            CAPTURE_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode == AUDIO_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] ==
            PackageManager.PERMISSION_GRANTED
        ) {

            requestMediaProjection()
        }
    }

    @Deprecated(
        "Deprecated in Android API",
        ReplaceWith("")
    )
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
            CAPTURE_REQUEST
        ) {

            if (
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
    }

    override fun onDestroy() {

        AudioCaptureManager.stop()

        super.onDestroy()
    }
}

private const val VISUALIZER_COUNT = 6

@Composable
fun FullScreenVisualizer() {

    val media by
        MediaRepository.media
            .collectAsState()

    val bands by
        AudioCaptureManager.bands
            .collectAsState()

    var phase by
        remember {
            mutableFloatStateOf(0f)
        }

    var visualizer by
        remember {
            mutableIntStateOf(0)
        }

    var dragAmount by
        remember {
            mutableFloatStateOf(0f)
        }

    LaunchedEffect(
        media.playing
    ) {

        while (true) {

            phase +=
                if (media.playing) {
                    0.035f
                } else {
                    0.008f
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
                                Color(0xFF21143D),
                                Color(0xFF0C0914),
                                Color.Black
                            )
                    )
                )
                .pointerInput(Unit) {

                    detectHorizontalDragGestures(

                        onDragStart = {
                            dragAmount = 0f
                        },

                        onHorizontalDrag = {
                                _,
                                amount ->

                            dragAmount +=
                                amount
                        },

                        onDragEnd = {

                            if (
                                dragAmount <
                                -100f
                            ) {

                                visualizer =
                                    (
                                        visualizer + 1
                                        ) %
                                        VISUALIZER_COUNT

                            } else if (
                                dragAmount >
                                100f
                            ) {

                                visualizer =
                                    (
                                        visualizer -
                                            1 +
                                            VISUALIZER_COUNT
                                        ) %
                                        VISUALIZER_COUNT
                            }

                            dragAmount = 0f
                        }
                    )
                }
    ) {

        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {

            when (visualizer) {

                0 ->
                    drawAudioBars(
                        bands,
                        phase,
                        media.playing
                    )

                1 ->
                    drawAudioCircle(
                        bands,
                        phase,
                        media.playing
                    )

                2 ->
                    drawAudioWave(
                        bands,
                        phase,
                        media.playing
                    )

                3 ->
                    drawAudioParticles(
                        bands,
                        phase,
                        media.playing
                    )

                4 ->
                    drawAudioOrb(
                        bands,
                        phase,
                        media.playing
                    )

                5 ->
                    drawAudioMirror(
                        bands,
                        phase,
                        media.playing
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
                        start = 28.dp,
                        end = 28.dp,
                        bottom = 24.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text =
                    media.title,
                color =
                    Color.White,
                fontSize =
                    27.sp,
                maxLines =
                    1
            )

            Spacer(
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text =
                    media.artist,
                color =
                    Color(0xFFB8B1C3),
                fontSize =
                    16.sp,
                maxLines =
                    1
            )

            Spacer(
                modifier =
                    Modifier.height(15.dp)
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
                                        visualizer
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
                                        visualizer
                                    ) {
                                        Color(
                                            0xFFB77CFF
                                        )
                                    } else {
                                        Color(
                                            0xFF494251
                                        )
                                    }
                                )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(15.dp)
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
                        Modifier.size(64.dp)
                ) {

                    Text(
                        "⏮",
                        color =
                            Color.White,
                        fontSize =
                            32.sp
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(82.dp)
                            .clip(
                                CircleShape
                            )
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(
                                                0xFFB278FF
                                            ),
                                            Color(
                                                0xFF586DFF
                                            )
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
                            if (
                                media.playing
                            ) {
                                "Ⅱ"
                            } else {
                                "▶"
                            },
                            color =
                                Color.White,
                            fontSize =
                                30.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        MediaRepository.next()
                    },
                    modifier =
                        Modifier.size(64.dp)
                ) {

                    Text(
                        "⏭",
                        color =
                            Color.White,
                        fontSize =
                            32.sp
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "SWIPE LEFT / RIGHT TO CHANGE",
                color =
                    Color(0xFF756A84),
                fontSize =
                    9.sp,
                letterSpacing =
                    1.8.sp
            )
        }
    }
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioBars(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val width = size.width
    val height = size.height

    val count =
        if (bands.isEmpty()) 32
        else minOf(bands.size, 48)

    val spacing = width / count.toFloat()
    val barWidth = spacing * 0.58f

    val centerY = height * 0.47f

    for (i in 0 until count) {

        val raw =
            if (bands.isNotEmpty()) {
                bands[i].coerceIn(0f, 1f)
            } else {
                0f
            }

        val smooth =
            raw * raw * 0.85f

        val idle =
            sin(
                phase * 2f +
                    i * 0.25f
            ) * 0.012f

        val value =
            if (playing) {
                smooth + idle
            } else {
                0.025f
            }

        val barHeight =
            (height * 0.48f *
                value.coerceIn(
                    0.015f,
                    1f
                ))

        val x =
            i * spacing +
                spacing / 2f

        val top =
            centerY - barHeight

        val bottom =
            centerY + barHeight

        val hue =
            i.toFloat() /
                count.toFloat()

        val color =
            Color(
                red = 0.45f + hue * 0.25f,
                green = 0.25f + hue * 0.25f,
                blue = 1f
            )

        drawRoundRect(
            color =
                color.copy(
                    alpha = 0.9f
                ),
            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x - barWidth / 2f,
                    top
                ),
            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight * 2f
                ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
        )

        drawRoundRect(
            color =
                Color.White.copy(
                    alpha =
                        if (value > 0.5f)
                            0.16f
                        else
                            0.05f
                ),
            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x - barWidth / 2f,
                    top
                ),
            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight * 0.35f
                ),
            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
        )
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioCircle(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height * 0.43f

    val baseRadius =
        size.minDimension * 0.20f

    val points = 160

    val path =
        Path()

    for (i in 0..points) {

        val t =
            i.toFloat() /
                points.toFloat()

        val bandIndex =
            if (bands.isEmpty()) {
                0
            } else {
                (
                    t *
                        (bands.size - 1)
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        bands.lastIndex
                    )
            }

        val audio =
            if (bands.isEmpty()) {
                0f
            } else {
                bands[bandIndex]
                    .coerceIn(
                        0f,
                        1f
                    )
            }

        val wave =
            sin(
                phase * 2.5f +
                    t * PI.toFloat() * 8f
            ) * 0.025f

        val amount =
            if (playing) {
                audio * 0.48f +
                    wave
            } else {
                0f
            }

        val radius =
            baseRadius *
                (
                    1f +
                        amount.coerceIn(
                            -0.05f,
                            0.75f
                        )
                    )

        val angle =
            t *
                PI.toFloat() *
                2f

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
            Color(0xFF9C6CFF),
        style =
            Stroke(
                width = 5f
            )
    )

    drawCircle(
        color =
            Color(0xFF8B5CF6)
                .copy(alpha = 0.08f),
        radius =
            baseRadius * 1.35f
    )

    drawCircle(
        color =
            Color(0xFFB77CFF)
                .copy(alpha = 0.22f),
        radius =
            baseRadius * 0.58f
    )

    drawCircle(
        color =
            Color(0xFFE8DDFF)
                .copy(alpha = 0.9f),
        radius =
            baseRadius * 0.10f
    )
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioWave(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val width = size.width
    val height = size.height

    val centerY =
        height * 0.43f

    val path =
        Path()

    val points = 180

    for (i in 0..points) {

        val t =
            i.toFloat() /
                points.toFloat()

        val bandIndex =
            if (bands.isEmpty()) {
                0
            } else {
                (
                    t *
                        (bands.size - 1)
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        bands.lastIndex
                    )
            }

        val audio =
            if (bands.isEmpty()) {
                0f
            } else {
                bands[bandIndex]
                    .coerceIn(
                        0f,
                        1f
                    )
            }

        val wave =
            sin(
                t *
                    PI.toFloat() *
                    10f +
                    phase * 3f
            )

        val amplitude =
            if (playing) {
                height *
                    (
                        0.025f +
                            audio *
                            0.42f
                        )
            } else {
                height * 0.015f
            }

        val y =
            centerY +
                wave *
                amplitude

        val x =
            t * width

        if (i == 0) {
            path.moveTo(
                x,
                y
            )
        } else {
            path.lineTo(
                x,
                y
            )
        }
    }

    drawPath(
        path = path,
        color =
            Color(0xFFB278FF),
        style =
            Stroke(
                width = 7f
            )
    )

    drawPath(
        path = path,
        color =
            Color.White.copy(
                alpha = 0.18f
            ),
        style =
            Stroke(
                width = 15f
            )
    )

    for (i in 0 until 18) {

        val x =
            width *
                i /
                17f

        drawCircle(
            color =
                Color(0xFF8B5CF6)
                    .copy(alpha = 0.16f),
            radius = 3f,
            center =
                androidx.compose.ui.geometry
                    .Offset(
                        x,
                        centerY
                    )
        )
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioParticles(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val cx =
        size.width / 2f

    val cy =
        size.height * 0.43f

    val maxRadius =
        size.minDimension * 0.39f

    val particleCount =
        150

    for (i in 0 until particleCount) {

        val normalized =
            i.toFloat() /
                particleCount.toFloat()

        val bandIndex =
            if (bands.isEmpty()) {
                0
            } else {
                (
                    normalized *
                        (bands.size - 1)
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        bands.lastIndex
                    )
            }

        val audio =
            if (bands.isEmpty()) {
                0f
            } else {
                bands[bandIndex]
                    .coerceIn(
                        0f,
                        1f
                    )
            }

        val angle =
            normalized *
                PI.toFloat() *
                2f +
                phase *
                (
                    0.25f +
                        normalized
                    )

        val radius =
            maxRadius *
                (
                    0.25f +
                        normalized *
                        0.7f +
                        if (playing) {
                            audio *
                                0.35f
                        } else {
                            0f
                        }
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
            1.5f +
                audio *
                7f

        val alpha =
            0.25f +
                audio *
                0.75f

        drawCircle(
            color =
                Color(
                    red =
                        0.55f +
                            normalized *
                            0.2f,
                    green =
                        0.25f +
                            audio *
                            0.4f,
                    blue = 1f,
                    alpha =
                        alpha.coerceIn(
                            0f,
                            1f
                        )
                ),
            radius =
                particleSize,
            center =
                androidx.compose.ui.geometry
                    .Offset(
                        x,
                        y
                    )
        )
    }

    drawCircle(
        color =
            Color(0xFF9C6CFF)
                .copy(alpha = 0.12f),
        radius =
            size.minDimension *
                0.12f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx,
                    cy
                )
    )
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioOrb(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height * 0.43f

    val minDimension = size.minDimension

    val average =
        if (bands.isEmpty()) {
            0f
        } else {
            bands.average()
                .toFloat()
                .coerceIn(0f, 1f)
        }

    val bass =
        if (bands.isEmpty()) {
            0f
        } else {
            bands
                .take(
                    maxOf(
                        1,
                        bands.size / 5
                    )
                )
                .average()
                .toFloat()
                .coerceIn(0f, 1f)
        }

    val radius =
        minDimension *
            (
                0.13f +
                    average *
                    0.13f +
                    bass *
                    0.08f
                )

    /*
     * Outer glow
     */
    drawCircle(
        color =
            Color(0xFF8B5CF6)
                .copy(
                    alpha =
                        0.035f
                ),
        radius =
            radius * 2.7f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx,
                    cy
                )
    )

    drawCircle(
        color =
            Color(0xFF9C6CFF)
                .copy(
                    alpha =
                        0.055f
                ),
        radius =
            radius * 2.1f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx,
                    cy
                )
    )

    drawCircle(
        color =
            Color(0xFFB77CFF)
                .copy(
                    alpha =
                        0.09f
                ),
        radius =
            radius * 1.6f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx,
                    cy
                )
    )

    /*
     * Main orb
     */
    drawCircle(
        brush =
            Brush.radialGradient(
                colors =
                    listOf(
                        Color(0xFFE8DDFF),
                        Color(0xFFB77CFF),
                        Color(0xFF6D4AFF),
                        Color(0xFF20104A)
                    ),
                center =
                    androidx.compose.ui.geometry
                        .Offset(
                            cx - radius * 0.28f,
                            cy - radius * 0.32f
                        ),
                radius =
                    radius * 1.35f
            ),
        radius =
            radius,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx,
                    cy
                )
    )

    /*
     * Audio-reactive rings
     */
    val ringCount = 4

    for (ring in 0 until ringCount) {

        val ringAudio =
            if (bands.isEmpty()) {
                0f
            } else {

                val start =
                    ring *
                        bands.size /
                        ringCount

                val end =
                    minOf(
                        bands.size,
                        (ring + 1) *
                            bands.size /
                            ringCount
                    )

                if (end > start) {
                    bands
                        .subList(
                            start,
                            end
                        )
                        .average()
                        .toFloat()
                } else {
                    0f
                }
            }

        val ringRadius =
            radius *
                (
                    1.35f +
                        ring *
                        0.24f +
                        ringAudio *
                        0.5f
                    )

        drawCircle(
            color =
                Color(
                    red = 0.55f +
                        ring * 0.07f,
                    green = 0.32f +
                        ring * 0.05f,
                    blue = 1f,
                    alpha =
                        (
                            0.20f +
                                ringAudio *
                                0.65f
                            )
                            .coerceIn(
                                0f,
                                0.85f
                            )
                ),
            radius =
                ringRadius,
            center =
                androidx.compose.ui.geometry
                    .Offset(
                        cx,
                        cy
                    ),
            style =
                Stroke(
                    width =
                        3f +
                            ringAudio *
                            5f
                )
        )
    }

    /*
     * Rotating highlights
     */
    val highlightCount = 10

    for (
        i in 0 until highlightCount
    ) {

        val angle =
            phase +
                i.toFloat() /
                highlightCount *
                PI.toFloat() *
                2f

        val x =
            cx +
                cos(angle) *
                radius *
                1.55f

        val y =
            cy +
                sin(angle) *
                radius *
                1.55f

        drawCircle(
            color =
                Color.White.copy(
                    alpha =
                        0.35f +
                            average *
                            0.45f
                ),
            radius =
                2f +
                    average *
                    4f,
            center =
                androidx.compose.ui.geometry
                    .Offset(
                        x,
                        y
                    )
        )
    }

    /*
     * Central highlight
     */
    drawCircle(
        color =
            Color.White.copy(
                alpha = 0.65f
            ),
        radius =
            radius * 0.11f,
        center =
            androidx.compose.ui.geometry
                .Offset(
                    cx - radius * 0.28f,
                    cy - radius * 0.32f
                )
    )
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAudioMirror(
    bands: List<Float>,
    phase: Float,
    playing: Boolean
) {
    val width = size.width
    val height = size.height

    val centerY =
        height * 0.43f

    val count =
        if (bands.isEmpty()) {
            40
        } else {
            minOf(
                bands.size,
                40
            )
        }

    val spacing =
        width /
            count.toFloat()

    for (i in 0 until count) {

        val audio =
            if (bands.isEmpty()) {
                0f
            } else {
                bands[i]
                    .coerceIn(
                        0f,
                        1f
                    )
            }

        val previous =
            if (
                i > 0 &&
                bands.isNotEmpty()
            ) {
                bands[i - 1]
                    .coerceIn(
                        0f,
                        1f
                    )
            } else {
                audio
            }

        val next =
            if (
                i <
                    bands.lastIndex &&
                bands.isNotEmpty()
            ) {
                bands[i + 1]
                    .coerceIn(
                        0f,
                        1f
                    )
            } else {
                audio
            }

        /*
         * Smooth the spectrum so it
         * doesn't look like raw bars.
         */
        val smooth =
            (
                previous +
                    audio * 2f +
                    next
                ) / 4f

        val pulse =
            if (playing) {
                sin(
                    phase * 2f +
                        i * 0.18f
                ) *
                    0.015f
            } else {
                0f
            }

        val amount =
            (
                smooth +
                    pulse
                )
                .coerceIn(
                    0.01f,
                    1f
                )

        val barHeight =
            height *
                (
                    0.035f +
                        amount *
                        0.36f
                    )

        val x =
            i *
                spacing +
                spacing / 2f

        val barWidth =
            spacing *
                0.52f

        val alpha =
            0.35f +
                smooth *
                0.65f

        val color =
            Color(
                red =
                    0.40f +
                        i.toFloat() /
                        count *
                        0.35f,
                green =
                    0.22f +
                        smooth *
                        0.35f,
                blue = 1f,
                alpha =
                    alpha.coerceIn(
                        0f,
                        1f
                    )
            )

        /*
         * Top half
         */
        drawRoundRect(
            color = color,
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(
                        x -
                            barWidth / 2f,
                        centerY -
                            barHeight
                    ),
            size =
                androidx.compose.ui.geometry
                    .Size(
                        barWidth,
                        barHeight
                    ),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(
                        barWidth / 2f,
                        barWidth / 2f
                    )
        )

        /*
         * Mirrored bottom half
         */
        drawRoundRect(
            color =
                color.copy(
                    alpha =
                        alpha * 0.72f
                ),
            topLeft =
                androidx.compose.ui.geometry
                    .Offset(
                        x -
                            barWidth / 2f,
                        centerY
                    ),
            size =
                androidx.compose.ui.geometry
                    .Size(
                        barWidth,
                        barHeight
                    ),
            cornerRadius =
                androidx.compose.ui.geometry
                    .CornerRadius(
                        barWidth / 2f,
                        barWidth / 2f
                    )
        )

        /*
         * Bright centre line
         */
        drawLine(
            color =
                Color.White.copy(
                    alpha =
                        0.10f +
                            smooth *
                            0.25f
                ),
            start =
                androidx.compose.ui.geometry
                    .Offset(
                        x -
                            barWidth / 2f,
                        centerY
                    ),
            end =
                androidx.compose.ui.geometry
                    .Offset(
                        x +
                            barWidth / 2f,
                        centerY
                    ),
            strokeWidth =
                2f
        )
    }

    /*
     * Centre glow
     */
    drawLine(
        color =
            Color(0xFFB77CFF)
                .copy(
                    alpha =
                        0.25f +
                            if (playing)
                                0.15f
                            else
                                0f
                ),
        start =
            androidx.compose.ui.geometry
                .Offset(
                    0f,
                    centerY
                ),
        end =
            androidx.compose.ui.geometry
                .Offset(
                    width,
                    centerY
                ),
        strokeWidth = 2f
    )
}


/*
 * Simple fallback audio smoothing.
 *
 * This prevents the visualizer from becoming
 * completely static if Android temporarily
 * gives us a zero-energy frame.
 */
private fun smoothAudio(
    current: Float,
    previous: Float,
    attack: Float = 0.55f,
    release: Float = 0.12f
): Float {

    return if (
        current > previous
    ) {

        previous +
            (
                current -
                    previous
                ) *
                attack

    } else {

        previous +
            (
                current -
                    previous
                ) *
                release
    }
}


/*
 * Small utility used by the visualizers.
 */
private fun clampAudio(
    value: Float
): Float {

    return value.coerceIn(
        0f,
        1f
    )
}
