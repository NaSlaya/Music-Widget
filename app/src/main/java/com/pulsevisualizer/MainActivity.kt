package com.pulsevisualizer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
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
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF15151A),
            primary = Color(0xFF9B7BFF)
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

    var showSources by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .padding(20.dp)
    ) {

        Text(
            text = "Pulse Visualizer",
            fontSize = 30.sp,
            color = Color.White
        )

        Text(
            text = "Music visualizer for any compatible media app",
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

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

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = "Enable Notification Access so Android can expose active media sessions.",
                        color = Color.Gray
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    Button(
                        onClick = openNotificationAccess
                    ) {
                        Text("Enable access")
                    }
                }
            }

        } else {

            Artwork(
                artwork = media.artwork
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = media.title,
                color = Color.White,
                fontSize = 24.sp
            )

            Text(
                text = media.artist,
                color = Color.Gray,
                fontSize = 16.sp
            )

            Text(
                text = "Source: ${media.packageName}",
                color = Color.DarkGray,
                fontSize = 12.sp
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            MiniVisualizer()

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Button(
                onClick = openFullScreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open full-screen visualizer")
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            OutlinedButton(
                onClick = {
                    showSources = !showSources
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose media source")
            }

            if (showSources) {

                Text(
                    text = "                PulseApp(
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
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF15151A),
            primary = Color(0xFF9B7BFF)
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
    var showSources by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .padding(20.dp)
    ) {
        Text(
            "Pulse Visualizer",
            fontSize = 30.sp,
            color = Color.White
        )

        Text(
            "Music visualizer for any compatible media app",
            color = Color.Gray
        )

        Spacer(Modifier.height(20.dp))

        if (media.title == "Nothing playing") {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF15151A)
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "No media session detected",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Enable Notification Access so Android can expose active media sessions.",
                        color = Color.Gray
                    )

                    Spacer(Modifier.height(14.dp))

                    Button(onClick = openNotificationAccess) {
                        Text("Enable access")
                    }
                }
            }
        } else {
            Artwork(media.artwork)

            Spacer(Modifier.height(16.dp))

            Text(
                media.title,
                color = Color.White,
                fontSize = 24.sp
            )

            Text(
                media.artist,
                color = Color.Gray,
                fontSize = 16.sp
            )

            Text(
                "Source: ${media.packageName}",
                color = Color.DarkGray,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            MiniVisualizer()

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = openFullScreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open full-screen visualizer")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { showSources = !showSources },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose media source")
            }

            if (showSources) {
                Text(
                    "The selected source system is prepared in this build; additional source filtering will be added in the next phase.",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
fun Artwork(bitmap: android.graphics.Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Album artwork",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF202027)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "♪",
                fontSize = 80.sp,
                color = Color(0xFF9B7BFF)
            )
        }
    }
}

@Composable
fun MiniVisualizer() {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            phase += 0.12f
            delay(16)
        }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
    ) {
        val bars = 42
        val w = size.width / bars

        for (i in 0 until bars) {
            val wave = (sin(phase + i * 0.45f) + 1f) / 2f
            val h = size.height * (0.15f + wave * 0.75f)

            drawRoundRect(
                color = Color(0xFF9B7BFF),
                topLeft = Offset(
                    x = i * w + 2f,
                    y = size.height - h
                ),
                size = Size(
                    width = w - 5f,
                    height = h
                ),
                cornerRadius = CornerRadius(6f, 6f)
            )
        }
    }
}                        startActivity(Intent(this, VisualizerActivity::class.java))
                    }
                )
            }
        }
    }
}

@Composable
fun PulseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF09090B),
            surface = Color(0xFF15151A),
            primary = Color(0xFF9B7BFF)
        ),
        content = content
    )
}

@Composable
fun PulseApp(openNotificationAccess: () -> Unit, openFullScreen: () -> Unit) {
    val media by MediaRepository.media.collectAsState()
    var showSources by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF09090B)).padding(20.dp)
    ) {
        Text("Pulse Visualizer", fontSize = 30.sp, color = Color.White)
        Text("Music visualizer for any compatible media app", color = Color.Gray)

        Spacer(Modifier.height(20.dp))

        if (media.title == "Nothing playing") {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15151A))
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("No media session detected", color = Color.White, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enable Notification Access so Android can expose active media sessions.",
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = openNotificationAccess) {
                        Text("Enable access")
                    }
                }
            }
        } else {
            Artwork(media.artwork)
            Spacer(Modifier.height(16.dp))
            Text(media.title, color = Color.White, fontSize = 24.sp)
            Text(media.artist, color = Color.Gray, fontSize = 16.sp)
            Text("Source: ${media.packageName}", color = Color.DarkGray, fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))
            MiniVisualizer()

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = openFullScreen,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open full-screen visualizer") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showSources = !showSources },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose media source") }

            if (showSources) {
                Text(
                    "The selected source system is prepared in this build; additional source filtering will be added in the next phase.",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
fun Artwork(bitmap: android.graphics.Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = "Album artwork",
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
        )
    } else {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF202027)),
            contentAlignment = Alignment.Center
        ) { Text("♪", fontSize = 80.sp, color = Color(0xFF9B7BFF)) }
    }
}

@Composable
fun MiniVisualizer() {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            phase += 0.12f
            delay(16)
        }
    }
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val bars = 42
        val w = size.width / bars
        for (i in 0 until bars) {
            val wave = (sin(phase + i * 0.45f) + 1f) / 2f
            val h = size.height * (0.15f + wave * 0.75f)
            drawRoundRect(
                color = Color(0xFF9B7BFF),
                left = i * w + 2f,
                top = size.height - h,
                right = i * w + w - 3f,
                bottom = size.height,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}
