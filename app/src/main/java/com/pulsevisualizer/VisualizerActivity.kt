package com.pulsevisualizer

import android.Manifest
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
        private const val AUDIO_PERMISSION = 9001
        private const val MEDIA_PROJECTION = 9002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppContextHolder.context = applicationContext

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        setContent {
            PulseTheme {
                FullScreenVisualizer()
            }
        }

        requestAudioPermission()
    }

    private fun requestAudioPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),
                AUDIO_PERMISSION
            )
        } else {
            requestProjection()
        }
    }

    private fun requestProjection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            MEDIA_PROJECTION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == AUDIO_PERMISSION) {
            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {
                requestProjection()
            }
        }
    }

    @Deprecated("Deprecated Android callback")
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
            requestCode == MEDIA_PROJECTION &&
            resultCode == Activity.RESULT_OK &&
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
fun FullScreenVisualizer() {

    val media by
        MediaRepository.media.collectAsState()

    val bands by
        AudioCaptureManager.bands.collectAsState()

    var visualizer by
        remember {
            mutableIntStateOf(0)
        }

    var phase by
        remember {
            mutableFloatStateOf(0f)
        }

    var drag by
        remember {
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
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF241044),
                            Color(0xFF0B0712),
                            Color.Black
                        )
                    )
                )
                .pointerInput(Unit) {

                    detectHorizontalDragGestures(

                        onDragStart = {
                            drag = 0f
                        },

                        onHorizontalDrag = {
                                _,
                                amount ->
                            drag += amount
                        },

                        onDragEnd = {

                            if (drag < -100f) {

                                visualizer =
                                    (visualizer + 1) %
                                    VISUALIZER_COUNT

                            } else if (drag > 100f) {

                                visualizer =
                                    (
                                        visualizer -
                                        1 +
                                        VISUALIZER_COUNT
                                    ) %
                                    VISUALIZER_COUNT
                            }

                            drag = 0f
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
                    drawSpectrum(
                        bands,
                        phase
                    )

                1 ->
                    drawCircleSpectrum(
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
                modifier =
                    Modifier.height(5.dp)
            )

            Text(
                text = media.artist,
                color = Color(0xFFB7AFC2),
                fontSize = 16.sp,
                maxLines = 1
            )

            Spacer(
                modifier =
                    Modifier.height(15.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {

                repeat(VISUALIZER_COUNT) { index ->

                    Box(
                        modifier =
                            Modifier
                                .size(
                                    if (
                                        index == visualizer
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
                                        index == visualizer
                                    ) {
                                        Color(0xFFB77CFF)
                                    } else {
                                        Color(0xFF51485C)
                                    }
                                )
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(14.dp)
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
                                    colors = listOf(
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
                    Modifier.height(12.dp)
            )

            Text(
                text = "SWIPE LEFT / RIGHT",
                color = Color(0xFF756A84),
                fontSize = 9.sp,
                letterSpacing = 1.8.sp
            )
        }
    }
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrum(
    bands: FloatArray,
    phase: Float
) {

    val count = minOf(bands.size, 48)

    if (count <= 0) return

    val spacing =
        size.width / count.toFloat()

    val centerY =
        size.height * 0.43f

    for (i in 0 until count) {

        val value =
            bands[i].coerceIn(0f, 1f)

        val height =
            size.height *
                (
                    0.025f +
                    value * 0.42f
                )

        val x =
            spacing * i +
            spacing / 2f

        val width =
            spacing * 0.62f

        val color =
            Color(
                red =
                    0.42f +
                    i.toFloat() /
                    count *
                    0.35f,

                green =
                    0.25f +
                    value * 0.25f,

                blue = 1f
            )

        drawRoundRect(
            color =
                color.copy(
                    alpha =
                        0.55f +
                        value * 0.45f
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x - width / 2f,
                    centerY - height
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    width,
                    height * 2f
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    width / 2f,
                    width / 2f
                )
        )
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCircleSpectrum(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.43f

    val radius =
        size.minDimension * 0.22f

    val points = 160

    val path = Path()

    for (i in 0..points) {

        val t =
            i.toFloat() / points

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

        val audio =
            bands[index]

        val r =
            radius *
            (
                1f +
                audio * 0.75f
            )

        val angle =
            t *
            PI.toFloat() *
            2f

        val x =
            cx +
            cos(angle) * r

        val y =
            cy +
            sin(angle) * r

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    path.close()

    drawPath(
        path = path,
        color = Color(0xFFB278FF),
        style = Stroke(5f)
    )

    drawCircle(
        color =
            Color(0xFF9C6CFF)
                .copy(alpha = 0.10f),

        radius =
            radius * 0.7f,

        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWave(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val path = Path()

    val centerY =
        size.height * 0.43f

    val points = 180

    for (i in 0..points) {

        val t =
            i.toFloat() / points

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

        val audio =
            bands[index]

        val wave =
            sin(
                t *
                PI.toFloat() *
                12f +
                phase * 3f
            )

        val amplitude =
            size.height *
            (
                0.025f +
                audio * 0.38f
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
        color = Color(0xFFB278FF),
        style = Stroke(6f)
    )

    drawPath(
        path = path,
        color =
            Color.White.copy(
                alpha = 0.14f
            ),
        style = Stroke(15f)
    )
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.43f

    val maxRadius =
        size.minDimension * 0.40f

    for (i in 0 until 150) {

        val normalized =
            i / 150f

        val index =
            (
                normalized *
                (bands.size - 1)
            )
                .toInt()
                .coerceIn(
                    0,
                    bands.lastIndex
                )

        val audio =
            bands[index]

        val angle =
            normalized *
            PI.toFloat() *
            2f +
            phase *
            (
                0.2f +
                normalized
            )

        val radius =
            maxRadius *
            (
                0.18f +
                normalized * 0.72f +
                audio * 0.35f
            )

        val x =
            cx +
            cos(angle) * radius

        val y =
            cy +
            sin(angle) * radius

        drawCircle(
            color =
                Color(
                    red =
                        0.55f +
                        normalized * 0.2f,

                    green =
                        0.25f +
                        audio * 0.4f,

                    blue = 1f,

                    alpha =
                        0.25f +
                        audio * 0.75f
                ),

            radius =
                1.5f +
                audio * 7f,

            center =
                androidx.compose.ui.geometry.Offset(
                    x,
                    y
                )
        )
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOrb(
    bands: FloatArray,
    phase: Float
) {

    if (bands.isEmpty()) return

    val cx =
        size.width / 2f

    val cy =
        size.height * 0.43f

    val average =
        bands
            .average()
            .toFloat()
            .coerceIn(
                0f,
                1f
            )

    val bass =
        bands
            .take(
                minOf(
                    12,
                    bands.size
                )
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
            0.13f +
            average * 0.13f +
            bass * 0.08f
        )

    drawCircle(
        color =
            Color(0xFF8B5CF6)
                .copy(
                    alpha = 0.07f
                ),

        radius =
            radius * 2.5f,

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
                        Color(0xFFB77CFF),
                        Color(0xFF6840FF),
                        Color(0xFF180B3A)
                    )
            ),

        radius = radius,

        center =
            androidx.compose.ui.geometry.Offset(
                cx,
                cy
            )
    )

    for (ring in 0 until 4) {

        val start =
            ring *
            bands.size /
            4

        val end =
            minOf(
                bands.size,
                (
                    ring + 1
                ) *
                bands.size /
                4
            )

        val energy =
            if (end > start) {

                bands
                    .copyOfRange(
                        start,
                        end
                    )
                    .average()
                    .toFloat()

            } else {
                0f
            }

        drawCircle(
            color =
                Color(0xFFB77CFF)
                    .copy(
                        alpha =
                            0.20f +
                            energy * 0.65f
                    ),

            radius =
                radius *
                (
                    1.35f +
                    ring * 0.24f +
                    energy * 0.5f
                ),

            center =
                androidx.compose.ui.geometry.Offset(
                    cx,
                    cy
                ),

            style =
                Stroke(
                    3f +
                    energy * 5f
                )
        )
    }
}
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMirror(
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
        size.height * 0.43f

    val spacing =
        size.width / count.toFloat()

    for (i in 0 until count) {

        val value =
            bands[i]
                .coerceIn(
                    0f,
                    1f
                )

        val barHeight =
            size.height *
            (
                0.02f +
                value * 0.34f
            )

        val x =
            i *
            spacing +
            spacing / 2f

        val barWidth =
            spacing * 0.55f

        val top =
            centerY -
            barHeight

        val bottom =
            centerY +
            barHeight

        val color =
            Color(
                red =
                    0.35f +
                    value * 0.35f,

                green =
                    0.20f +
                    i.toFloat() /
                    count *
                    0.25f,

                blue = 1f,

                alpha =
                    0.75f +
                    value * 0.25f
            )

        drawRoundRect(
            color = color,

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x -
                        barWidth / 2f,
                    top
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
        )

        drawRoundRect(
            color =
                color.copy(
                    alpha = 0.45f
                ),

            topLeft =
                androidx.compose.ui.geometry.Offset(
                    x -
                        barWidth / 2f,
                    centerY
                ),

            size =
                androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                ),

            cornerRadius =
                androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2f,
                    barWidth / 2f
                )
        )
    }

    drawLine(
        color =
            Color.White.copy(
                alpha = 0.12f
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

        strokeWidth = 2f
    )
}


/*
 * Small application theme.
 *
 * Keeping this here means the visualizer does not depend
 * on a separate theme file being present.
 */
@Composable
private fun PulseTheme(
    content: @Composable () -> Unit
) {

    androidx.compose.material3.MaterialTheme(
        colorScheme =
            androidx.compose.material3.darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                primary = Color(0xFFB77CFF)
            )
    ) {
        content()
    }
}


/*
 * Application context holder.
 *
 * AudioCaptureManager can use this when it needs
 * application-level Android context.
 */
object AppContextHolder {

    lateinit var context:
        android.content.Context
}


/*
 * MediaRepository
 *
 * This is deliberately kept lightweight.
 *
 * Your existing MediaInfo object remains the source
 * of the actual song information.
 */
object MediaRepository {

    private val _media =
        kotlinx.coroutines.flow.MutableStateFlow(
            MediaInfo()
        )

    val media =
        _media

    fun update(
        info: MediaInfo
    ) {
        _media.value = info
    }

    fun next() {

        sendMediaButton(
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT
        )
    }

    fun previous() {

        sendMediaButton(
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
        )
    }

    fun togglePlayPause() {

        sendMediaButton(
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        )
    }

    private fun sendMediaButton(
        keyCode: Int
    ) {

        val context =
            AppContextHolder.context

        val audioManager =
            context.getSystemService(
                android.content.Context.AUDIO_SERVICE
            ) as android.media.AudioManager

        val down =
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                keyCode
            )

        val up =
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                keyCode
            )

        audioManager.dispatchMediaKeyEvent(
            down
        )

        audioManager.dispatchMediaKeyEvent(
            up
        )
    }
}
