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
            Handler(Looper.getMainLooper())

        private var cachedContext:
            Context? = null

        private var lastSongKey =
            ""

        private var lyricLines:
            List<LyricLine> =
            emptyList()

        private var loading =
            false

        /*
         * -1 = before first lyric
         * -2 = instrumental / silent gap
         * >= 0 = current lyric
         */
        private var lastDisplayedIndex =
            -1

        /*
         * Check playback position frequently,
         * but ONLY update the widget when the
         * actual lyric changes.
         */
        private const val UPDATE_INTERVAL =
            100L

        /*
         * If there is more than this much time
         * before the next lyric, blank the widget.
         */
        private const val SILENCE_GAP_MS =
            2500L

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
                    .getInstance(context)

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

            val artist =
                media.artist
                    .trim()

            /*
             * Nothing is playing.
             * The widget must be completely blank.
             */

            if (
                title.isBlank()
            ) {

                lyricLines =
                    emptyList()

                lastSongKey =
                    ""

                lastDisplayedIndex =
                    -1

                updateWidgetText(
                    context,
                    ""
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

            /*
             * A new song has started.
             */

            if (
                songKey !=
                lastSongKey
            ) {

                lastSongKey =
                    songKey

                lyricLines =
                    emptyList()

                lastDisplayedIndex =
                    -1

                /*
                 * Keep blank while lyrics are
                 * being retrieved.
                 */

                updateWidgetText(
                    context,
                    ""
                )

                fetchLyrics(
                    context,
                    cleanedTitle,
                    artist
                )
            }

            startPositionUpdates(
                context
            )
        }


        private fun fetchLyrics(
            context: Context,
            title: String,
            artist: String
        ) {

            if (
                loading
            ) {

                return
            }

            loading =
                true

            executor.execute {

                val result =
                    fetchLyricsFromLrclib(
                        title,
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

                    /*
                     * Ignore results from an old song.
                     */

                    if (
                        currentKey !=
                        lastSongKey
                    ) {

                        return@post
                    }

                    lyricLines =
                        result

                    /*
                     * No synced lyrics available.
                     * Leave the widget blank.
                     */

                    if (
                        lyricLines.isEmpty()
                    ) {

                        updateWidgetText(
                            context,
                            ""
                        )

                        return@post
                    }

                    /*
                     * Immediately calculate the
                     * correct lyric for the current
                     * playback position.
                     */

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

            val resultWithArtist =
                searchLrclib(
                    title,
                    artist
                )

            if (
                resultWithArtist.isNotEmpty()
            ) {

                return resultWithArtist
            }

            /*
             * Fallback:
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
                    URL(urlString)
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
                    JSONArray(response)

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
                    results.getJSONObject(
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
                 * We specifically require
                 * timestamped lyrics.
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
                    normalise(resultTitle)
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
                        normalise(resultArtist)
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
                        .findAll(rawLine)
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
                        match.groupValues[3]

                    val fractionMs =
                        when (
                            fraction.length
                        ) {

                            1 ->
                                fraction
                                    .toLongOrNull()
                                    ?.times(100)
                                    ?: 0L

                            2 ->
                                fraction
                                    .toLongOrNull()
                                    ?.times(10)
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

                if (
                    lastDisplayedIndex !=
                    -2
                ) {

                    lastDisplayedIndex =
                        -2

                    updateWidgetText(
                        cachedContext,
                        ""
                    )
                }

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()

            /*
             * Before the first lyric:
             * BLANK.
             *
             * This handles instrumental intros.
             */

            if (
                position <
                lyricLines.first().timeMs
            ) {

                if (
                    force ||
                    lastDisplayedIndex != -1
                ) {

                    lastDisplayedIndex =
                        -1

                    updateWidgetText(
                        cachedContext,
                        ""
                    )
                }

                return
            }

            var currentIndex =
                -1

            for (
                index in
                lyricLines.indices
            ) {

                if (
                    lyricLines[index].timeMs <=
                    position
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

                return
            }

            /*
             * Check for a large instrumental gap.
             */

            val nextIndex =
                currentIndex + 1

            if (
                nextIndex <
                lyricLines.size
            ) {

                val nextTime =
                    lyricLines[
                        nextIndex
                    ].timeMs

                val timeUntilNext =
                    nextTime - position

                if (
                    timeUntilNext >
                    SILENCE_GAP_MS
                ) {

                    if (
                        lastDisplayedIndex !=
                        -2
                    ) {

                        lastDisplayedIndex =
                            -2

                        updateWidgetText(
                            cachedContext,
                            ""
                        )
                    }

                    return
                }
            }

            /*
             * The lyric hasn't changed.
             *
             * IMPORTANT:
             * Don't update the widget.
             *
             * This is what eliminates the flashing.
             */

            if (
                !force &&
                currentIndex ==
                lastDisplayedIndex
            ) {

                return
            }

            lastDisplayedIndex =
                currentIndex

            val text =
                lyricLines[
                    currentIndex
                ].text

            updateWidgetText(
                cachedContext,
                text
            )
        }
                private fun updateWidgetText(
            context: Context?,
            text: String
        ) {

            val safeContext =
                context
                    ?: return

            val manager =
                AppWidgetManager
                    .getInstance(
                        safeContext
                    )

            val component =
                ComponentName(
                    safeContext,
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

            /*
             * ONE RemoteViews update.
             *
             * No alpha loop.
             * No repeated redraws.
             * No flashing.
             */

            val views =
                RemoteViews(
                    safeContext.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_text,
                text
            )

            /*
             * Keep the lyric visible whenever
             * there is actually a lyric.
             */

            views.setFloat(
                R.id.lyrics_text,
                "setAlpha",
                if (
                    text.isBlank()
                ) {
                    0f
                } else {
                    1f
                }
            )

            setClickAction(
                safeContext,
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
                    Regex("\\s{2,}"),
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
                    Regex("[^a-z0-9 ]"),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
        }
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

        lastDisplayedIndex =
            -1

        cachedContext =
            null
    }
}
