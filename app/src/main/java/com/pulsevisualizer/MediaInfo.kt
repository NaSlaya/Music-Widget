package com.pulsevisualizer

import android.graphics.Bitmap

data class MediaInfo(
    val packageName: String = "",
    val appName: String = "",
    val title: String = "",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val playing: Boolean = false,

    // Current playback position reported by the media session.
    val positionMs: Long = 0L,

    // Playback speed.
    // 1.0f = normal speed.
    // 0.0f = paused.
    val playbackSpeed: Float = 0f,

    // System elapsed-real-time timestamp at which the
    // media session last reported its position.
    val positionUpdateTimeMs: Long = 0L
)
