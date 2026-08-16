package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class LyricsWidgetProvider : AppWidgetProvider() {

    companion object {

        private val executor =
            Executors.newSingleThreadExecutor()

        private var lastSongKey = ""
        private var lastLyrics = ""

        fun updateAll(context: Context) {

            val manager =
                AppWidgetManager.getInstance(context)

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val widgetIds =
                manager.getAppWidgetIds(component)

            if (widgetIds.isEmpty()) {
                return
            }

            val media =
                MediaRepository.media.value

            val title =
                media.title
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: "Nothing playing"

            val artist =
                media.artist
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: ""

            /*
             * No song playing.
             */

            if (
                title.equals(
                    "Nothing playing",
                    ignoreCase = true
                )
            ) {

                updateWidgets(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    "Play a song to see lyrics"
                )

                return
            }

            val songKey =
                "$title|$artist"

            /*
             * Use cached lyrics if this is
             * the same song.
             */

            if (
                songKey == lastSongKey &&
                lastLyrics.isNotBlank()
            ) {

                updateWidgets(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    lastLyrics
                )

                return
            }

            /*
             * Immediately show a loading message.
             */

            updateWidgets(
                context,
                manager,
                widgetIds,
                title,
                artist,
                "Loading lyrics..."
            )

            /*
             * Don't perform network requests on
             * Android's widget/main thread.
             */

            executor.execute {

                val lyrics =
                    fetchLyrics(
                        title,
                        artist
                    )

                lastSongKey =
                    songKey

                lastLyrics =
                    lyrics

                updateWidgets(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    lyrics
                )
            }
        }


        private fun fetchLyrics(
            title: String,
            artist: String
        ): String {

            return try {

                val encodedTitle =
                    URLEncoder.encode(
                        title,
                        "UTF-8"
                    )

                val encodedArtist =
                    URLEncoder.encode(
                        artist,
                        "UTF-8"
                    )

                /*
                 * LRCLIB's search endpoint doesn't require
                 * album or duration and returns matching
                 * lyric records.
                 */

                val url =
                    URL(
                        "https://lrclib.net/api/search" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist"
                    )

                val connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    10000

                connection.readTimeout =
                    10000

                connection.setRequestProperty(
                    "User-Agent",
                    "PulseVisualizer/1.0 " +
                    "(https://github.com/NaSlaya/Music-Widget)"
                )

                val responseCode =
                    connection.responseCode

                if (
                    responseCode !=
                    HttpURLConnection.HTTP_OK
                ) {

                    connection.disconnect()

                    return "Lyrics unavailable"
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val results =
                    JSONArray(response)

                if (results.length() == 0) {
                    return "Lyrics not found"
                }

                /*
                 * Find the best matching result.
                 */

                var bestLyrics =
                    ""

                var bestScore =
                    -1

                for (i in 0 until results.length()) {

                    val item =
                        results.getJSONObject(i)

                    val resultTitle =
                        item.optString(
                            "trackName"
                        )

                    val resultArtist =
                        item.optString(
                            "artistName"
                        )

                    val syncedLyrics =
                        item.optString(
                            "syncedLyrics"
                        )

                    val plainLyrics =
                        item.optString(
                            "plainLyrics"
                        )

                    val lyrics =
                        if (
                            syncedLyrics.isNotBlank()
                        ) {
                            cleanSyncedLyrics(
                                syncedLyrics
                            )
                        } else {
                            plainLyrics
                        }

                    if (lyrics.isBlank()) {
                        continue
                    }

                    var score =
                        0

                    if (
                        resultTitle.equals(
                            title,
                            ignoreCase = true
                        )
                    ) {
                        score += 3
                    }

                    if (
                        resultArtist.equals(
                            artist,
                            ignoreCase = true
                        )
                    ) {
                        score += 3
                    }

                    if (
                        syncedLyrics.isNotBlank()
                    ) {
                        score += 2
                    }

                    if (score > bestScore) {

                        bestScore =
                            score

                        bestLyrics =
                            lyrics
                    }
                }

                if (bestLyrics.isBlank()) {
                    "Lyrics not found"
                } else {
                    bestLyrics
                }

            } catch (
                exception: Exception
            ) {

                "Unable to load lyrics"
            }
        }


        private fun cleanSyncedLyrics(
            lyrics: String
        ): String {

            return lyrics
                .lines()
                .map { line ->

                    line.replace(
                        Regex(
                            """^\[\d{1,2}:\d{2}(?:\.\d{1,3})?](?:\s*)"""
                        ),
                        ""
                    ).trim()
                }
                .filter {
                    it.isNot
