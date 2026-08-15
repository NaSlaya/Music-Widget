package com.pulsevisualizer

data class MediaInfo(
    val packageName: String = "",
    val appName: String = "",
    val title: String = "Nothing playing",
    val artist: String = "",
    val artwork: android.graphics.Bitmap? = null,
    val playing: Boolean = false
)
