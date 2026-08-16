package com.pulsevisualizer

data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

data class LyricLine(
    val timeMs: Long,
    val endMs: Long,
    val text: String,
    val words: List<LyricWord>
)

data class LyricsDocument(
    val lines: List<LyricLine>,
    val source: String = "",
    val confidence: Float = 1f
)
