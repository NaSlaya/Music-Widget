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

        /*
         * -1 = before lyrics
         * -2 = instrumental/silent gap
         * >= 0 = current lyric index
         */
        private var lastDisplayedIndex =
            -1

        private const val UPDATE_INTERVAL =
            100L

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
             *
             * Keep the widget completely blank.
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

                showBlank()

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
             * Detect a new song.
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
                 * Don't display "Loading lyrics..."
                 * or any metadata.
                 *
                 * The widget stays blank while
                 * lyrics are being downloaded.
                 */

                showBlank()

                fetchLyrics(
                    context,
                    manager,
                    widgetIds,
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
            manager: AppWidgetManager,
            widgetIds: IntArray,
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
                     * The song changed while the
                     * network request was running.
                     *
                     * Ignore the old result.
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
                     * No synced lyrics were found.
                     *
                     * Stay blank.
                     */

                    if (
                        lyricLines.isEmpty()
                    ) {

                        showBlank()

                        return@post
                    }

                    /*
                     * Immediately determine whether
                     * the current playback position is
                     * inside a lyric.
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
             * First attempt:
             *
             * TITLE + ARTIST
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
             * Fallback:
             *
             * TITLE ONLY
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
                 * We specifically need synced lyrics.
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

                showBlank()

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()

            /*
             * BEFORE THE FIRST LYRIC
             *
             * This is what prevents the first
             * lyric from appearing during a
             * 10-second instrumental intro.
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

                    showBlank()
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

                showBlank()

                return
            }

            /*
             * Check how long until the next lyric.
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
                    nextTime -
                    position

                /*
                 * A gap longer than 2.5 seconds
                 * is treated as an instrumental/
                 * silent section.
                 */

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

                        showBlank()
                    }

                    return
                }
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

            fadeToLine(
                currentLine.text
            )
        }
                private fun showBlank() {

            val context =
                cachedContext
                    ?: return

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

                return
            }

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_text,
                ""
            )

            views.setFloat(
                R.id.lyrics_text,
                "setAlpha",
                0f
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


        private fun fadeToLine(
            text: String
        ) {

            val context =
                cachedContext
                    ?: return

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

                return
            }

            /*
             * First fade the existing lyric out.
             */

            fadeAlpha(
                context,
                manager,
                widgetIds,
                1f,
                0f,
                180L
            ) {

                /*
                 * Put the new lyric into the
                 * invisible widget.
                 */

                val views =
                    RemoteViews(
                        context.packageName,
                        R.layout.widget_lyrics
                    )

                views.setTextViewText(
                    R.id.lyrics_text,
                    text
                )

                views.setFloat(
                    R.id.lyrics_text,
                    "setAlpha",
                    0f
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

                /*
                 * Fade the new lyric in.
                 */

                fadeAlpha(
                    context,
                    manager,
                    widgetIds,
                    0f,
                    1f,
                    220L
                )
            }
        }


        private fun fadeAlpha(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
            from: Float,
            to: Float,
            duration: Long,
            onFinished:
                (() -> Unit)? = null
        ) {

            val steps =
                8

            val stepDelay =
                duration /
                steps

            for (
                step in
                0..steps
            ) {

                handler.postDelayed({

                    val progress =
                        step.toFloat() /
                        steps.toFloat()

                    val alpha =
                        from +
                        (
                            (to - from) *
                            progress
                        )

                    val views =
                        RemoteViews(
                            context.packageName,
                            R.layout.widget_lyrics
                        )

                    /*
                     * If we are fading out, preserve
                     * the currently displayed lyric.
                     */

                    val currentText =
                        if (
                            lastDisplayedIndex >= 0 &&
                            lastDisplayedIndex <
                            lyricLines.size
                        ) {

                            lyricLines[
                                lastDisplayedIndex
                            ].text

                        } else {

                            ""
                        }

                    views.setTextViewText(
                        R.id.lyrics_text,
                        currentText
                    )

                    views.setFloat(
                        R.id.lyrics_text,
                        "setAlpha",
                        alpha
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

                    if (
                        step == steps
                    ) {

                        onFinished?.invoke()
                    }

                }, stepDelay * step)
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


        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            widgetIds: IntArray,
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
                        R.id.lyrics_text,
                        text
                    )

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
                        context,
                        views,
                        intArrayOf(widgetId)
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

        lastDisplayedIndex =
            -1

        cachedContext =
            null
    }
    }
    
