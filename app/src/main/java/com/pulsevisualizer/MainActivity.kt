package com.pulsevisualizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            NotificationAccessHelper.hasNotificationAccess(this)
        ) {
            startMediaSystem()
        }

        setContent {
            PulseTheme {
                PulseApp(
                    openNotificationAccess = {
                        NotificationAccessHelper.requestNotificationAccess(
                            this
                        )
                    },
                    openFullScreen = {
                        startActivity(
                            Intent(
                                this,
                                VisualizerActivity::class.java
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNotificationAccess()
    }

    private fun checkNotificationAccess() {

        if (
            NotificationAccessHelper.hasNotificationAccess(this)
        ) {
            startMediaSystem()
        } else {
            NotificationAccessHelper.requestNotificationAccess(this)
        }
    }

    private fun startMediaSystem() {

        try {
            MediaRepository.start(
                applicationContext
            )
        } catch (
            _: Exception
        ) {
            // Prevent repository startup errors from
            // crashing the Activity.
        }
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
                    Color(0xFF050509),
                surface =
                    Color(0xFF101017),
                primary =
                    Color(0xFF9C6CFF),
                secondary =
                    Color(0xFF6E8BFF)
            ),
        content = content
    )
}

@Composable
fun PulseApp(
    openNotificationAccess: () -> Unit,
    openFullScreen: () -> Unit
) {

    val media by
        MediaRepository.media.collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF090711),
                            Color(0xFF050509),
                            Color(0xFF020204)
                        )
                    )
                )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text = "PULSE",
                color = Color.White,
                fontSize = 31.sp,
                letterSpacing = 5.sp
            )

            Text(
                text = "MUSIC VISUALIZER",
                color = Color(0xFF8E7AAE),
                fontSize = 10.sp,
                letterSpacing = 3.sp
            )

            Spacer(
                modifier =
                    Modifier.height(22.dp)
            )

            if (
                media.title == "Nothing playing"
            ) {

                EmptyMediaCard(
                    openNotificationAccess
                )

            } else {

                Artwork(
                    media.artwork
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    maxLines = 1
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        media.artist.ifBlank {
                            "Unknown artist"
                        },
                    color = Color(0xFFAAA5B5),
                    fontSize = 15.sp,
                    maxLines = 1
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                ReactiveVisualizer(
                    playing = media.playing,
                    trackKey =
                        media.title +
                            media.artist
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                PlaybackControls(
                    playing = media.playing
                )

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )

                Button(
                    onClick = openFullScreen,
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text =
                            "OPEN IMMERSIVE VISUALIZER"
                    )
                }
            }
        }
    }
}
@Composable
private fun EmptyMediaCard(
    openNotificationAccess: () -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFF111018)
            )
    ) {

        Column(
            modifier =
                Modifier.padding(24.dp)
        ) {

            Text(
                text =
                    "NO MUSIC DETECTED",
                color =
                    Color.White,
                fontSize =
                    18.sp,
                letterSpacing =
                    1.5.sp
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Start playing music in Spotify, YouTube Music or another supported media app.",
                color =
                    Color(0xFF9994A3),
                fontSize =
                    14.sp
            )

            Spacer(
                modifier =
                    Modifier.height(18.dp)
            )

            Button(
                onClick =
                    openNotificationAccess,
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(16.dp)
            ) {
                Text(
                    "MEDIA ACCESS SETTINGS"
                )
            }
        }
    }
}

@Composable
private fun Artwork(
    bitmap: android.graphics.Bitmap?
) {

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(
                    RoundedCornerShape(30.dp)
                )
                .background(
                    Color(0xFF17131F)
                ),
        contentAlignment =
            Alignment.Center
    ) {

        if (
            bitmap != null
        ) {

            Image(
                bitmap =
                    bitmap.asImageBitmap(),
                contentDescription =
                    "Album artwork",
                modifier =
                    Modifier.fillMaxSize()
            )

        } else {

            Text(
                text = "♪",
                color =
                    Color(0xFF9C6CFF),
                fontSize =
                    90.sp
            )
        }
    }
}

@Composable
private fun PlaybackControls(
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
                    28.sp
            )
        }

        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF9C6CFF),
                                Color(0xFF596FFF)
                            )
                        )
                    ),
            contentAlignment =
                Alignment.Center
        ) {

            IconButton(
                onClick = {
                    MediaRepository.togglePlayPause()
                }
            ) {

                Text(
                    text =
                        if (playing) {
                            "Ⅱ"
                        } else {
                            "▶"
                        },
                    color =
                        Color.White,
                    fontSize =
                        27.sp
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
                color =
                    Color.White,
                fontSize =
                    28.sp
            )
        }
    }
}

@Composable
private fun ReactiveVisualizer(
    playing: Boolean,
    trackKey: String
) {

    var phase by
        remember(trackKey) {
            mutableFloatStateOf(0f)
        }

    val transition =
        rememberInfiniteTransition(
            label = "visualizer"
        )

    val movement by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 1100,
                            easing =
                                LinearEasing
                        ),
                    repeatMode =
                        RepeatMode.Restart
                ),
            label = "movement"
        )

    LaunchedEffect(
        playing,
        trackKey
    ) {

        while (playing) {

            phase += 0.055f

            delay(16)
        }
    }

    Canvas(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(
                    RoundedCornerShape(24.dp)
                )
                .background(
                    Color(0xFF0D0B13)
                )
    ) {

        val bars = 56
        val gap = 3f

        val barWidth =
            (
                size.width -
                    gap *
                    (bars + 1)
            ) / bars

        val seed =
            abs(
                trackKey.hashCode()
            )

        for (
            i in 0 until bars
        ) {

            val normalized =
                i.toFloat() / bars

            val harmonic =
                0.5f +
                    0.5f *
                        sin(
                            phase * 3.0f +
                                normalized *
                                (
                                    5.0f +
                                        seed % 7
                                )
                        )

            val second =
                0.5f +
                    0.5f *
                        sin(
                            phase * 1.7f +
                                normalized * 12f
                        )

            val shape =
                harmonic * 0.65f +
                    second * 0.35f

            val heightFactor =
                if (playing) {
                    0.12f +
                        shape * 0.72f
                } else {
                    0.06f
                }

            val pulse =
                if (playing) {
                    1f +
                        movement * 0.08f
                } else {
                    1f
                }

            val barHeight =
                size.height *
                    heightFactor *
                    pulse

            val x =
                gap +
                    i *
                    (barWidth + gap)

            val y =
                (
                    size.height -
                        barHeight
                ) / 2f

            drawRoundRect(
                brush =
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFB58AFF),
                            Color(0xFF716CFF),
                            Color(0xFF3E4EFF)
                        )
                    ),
                topLeft =
                    androidx.compose.ui.geometry
                        .Offset(
                            x,
                            y
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
                            8f,
                            8f
                        )
            )
        }

        drawCircle(
            color =
                Color(0xFF9C6CFF)
                    .copy(
                        alpha = 0.12f
                    ),
            radius =
                size.minDimension *
                    0.38f,
            center =
                androidx.compose.ui.geometry
                    .Offset(
                        size.width / 2f,
                        size.height / 2f
                    ),
            style =
                Stroke(2f)
        )
    }
}
