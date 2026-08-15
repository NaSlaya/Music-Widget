package com.pulsevisualizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlin.math.abs
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MediaRepository.start(this)

        setContent {
            PulseTheme {
                PulseApp(
                    openNotificationAccess = {
                        startActivity(
                            Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        )
                    },
                    openFullScreen = {
                        startActivity(
                            Intent(this, VisualizerActivity::class.java)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun PulseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = Color(0xFF08080B),
            surface = Color(0xFF141419),
            primary = Color(0xFF9B7BFF),
            secondary = Color(0xFFB9A5FF)
        ),
        content = content
    )
}

@Composable
fun PulseApp(
    openNotificationAccess: () -> Unit,
    openFullScreen: () -> Unit
) {
    val media by MediaRepository.media.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080B))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Pulse Visualizer",
            color = Color.White,
            fontSize = 30.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Music controls & visualizer",
            color = Color.Gray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (media.title == "Nothing playing") {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF15151A)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "No media session detected",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enable Notification Access so Android can expose active media sessions.",
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = openNotificationAccess,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enable access")
                    }
                }
            }

        } else {

            Artwork(media.artwork)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = media.title,
                color = Color.White,
                fontSize = 23.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = media.artist,
                color = Color.Gray,
                fontSize = 16.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = media.packageName,
                color = Color.DarkGray,
                fontSize = 11.sp,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(14.dp))

            MiniVisualizer(
                playing = media.playing
            )

            Spacer(modifier = Modifier.height(12.dp))

            PlaybackControls(
                playing = media.playing
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = openFullScreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open full-screen visualizer")
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    MediaRepository.next()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip to next song")
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
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Album artwork",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF202027)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "♪",
                color = Color(0xFF9B7BFF),
                fontSize = 80.sp
            )
        }
    }
}

@Composable
fun PlaybackControls(
    playing: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(
            onClick = {
                MediaRepository.previous()
            },
            modifier = Modifier.size(58.dp)
        ) {
            Text(
                text = "⏮",
                color = Color.White,
                fontSize = 30.sp
            )
        }

        IconButton(
            onClick = {
                MediaRepository.togglePlayPause()
            },
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color(0xFF9B7BFF))
        ) {
            Text(
                text = if (playing) "Ⅱ" else "▶",
                color = Color.White,
                fontSize = if (playing) 25.sp else 27.sp
            )
        }

        IconButton(
            onClick = {
                MediaRepository.next()
            },
            modifier = Modifier.size(58.dp)
        ) {
            Text(
                text = "⏭",
                color = Color.White,
                fontSize = 30.sp
            )
        }
    }
}

@Composable
fun MiniVisualizer(
    playing: Boolean
) {
    var phase by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(playing) {
        while (playing) {
            phase += 0.11f
            delay(16)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(95.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101016))
    ) {
        val bars = 48
        val gap = 3f
        val barWidth = (size.width - gap * (bars + 1)) / bars

        for (i in 0 until bars) {
            val wave1 = abs(sin(phase + i * 0.22f))
            val wave2 = abs(sin(phase * 0.63f + i * 0.11f))

            val multiplier = if (playing) {
                0.15f + wave1 * 0.55f + wave2 * 0.25f
            } else {
                0.08f
            }

            val barHeight = size.height * multiplier

            val left = gap + i * (barWidth + gap)
            val top = (size.height - barHeight) / 2f

            drawRect(
                color = Color(0xFF9B7BFF),
                topLeft = androidx.compose.ui.geometry.Offset(
                    left,
                    top
                ),
                size = androidx.compose.ui.geometry.Size(
                    barWidth,
                    barHeight
                )
            )
        }
    }
}
