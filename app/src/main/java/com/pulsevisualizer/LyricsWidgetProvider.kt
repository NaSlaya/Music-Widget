package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

import org.json.JSONArray

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class LyricsWidgetProvider :
    AppWidgetProvider() {

    companion object {

        private val executor =
            Executors.newSingleThreadExecutor()

        private val handler =
            Handler(
                Looper.getMainLooper()
            )

        private var lastSongKey =
            ""

        private var lyricLines:
            List<LyricLine> =
            emptyList()

        private var loading =
            false

        private var lastDisplayedLine =
            ""

        private var lastDisplayedIndex =
            -1

        private const val UPDATE_INTERVAL =
            250L

        private val positionUpdater =
            object : Runnable {

                override fun run() {

                    updateCurrentLine()

                    handler.postDelayed(
                        this,
                        UPDATE_INTERVAL
                    )
                }
            }

        data class LyricLine(
            val timeMs: Long,
            val text: String
        )


        fun updateAll(
            context: Context
        ) {

            cachedContext =
                context.applicationContext

            val manager =
                AppWidgetManager
                    .getInstance(
                        context
                    )

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val widgetIds =
                manager.getAppWidgetIds(
                    component
                )

            if (
                widgetIds.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

            val media =
                MediaRepository
                    .media
                    .value

            val title =
                media.title
                    .trim()
                    .takeIf {
                        it.isNotBlank()
                    }
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

                lyricLines =
                    emptyList()

                lastSongKey =
                    ""

                lastDisplayedLine =
                    ""

                lastDisplayedIndex =
                    -1

                updateWidgets(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    "Play a song to see lyrics"
                )

                stopPositionUpdates()

                return
            }

            val cleanedTitle =
                cleanTitle(
                    title
                )

            val songKey =
                (
                    cleanedTitle +
                    "|" +
                    artist
                )
                    .lowercase()

            if (
                songKey !=
                lastSongKey
            ) {

                lastSongKey =
                    songKey

                lyricLines =
                    emptyList()

                lastDisplayedLine =
                    ""

                lastDisplayedIndex =
                    -1

                updateWidgets(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    "Loading lyrics..."
                )

                fetchLyrics(
                    context,
                    manager,
                    widgetIds,
                    title,
                    artist,
                    cleanedTitle
                )
            }

            startPositionUpdates(
                context
            )
        }


        private fun fetchLyrics(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            originalTitle: String,
            artist: String,
            cleanedTitle: String
        ) {

            if (loading) {
                return
            }

            loading =
                true

            executor.execute {

                val result =
                    fetchLyricsFromLrclib(
                        cleanedTitle,
                        artist
                    )

                handler.post {

                    loading =
                        false

                    val currentMedia =
                        MediaRepository
                            .media
                            .value

                    val currentTitle =
                        cleanTitle(
                            currentMedia.title
                        )

                    val currentArtist =
                        currentMedia.artist
                            .trim()

                    val currentKey =
                        (
                            currentTitle +
                            "|" +
                            currentArtist
                        )
                            .lowercase()

                    if (
                        currentKey !=
                        lastSongKey
                    ) {

                        return@post
                    }

                    lyricLines =
                        result

                    if (
                        lyricLines.isEmpty()
                    ) {

                        updateWidgets(
                            context,
                            manager,
                            widgetIds,
                            originalTitle,
                            artist,
                            "Lyrics not found"
                        )

                        return@post
                    }

                    updateCurrentLine(
                        force = true
                    )
                }
            }
        }


        private fun fetchLyricsFromLrclib(
            title: String,
            artist: String
        ): List<LyricLine> {

            /*
             * First:
             * title + artist
             */

            val firstResult =
                searchLrclib(
                    title,
                    artist
                )

            if (
                firstResult.isNotEmpty()
            ) {

                return firstResult
            }

            /*
             * Second:
             * title only
             */

            return searchLrclib(
                title,
                null
            )
        }


        private fun searchLrclib(
            title: String,
            artist: String?
        ): List<LyricLine> {

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

                        "https://lrclib.net/api/search" +
                        "?q=$encodedTitle"
                    }

                val connection =
                    URL(
                        urlString
                    )
                        .openConnection()
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

                    return emptyList()
                }

                val response =
                    connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                connection.disconnect()

                val results =
                    JSONArray(
                        response
                    )

                if (
                    results.length() == 0
                ) {

                    return emptyList()
                }

                findBestSyncedLyrics(
                    results,
                    title,
                    artist
                )

            } catch (
                _: Exception
            ) {

                emptyList()
            }
        }
                private fun findBestSyncedLyrics(
            results: JSONArray,
            requestedTitle: String,
            requestedArtist: String?
        ): List<LyricLine> {

            var bestLines:
                List<LyricLine> =
                emptyList()

            var bestScore =
                -1

            for (
                index in
                0 until results.length()
            ) {

                val item =
                    results
                        .getJSONObject(
                            index
                        )

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

                /*
                 * We need synced lyrics because
                 * plain lyrics have no timestamps.
                 */

                if (
                    syncedLyrics.isBlank()
                ) {

                    continue
                }

                val lines =
                    parseSyncedLyrics(
                        syncedLyrics
                    )

                if (
                    lines.isEmpty()
                ) {

                    continue
                }

                var score =
                    0

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
                    )
                        .contains(
                            normalise(
                                requestedTitle
                            )
                        )
                ) {

                    score += 5
                }

                if (
                    !requestedArtist
                        .isNullOrBlank()
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
                        )
                            .contains(
                                normalise(
                                    requestedArtist
                                )
                            )
                    ) {

                        score += 5
                    }
                }

                if (
                    lines.size >= 3
                ) {

                    score += 2
                }

                if (
                    score > bestScore
                ) {

                    bestScore =
                        score

                    bestLines =
                        lines
                }
            }

            return bestLines
        }


        private fun parseSyncedLyrics(
            lyrics: String
        ): List<LyricLine> {

            val result =
                mutableListOf<LyricLine>()

            val timestampRegex =
                Regex(
                    """\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]"""
                )

            for (
                rawLine in
                lyrics.lines()
            ) {

                val matches =
                    timestampRegex
                        .findAll(
                            rawLine
                        )
                        .toList()

                if (
                    matches.isEmpty()
                ) {

                    continue
                }

                val text =
                    rawLine
                        .replace(
                            timestampRegex,
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
                        match
                            .groupValues[1]
                            .toLongOrNull()
                            ?: continue

                    val seconds =
                        match
                            .groupValues[2]
                            .toLongOrNull()
                            ?: continue

                    val fraction =
                        match
                            .groupValues[3]

                    val fractionMs =
                        when (
                            fraction.length
                        ) {

                            1 ->
                                fraction
                                    .toLongOrNull()
                                    ?.times(
                                        100
                                    )
                                    ?: 0L

                            2 ->
                                fraction
                                    .toLongOrNull()
                                    ?.times(
                                        10
                                    )
                                    ?: 0L

                            3 ->
                                fraction
                                    .toLongOrNull()
                                    ?: 0L

                            else ->
                                0L
                        }

                    val timeMs =
                        (
                            minutes * 60_000L
                        ) +
                        (
                            seconds * 1_000L
                        ) +
                        fractionMs

                    result.add(
                        LyricLine(
                            timeMs =
                                timeMs,

                            text =
                                text
                        )
                    )
                }
            }

            return result
                .sortedBy {
                    it.timeMs
                }
        }


        private fun startPositionUpdates(
            context: Context
        ) {

            cachedContext =
                context.applicationContext

            handler.removeCallbacks(
                positionUpdater
            )

            handler.post(
                positionUpdater
            )
        }


        private fun stopPositionUpdates() {

            handler.removeCallbacks(
                positionUpdater
            )
        }


        private fun updateCurrentLine(
            force: Boolean = false
        ) {

            if (
                lyricLines.isEmpty()
            ) {

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()

            var currentIndex =
                -1

            for (
                index in
                lyricLines.indices
            ) {

                if (
                    lyricLines[index]
                        .timeMs <= position
                ) {

                    currentIndex =
                        index

                } else {

                    break
                }
            }

            if (
                currentIndex < 0
            ) {

                currentIndex =
                    0
            }

            val currentLine =
                lyricLines[
                    currentIndex
                ]

            if (
                !force &&
                currentIndex ==
                    lastDisplayedIndex
            ) {

                return
            }

            lastDisplayedIndex =
                currentIndex

            lastDisplayedLine =
                currentLine.text

            updateCurrentLineOnWidgets(
                currentLine.text
            )
        }


        private fun updateCurrentLineOnWidgets(
            text: String
        ) {

            val context =
                cachedContext
                    ?: return

            val manager =
                AppWidgetManager
                    .getInstance(
                        context
                    )

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val widgetIds =
                manager.getAppWidgetIds(
                    component
                )

            if (
                widgetIds.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

            val media =
                MediaRepository
                    .media
                    .value

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_title,
                media.title
            )

            views.setTextViewText(
                R.id.lyrics_artist,
                media.artist
            )

            views.setTextViewText(
                R.id.lyrics_text,
                text
            )

            setClickAction(
                context,
                views,
                widgetIds
            )

            for (
                widgetId in widgetIds
            ) {

                manager.updateAppWidget(
                    widgetId,
                    views
                )
            }
        }
                private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            title: String,
            artist: String,
            text: String
        ) {

            cachedContext =
                context.applicationContext

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
                        text
                    )

                    setClickAction(
                        context,
                        views,
                        intArrayOf(
                            widgetId
                        )
                    )

                    manager.updateAppWidget(
                        widgetId,
                        views
                    )

                } catch (
                    _: Exception
                ) {
                }
            }
        }


        private fun setClickAction(
            context: Context,
            views: RemoteViews,
            widgetIds: IntArray
        ) {

            val intent =
                Intent(
                    context,
                    MainActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val requestCode =
                if (
                    widgetIds.isNotEmpty()
                ) {

                    widgetIds[0]

                } else {

                    9001
                }

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.lyrics_root,
                pendingIntent
            )
        }


        private fun cleanTitle(
            title: String
        ): String {

            var result =
                title.trim()

            val patterns =
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
                pattern in patterns
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


        private var cachedContext:
            Context? =
            null
    }


    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        cachedContext =
            context.applicationContext

        updateAll(
            context
        )
    }


    override fun onEnabled(
        context: Context
    ) {

        cachedContext =
            context.applicationContext

        updateAll(
            context
        )
    }


    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {

        if (
            appWidgetIds.isEmpty()
        ) {

            stopPositionUpdates()
        }
    }


    override fun onDisabled(
        context: Context
    ) {

        stopPositionUpdates()

        lyricLines =
            emptyList()

        lastSongKey =
            ""

        lastDisplayedLine =
            ""

        lastDisplayedIndex =
            -1

        cachedContext =
            null
    }
}
