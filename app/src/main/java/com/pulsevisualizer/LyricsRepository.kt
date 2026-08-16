package com.pulsevisualizer

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LyricsRepository {

    private const val SEARCH_URL =
        "https://lrclib.net/api/search"

    suspend fun getLyrics(
        title: String,
        artist: String
    ): LyricsResult? = withContext(Dispatchers.IO) {

        if (
            title.isBlank() ||
            artist.isBlank()
        ) {
            return@withContext null
        }

        val url = Uri.parse(SEARCH_URL)
            .buildUpon()
            .appendQueryParameter(
                "track_name",
                title
            )
            .appendQueryParameter(
                "artist_name",
                artist
            )
            .build()
            .toString()

        val connection =
            URL(url).openConnection()
                as HttpURLConnection

        try {

            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 10000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                "PulseVisualizer/1.0"
            )

            if (
                connection.responseCode !in 200..299
            ) {
                return@withContext null
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val results =
                JSONArray(response)

            if (
                results.length() == 0
            ) {
                return@withContext null
            }

            val wantedTitle =
                normalize(title)

            val wantedArtist =
                normalize(artist)

            var best: JSONObject? = null
            var bestScore = Int.MIN_VALUE

            for (
                i in 0 until results.length()
            ) {

                val item =
                    results.optJSONObject(i)
                        ?: continue

                val resultTitle =
                    normalize(
                        item.optString(
                            "trackName"
                        )
                    )

                val resultArtist =
                    normalize(
                        item.optString(
                            "artistName"
                        )
                    )

                var score = 0

                if (
                    resultTitle ==
                    wantedTitle
                ) {
                    score += 100
                }

                if (
                    resultArtist ==
                    wantedArtist
                ) {
                    score += 100
                }

                if (
                    resultTitle.contains(
                        wantedTitle
                    ) ||
                    wantedTitle.contains(
                        resultTitle
                    )
                ) {
                    score += 30
                }

                if (
                    resultArtist.contains(
                        wantedArtist
                    ) ||
                    wantedArtist.contains(
                        resultArtist
                    )
                ) {
                    score += 30
                }

                if (
                    item.optString(
                        "syncedLyrics"
                    ).isNotBlank()
                ) {
                    score += 50
                }

                if (score > bestScore) {
                    bestScore = score
                    best = item
                }
            }

            val item =
                best ?: return@withContext null

            val resultTitle =
                item.optString(
                    "trackName",
                    title
                )

            val resultArtist =
                item.optString(
                    "artistName",
                    artist
                )

            val synced =
                item.optString(
                    "syncedLyrics"
                )

            if (synced.isNotBlank()) {

                val lines =
                    parseLrc(synced)

                if (lines.isNotEmpty()) {

                    return@withContext LyricsResult(
                        title = resultTitle,
                        artist = resultArtist,
                        lines = lines,
                        synced = true
                    )
                }
            }

            val plain =
                item.optString(
                    "plainLyrics"
                )

            if (plain.isNotBlank()) {

                val lines =
                    plain.lines()
                        .mapIndexed { index, line ->

                            LyricLine(
                                timeMs =
                                    index * 3500L,
                                text =
                                    line.trim()
                            )
                        }
                        .filter {
                            it.text.isNotBlank()
                        }

                if (lines.isNotEmpty()) {

                    return@withContext LyricsResult(
                        title = resultTitle,
                        artist = resultArtist,
                        lines = lines,
                        synced = false
                    )
                }
            }

            null

        } catch (
            _: Exception
        ) {

            null

        } finally {

            connection.disconnect()
        }
    }

    private fun normalize(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                Regex("[^a-z0-9]+"),
                " "
            )
            .trim()
    }

    private fun parseLrc(
        lrc: String
    ): List<LyricLine> {

        val output =
            mutableListOf<LyricLine>()

        val regex =
            Regex(
                """\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]"""
            )

        for (
            rawLine in lrc.lines()
        ) {

            val matches =
                regex.findAll(
                    rawLine
                ).toList()

            if (
                matches.isEmpty()
            ) {
                continue
            }

            val text =
                rawLine
                    .replace(
                        regex,
                        ""
                    )
                    .trim()

            if (
                text.isBlank()
            ) {
                continue
            }

            for (
                match in matches
            ) {

                val minutes =
                    match.groupValues[1]
                        .toLongOrNull()
                        ?: continue

                val seconds =
                    match.groupValues[2]
                        .toLongOrNull()
                        ?: continue

                val fraction =
                    match.groupValues[3]
                        .toLongOrNull()
                        ?: 0L

                val fractionMs =
                    when (
                        match.groupValues[3].length
                    ) {
                        1 -> fraction * 100L
                        2 -> fraction * 10L
                        else -> fraction
                    }

                output.add(
                    LyricLine(
                        timeMs =
                            minutes * 60000L +
                            seconds * 1000L +
                            fractionMs,
                        text = text
                    )
                )
            }
        }

        return output.sortedBy {
            it.timeMs
        }
    }
}
