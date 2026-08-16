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
import java.util.concurrent.Executors

class LyricsWidgetProvider : AppWidgetProvider() {

    companion object {

        private val executor =
            Executors.newSingleThreadExecutor()

        private val handler =
            Handler(Looper.getMainLooper())

        private var appContext: Context? = null

        private var currentSongKey = ""

        private var document =
            LyricsDocument(
                emptyList()
            )

        private var loading = false

        private var lastDisplayedIndex =
            Int.MIN_VALUE

        private const val UPDATE_INTERVAL = 100L

        private const val SILENCE_GAP_MS = 2500L

        private val positionUpdater =
            object : Runnable {

                override fun run() {

                    try {
                        updateCurrentLyric()
                    } catch (_: Exception) {
                    }

                    handler.postDelayed(
                        this,
                        UPDATE_INTERVAL
                    )
                }
            }

        fun updateAll(
            context: Context
        ) {

            appContext =
                context.applicationContext

            val manager =
                AppWidgetManager.getInstance(
                    context
                )

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (ids.isEmpty()) {
                stopPositionUpdates()
                return
            }

            MediaRepository.start(
                context
            )

            val media =
                MediaRepository.media.value

            val title =
                media.title.trim()

            val artist =
                media.artist.trim()

            if (
                title.isBlank() ||
                title.equals(
                    "Nothing playing",
                    ignoreCase = true
                ) ||
                title.equals(
                    "Unknown title",
                    ignoreCase = true
                )
            ) {

                currentSongKey = ""

                document =
                    LyricsDocument(
                        emptyList()
                    )

                lastDisplayedIndex =
                    Int.MIN_VALUE

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
                ).lowercase()

            if (
                songKey != currentSongKey
            ) {

                currentSongKey =
                    songKey

                document =
                    LyricsDocument(
                        emptyList()
                    )

                lastDisplayedIndex =
                    Int.MIN_VALUE

                updateWidgetText(
                    context,
                    ""
                )

                fetchLyrics(
                    context,
                    cleanedTitle,
                    artist,
                    songKey
                )
            }

            startPositionUpdates(
                context
            )
        }

        private fun fetchLyrics(
            context: Context,
            title: String,
            artist: String,
            songKey: String
        ) {

            if (loading) {
                return
            }

            loading = true

            executor.execute {

                val result =
                    try {
                        LyricsRepository.getLyrics(
                            context.applicationContext,
                            title,
                            artist
                        )
                    } catch (_: Exception) {
                        LyricsDocument(
                            emptyList(),
                            source = "none",
                            confidence = 0f
                        )
                    }

                handler.post {

                    loading = false

                    val media =
                        MediaRepository
                            .media
                            .value

                    val currentTitle =
                        cleanTitle(
                            media.title
                        )

                    val currentArtist =
                        media.artist.trim()

                    val currentKey =
                        (
                            currentTitle +
                                "|" +
                                currentArtist
                        ).lowercase()

                    if (
                        currentKey !=
                        songKey ||
                        currentKey !=
                        currentSongKey
                    ) {
                        return@post
                    }

                    document =
                        result

                    lastDisplayedIndex =
                        Int.MIN_VALUE

                    updateCurrentLyric(
                        force = true
                    )
                }
            }
        }

        private fun startPositionUpdates(
            context: Context
        ) {

            appContext =
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

        private fun updateCurrentLyric(
            force: Boolean = false
        ) {

            val context =
                appContext
                    ?: return

            val lines =
                document.lines

            if (lines.isEmpty()) {

                if (
                    force ||
                    lastDisplayedIndex !=
                    -2
                ) {

                    lastDisplayedIndex =
                        -2

                    updateWidgetText(
                        context,
                        ""
                    )
                }

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()
                    .coerceAtLeast(0L)

            /*
             * Blank before the first lyric.
             * This handles instrumental intros.
             */
            if (
                position <
                lines.first().timeMs
            ) {

                if (
                    force ||
                    lastDisplayedIndex !=
                    -1
                ) {

                    lastDisplayedIndex =
                        -1

                    updateWidgetText(
                        context,
                        ""
                    )
                }

                return
            }

            var currentIndex =
                -1

            for (
                index in lines.indices
            ) {

                if (
                    lines[index].timeMs <=
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

            val current =
                lines[currentIndex]

            /*
             * Do not leave a lyric displayed forever
             * after its actual end time.
             */
            if (
                position >= current.endMs
            ) {

                val nextIndex =
                    currentIndex + 1

                if (
                    nextIndex >=
                    lines.size
                ) {

                    if (
                        lastDisplayedIndex !=
                        -2
                    ) {

                        lastDisplayedIndex =
                            -2

                        updateWidgetText(
                            context,
                            ""
                        )
                    }

                    return
                }

                val next =
                    lines[nextIndex]

                val gap =
                    next.timeMs -
                        position

                if (
                    gap >
                    SILENCE_GAP_MS
                ) {

                    if (
                        lastDisplayedIndex !=
                        -2
                    ) {

                        lastDisplayedIndex =
                            -2

                        updateWidgetText(
                            context,
                            ""
                        )
                    }

                    return
                }

                if (
                    position <
                    next.timeMs
                ) {

                    if (
                        lastDisplayedIndex !=
                        -2
                    ) {

                        lastDisplayedIndex =
                            -2

                        updateWidgetText(
                            context,
                            ""
                        )
                    }

                    return
                }
            }
                        if (
                current.text.isBlank()
            ) {

                if (
                    lastDisplayedIndex !=
                    -2
                ) {

                    lastDisplayedIndex =
                        -2

                    updateWidgetText(
                        context,
                        ""
                    )
                }

                return
            }

            if (
                !force &&
                currentIndex ==
                lastDisplayedIndex
            ) {

                return
            }

            lastDisplayedIndex =
                currentIndex

            updateWidgetText(
                context,
                current.text
            )
        }

        private fun updateWidgetText(
            context: Context,
            text: String
        ) {

            val manager =
                AppWidgetManager.getInstance(
                    context
                )

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (ids.isEmpty()) {
                stopPositionUpdates()
                return
            }

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
                    9002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.lyrics_root,
                pendingIntent
            )

            for (
                id in ids
            ) {

                manager.updateAppWidget(
                    id,
                    views
                )
            }
        }

        private fun cleanTitle(
            value: String
        ): String {

            var result =
                value.trim()

            val removable =
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
                    "[Audio]",
                    "(Visualizer)",
                    "[Visualizer]",
                    "(4K)",
                    "[4K]",
                    "(HD)",
                    "[HD]",
                    "(Remastered)",
                    "[Remastered]"
                )

            for (
                item in removable
            ) {

                result =
                    result.replace(
                        item,
                        "",
                        ignoreCase = true
                    )
            }

            return result
                .replace(
                    Regex(
                        """\s*[-|]\s*(official|lyrics?|lyric video|audio|visualizer).*$"""
                    ),
                    "",
                    ignoreCase = true
                )
                .replace(
                    Regex(
                        "\\s{2,}"
                    ),
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

        updateAll(
            context
        )
    }

    override fun onEnabled(
        context: Context
    ) {

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

        currentSongKey =
            ""

        document =
            LyricsDocument(
                emptyList()
            )

        lastDisplayedIndex =
            Int.MIN_VALUE

        loading =
            false

        appContext =
            null
    }
}
