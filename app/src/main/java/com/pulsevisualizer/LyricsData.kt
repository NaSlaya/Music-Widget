package com.pulsevisualizer

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class LyricsResult(
    val title: String,
    val artist: String,
    val lines: List<LyricLine>,
    val synced: Boolean
)
