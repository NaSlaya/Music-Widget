package com.pulsevisualizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val audioPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                requestAudioCapture()
            }
        }

    private val captureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode ==
                    RESULT_OK &&
                result.data != null
            ) {

                AudioCaptureManager.start(
                    result.resultCode,
                    result.data!!
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        AudioCaptureManager.initialize(this)

        setContent {

            PulseTheme {

                PulseApp(
                    openNotificationAccess = {

                        try {

                            startActivity(
                                Intent(
                                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
                                )
                            )

                        } catch (_: Exception) {
                        }
                    },

                    openFullScreen = {

                        startActivity(
                            Intent(
                                this,
                                VisualizerActivity::class.java
                            )
                        )
                    },

                    enableVisualizer = {
                        requestAudioCapture()
                    }
                )
            }
        }

        requestAudioCapture()
    }

    private fun requestAudioCapture() {

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            audioPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )

            return
        }

        val manager =
            getSystemService(
                MediaProjectionManager::class.java
            )

        captureLauncher.launch(
            manager.createScreenCaptureIntent()
        )
    }

    override fun onDestroy() {

        AudioCaptureManager.stop()

        super.onDestroy()
    }
}

@Composable
fun PulseTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(
        colorScheme =
            androidx.compose.material3.darkColorScheme(
                background =
                    Color(0xFF060609),

                surface =
                    Color(0xFF111116),

                primary =
                    Color(0xFF9B7BFF),

                secondary =
                    Color(0xFFB9A5FF)
            ),

        content = content
    )
}

@Composable
fun PulseApp(
    openNotificationAccess: () -> Unit,
    openFullScreen: () -> Unit,
    enableVisualizer: () -> Unit
) {

    val media by
        MediaRepository.media.collectAsState()

    val bands by
        AudioCaptureManager.bands.collectAsState()

    val capturing by
        AudioCaptureManager.isCapturing.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color(0xFF060609)
                )
                .padding(20.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = "Pulse Visualizer",
            color = Color.White,
            fontSize = 30.sp
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text = "Real-time music visualizer",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(
            modifier =
                Modifier.height(18.dp)
        )

        if (
            media.title ==
            "Nothing playing"
        ) {

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            Color(0xFF15151A)
                    )
            ) {

                Column(
                    modifier =
                        Modifier.padding(20.dp)
                ) {

                    Text(
                        text =
                            "No media session detected",
                        color =
                            Color.White,
                        fontSize =
                            18.sp
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Enable Notification Access so Android can expose your music player.",
                        color =
                            Color.Gray
                    )

                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )

                    Button(
                        onClick =
                            openNotificationAccess,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "Enable access"
                        )
                    }
                }
            }

        } else {

            Artwork(
                bitmap =
                    media.artwork
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            Text(
                text =
                    media.title,
                color =
                    Color.White,
                fontSize =
                    23.sp,
                maxLines =
                    1
            )

            Spacer(
                modifier =
                    Modifier.height(3.dp)
            )

            Text(
                text =
                    media.artist,
                color =
                    Color.Gray,
                fontSize =
                    16.sp,
                maxLines =
                    1
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            RealVisualizer(
                bands =
                    bands,
                playing =
                    media.playing
            )

            Spacer(
                modifier =
                    Modifier.height(14.dp)
            )

            PlaybackControls(
                playing =
                    media.playing
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            Button(
                onClick =
                    openFullScreen,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "Open full-screen visualizer"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick =
                    enableVisualizer,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    if (capturing)
                        "Audio capture active"
                    else
                        "Enable live audio"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick = {
                    MediaRepository.next()
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "Skip to next song"
                )
            }
        }
    }
}

@Composable
fun Artwork(
    bitmap: android.graphics.Bitmap?
) {

    if (bitmap != null) {

        Image(
            bitmap =
                bitmap.asImageBitmap(),

            contentDescription =
                "Album artwork",

            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        RoundedCornerShape(22.dp)
                    )
        )

    } else {

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        RoundedCornerShape(22.dp)
                    )
                    .background(
                        Color(0xFF202027)
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Text(
                text = "♪",
                color =
                    Color(0xFF9B7BFF),
                fontSize =
                    80.sp
            )
        }
    }
}

@Composable
fun PlaybackControls(
    playing: Boolean
) {

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
                color =
                    Color.White,
                fontSize =
                    30.sp
            )
        }

        IconButton(
            onClick = {
                MediaRepository.togglePlayPause()
            },

            modifier =
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Color(0xFF9B7BFF)
                    )
        ) {

            Text(
                text =
                    if (playing)
                        "Ⅱ"
                    else
                        "▶",

                color =
                    Color.White,

                fontSize =
                    27.sp
            )
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
                color =
                    Color.White,
                fontSize =
                    30.sp
            )
        }
    }
}

@Composable
fun RealVisualizer(
    bands: FloatArray,
    playing: Boolean
) {

    var pulse by
        remember {
            mutableFloatStateOf(0f)
        }

    LaunchedEffect(playing) {

        while (playing) {

            pulse += 0.05f

            delay(16)
        }
    }

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(
                    RoundedCornerShape(22.dp)
                )
                .background(
                    Color(0xFF0D0D13)
                )
    ) {

        val count =
            minOf(
                48,
                bands.size
            )

        val gap = 3f

        val width =
            (
                size.width -
                    gap *
                    (count + 1)
                ) / count

        for (i in 0 until count) {

            val audio =
                bands[i]
                    .coerceIn(
                        0f,
                        1f
                    )

            val height =
                (
                    size.height *
                        (
                            0.035f +
                                audio *
                                0.92f
                            )
                    )
                        .coerceAtMost(
                            size.height
                        )

            val x =
                gap +
                    i *
                    (
                        width +
                            gap
                    )

            val y =
                (
                    size.height -
                        height
                ) / 2f

            drawRoundRect(
                color =
                    Color(
                        red =
                            0.45f +
                                audio *
                                0.25f,

                        green =
                            0.30f +
                                audio *
                                0.25f,

                        blue =
                            1f,

                        alpha =
                            0.75f +
                                audio *
                                0.25f
                    ),

                topLeft =
                    androidx.compose.ui.geometry.Offset(
                        x,
                        y
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
    }
}
