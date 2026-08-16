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

            updateWidgets(
                context,
                manager,
                widgetIds,
                title,
                artist,
                "Loading lyrics..."
            )

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
            originalTitle: String,
            originalArtist: String
        ): String {

            /*
             * Clean obvious YouTube title additions.
             *
             * Example:
             *
             * My Song (Official Lyric Video)
             *
             * becomes:
             *
             * My Song
             */

            val cleanedTitle =
                cleanTitle(originalTitle)

            /*
             * ------------------------------------------------
             * FIRST SEARCH
             * ------------------------------------------------
             *
             * Search using BOTH title and artist.
             */

            val artistResult =
                searchLrclib(
                    cleanedTitle,
                    originalArtist
                )

            if (
                artistResult.isNotBlank()
            ) {

                return artistResult
            }

            /*
             * ------------------------------------------------
             * SECOND SEARCH
             * ------------------------------------------------
             *
             * If title + artist failed,
             * search using TITLE ONLY.
             */

            val titleOnlyResult =
                searchLrclib(
                    cleanedTitle,
                    null
                )

            if (
                titleOnlyResult.isNotBlank()
            ) {

                return titleOnlyResult
            }

            return "Lyrics not found"
        }


        private fun searchLrclib(
            title: String,
            artist: String?
        ): String {

            return try {

                val encodedTitle =
                    URLEncoder.encode(
                        title,
                        "UTF-8"
                    )

                val urlString =
                    if (
                        !artist.isNullOrBlank()
                    ) {

                        val encodedArtist =
                            URLEncoder.encode(
                                artist,
                                "UTF-8"
                            )

                        "https://lrclib.net/api/search" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist"

                    } else {

                        /*
                         * Title-only search.
                         */

                        "https://lrclib.net/api/search" +
                        "?q=$encodedTitle"
                    }

                val url =
                    URL(urlString)

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

                    return ""
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

                if (
                    results.length() == 0
                ) {

                    return ""
                }

                findBestLyrics(
                    results,
                    title,
                    artist
                )

            } catch (
                _: Exception
            ) {

                ""
            }
        }


        private fun findBestLyrics(
            results: JSONArray,
            requestedTitle: String,
            requestedArtist: String?
        ): String {

            var bestLyrics =
                ""

            var bestScore =
                -1

            for (
                index in 0 until results.length()
            ) {

                val item =
                    results.getJSONObject(index)

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

                        plainLyrics.trim()
                    }

                if (
                    lyrics.isBlank()
                ) {

                    continue
                }

                var score =
                    0

                /*
                 * Exact title match.
                 */

                if (
                    resultTitle.equals(
                        requestedTitle,
                        ignoreCase = true
                    )
                ) {

                    score += 10

                } else if (
                    normalise(
                        resultTitle
                    ).contains(
                        normalise(
                            requestedTitle
                        )
                    )
                ) {

                    score += 5
                }

                /*
                 * Artist match is only relevant
                 * during the first search.
                 */

                if (
                    !requestedArtist.isNullOrBlank()
                ) {

                    if (
                        resultArtist.equals(
                            requestedArtist,
                            ignoreCase = true
                        )
                    ) {

                        score += 10

                    } else if (
                        normalise(
                            resultArtist
                        ).contains(
                            normalise(
                                requestedArtist
                            )
                        )
                    ) {

                        score += 5
                    }
                }

                /*
                 * Prefer synced lyrics.
                 */

                if (
                    syncedLyrics.isNotBlank()
                ) {

                    score += 3
                }

                if (
                    score > bestScore
                ) {

                    bestScore =
                        score

                    bestLyrics =
                        lyrics
                }
            }

            return bestLyrics
        }


        private fun cleanTitle(
            title: String
        ): String {

            var result =
                title.trim()

            /*
             * Remove common YouTube additions.
             */

            val removablePatterns =
                listOf(

                    "(Official Lyric Video)",
                    "[Official Lyric Video]",
                    "(Official Lyrics)",
                    "[Official Lyrics]",

                    "(Lyric Video)",
                    "[Lyric Video]",
                    "(Lyrics)",
                    "[Lyrics]",

                    "(Official Audio)",
                    "[Official Audio]",

                    "(Official Video)",
                    "[Official Video]",

                    "(Official Music Video)",
                    "[Official Music Video]",

                    "(Audio)",
                    "[Audio]"
                )

            for (
                pattern in removablePatterns
            ) {

                result =
                    result.replace(
                        pattern,
                        "",
                        ignoreCase = true
                    )
            }

            return result
                .replace(
                    Regex(
                        "\\s{2,}"
                    ),
                    " "
                )
                .trim()
        }


        private fun normalise(
            value: String
        ): String {

            return value
                .lowercase()
                .replace(
                    Regex(
                        "[^a-z0-9 ]"
                    ),
                    ""
                )
                .replace(
                    Regex(
                        "\\s+"
                    ),
                    " "
                )
                .trim()
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
                    it.isNotBlank()
                }
                .joinToString("\n")
        }


        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            title: String,
            artist: String,
            lyrics: String
        ) {

            for (
                widgetId in widgetIds
            ) {

                try {

                    val views =
                        RemoteViews(
                            context.packageName,
                            R.layout.widget_lyrics
                        )

                    views.setTextViewText(
                        R.id.lyrics_title,
                        title
                    )

                    views.setTextViewText(
                        R.id.lyrics_artist,
                        artist
                    )

                    views.setTextViewText(
                        R.id.lyrics_text,
                        lyrics
                    )

                    val intent =
                        Intent(
                            context,
                            MainActivity::class.java
                        ).apply {

                            flags =
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }

                    val pendingIntent =
                        PendingIntent.getActivity(
                            context,
                            widgetId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                        )

                    views.setOnClickPendingIntent(
                        R.id.lyrics_root,
                        pendingIntent
                    )

                    manager.updateAppWidget(
                        widgetId,
                        views
                    )

                } catch (
                    _: Exception
                ) {

                    /*
                     * Never allow widget errors to
                     * interfere with media detection.
                     */
                }
            }
        }
    }


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        updateAll(context)
    }


    override fun onEnabled(
        context: Context
    ) {

        updateAll(context)
    }


    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {
    }


    override fun onDisabled(
        context: Context
    ) {
    }
}
