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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LyricsWidgetProvider : AppWidgetProvider() {

    companion object {

        /*
         * LyricsRepository already has its own memory and
         * disk cache, so the widget provider does NOT keep
         * another copy of the lyrics.
         */
        private val executor =
            Executors.newCachedThreadPool()

        private val handler =
            Handler(Looper.getMainLooper())

        private var appContext: Context? =
            null

        /*
         * The song currently represented by the widget.
         */
        private var currentSongKey =
            ""

        /*
         * Lyrics currently being displayed.
         */
        private var document =
            LyricsDocument(
                emptyList()
            )

        /*
         * Prevent duplicate requests for the exact
         * same song while still allowing a NEW song
         * to start fetching immediately.
         */
        private val activeRequests =
            ConcurrentHashMap.newKeySet<String>()

        /*
         * Index of the lyric currently displayed.
         *
         * -1 = before first lyric
         * -2 = blank / instrumental / no lyric
         * Int.MIN_VALUE = nothing displayed yet
         * >= 0 = lyric index
         */
        private var lastDisplayedIndex =
            Int.MIN_VALUE

        /*
         * Check playback position every 100 ms.
         *
         * The widget itself is NOT redrawn every 100 ms.
         * It is only redrawn when the lyric changes.
         */
        private const val UPDATE_INTERVAL =
            100L

        /*
         * If there is a gap this large before the next
         * lyric, treat the section as instrumental/silent.
         */
        private const val SILENCE_GAP_MS =
            2500L

        /*
         * Prevent old asynchronous requests from updating
         * a newer song.
         */
        private var requestGeneration =
            0L

        private val positionUpdater =
            object : Runnable {

                override fun run() {

                    try {

                        updateCurrentLyric()

                    } catch (
                        _: Exception
                    ) {
                        /*
                         * Never allow a widget exception to
                         * kill the position updater.
                         */
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

            /*
             * No widgets exist anymore.
             */
            if (
                ids.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

            /*
             * MediaRepository owns the actual media
             * playback state.
             */
            MediaRepository.start(
                context
            )

            val media =
                MediaRepository
                    .media
                    .value

            val title =
                media.title.trim()

            val artist =
                media.artist.trim()

            /*
             * Nothing is playing.
             *
             * Completely blank the widget.
             */
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

                requestGeneration++

                currentSongKey =
                    ""

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
                createSongKey(
                    cleanedTitle,
                    artist
                )

            /*
             * The song changed.
             */
            if (
                songKey !=
                currentSongKey
            ) {

                currentSongKey =
                    songKey

                document =
                    LyricsDocument(
                        emptyList()
                    )

                lastDisplayedIndex =
                    Int.MIN_VALUE

                requestGeneration++

                val generation =
                    requestGeneration

                /*
                 * Blank immediately.
                 *
                 * This prevents lyrics from the previous
                 * song remaining on screen.
                 */
                updateWidgetText(
                    context,
                    ""
                )

                /*
                 * Start the request immediately.
                 *
                 * This runs independently from the playback
                 * position updater.
                 */
                fetchLyrics(
                    context = context,
                    title = cleanedTitle,
                    artist = artist,
                    songKey = songKey,
                    generation = generation
                )
            }

            /*
             * Always keep the position updater alive while
             * something is playing.
             *
             * This is important: lyrics may still be loading.
             */
            startPositionUpdates(
                context
            )
        }


        private fun createSongKey(
            title: String,
            artist: String
        ): String {

            return (
                title.trim() +
                    "|" +
                    artist.trim()
            )
                .lowercase()
        }


        private fun fetchLyrics(
            context: Context,
            title: String,
            artist: String,
            songKey: String,
            generation: Long
        ) {

            /*
             * Do not start another request for this exact
             * song if one is already running.
             */
            if (
                !activeRequests.add(
                    songKey
                )
            ) {

                return
            }

            executor.execute {

                val result =
                    try {

                        LyricsRepository.getLyrics(
                            context.applicationContext,
                            title,
                            artist
                        )

                    } catch (
                        _: Exception
                    ) {

                        LyricsDocument(
                            lines = emptyList(),
                            source = "none",
                            confidence = 0f
                        )
                    }

                /*
                 * This song's request is finished.
                 */
                activeRequests.remove(
                    songKey
                )

                handler.post {

                    /*
                     * Ignore this result if another song
                     * has started since the request began.
                     */
                    if (
                        generation !=
                        requestGeneration
                    ) {

                        return@post
                    }

                    if (
                        songKey !=
                        currentSongKey
                    ) {

                        return@post
                    }

                    /*
                     * Verify the media state one more time.
                     */
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
                        createSongKey(
                            currentTitle,
                            currentArtist
                        )

                    if (
                        currentKey !=
                        songKey
                    ) {

                        return@post
                    }

                    /*
                     * Lyrics have arrived.
                     */
                    document =
                        result

                    lastDisplayedIndex =
                        Int.MIN_VALUE

                    /*
                     * IMPORTANT:
                     *
                     * Calculate the lyric for the CURRENT
                     * playback position immediately.
                     *
                     * If the network took 2 seconds, we do
                     * NOT start from the first lyric.
                     */
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

            /*
             * Prevent duplicate updater loops.
             */
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

            /*
             * Lyrics are not available yet.
             *
             * Do nothing except keep the widget blank.
             */
            if (
                lines.isEmpty()
            ) {

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
                    .coerceAtLeast(
                        0L
                    )

            /*
             * Before the first lyric.
             *
             * This is what keeps a 10-second instrumental
             * intro blank instead of displaying the first
             * lyric early.
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

            /*
             * Find the latest lyric whose timestamp is
             * less than or equal to the current position.
             */
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
             * Blank lyric entries are never displayed.
             */
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
                        /*
             * Respect the calculated end time of the lyric.
             */
            if (
                position >=
                current.endMs
            ) {

                val nextIndex =
                    currentIndex + 1

                /*
                 * There is no next lyric.
                 *
                 * The current lyric has finished.
                 */
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

                /*
                 * Time remaining until the next lyric.
                 */
                val gap =
                    next.timeMs -
                        position

                /*
                 * Large gap = instrumental/silent section.
                 */
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

                /*
                 * We are between two lyric timestamps.
                 *
                 * Keep the widget blank rather than showing
                 * an old lyric.
                 */
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

            /*
             * The current lyric hasn't changed.
             *
             * DO NOT redraw the RemoteViews.
             *
             * This prevents flashing and unnecessary work.
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

            if (
                ids.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_lyrics
                )

            /*
             * The lyrics widget contains ONLY the lyric.
             *
             * No title.
             * No artist.
             * No status.
             */
            views.setTextViewText(
                R.id.lyrics_text,
                text
            )

            /*
             * A blank state is genuinely invisible.
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

            /*
             * Tapping the lyric widget opens the app.
             */
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

            /*
             * Update every instance of the lyrics widget.
             */
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

            /*
             * Remove common YouTube/Spotify metadata
             * from the title before searching.
             */
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
                        oldValue = item,
                        newValue = "",
                        ignoreCase = true
                    )
            }

            /*
             * Remove suffixes such as:
             *
             * Song - Official Video
             * Song - Official Audio
             * Song - Lyrics
             * Song | Lyric Video
             */
            result =
                result.replace(
                    Regex(
                        """\s*[-|]\s*(official\s+music\s+video|official\s+video|official\s+audio|official\s+lyrics?|lyric\s+video|lyrics?|audio|visualizer).*$""",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )

            /*
             * Collapse multiple spaces.
             */
            result =
                result.replace(
                    Regex(
                        """\s{2,}"""
                    ),
                    " "
                )

            return result.trim()
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

        /*
         * Check whether any lyrics widgets remain.
         */
        val manager =
            AppWidgetManager.getInstance(
                context
            )

        val component =
            ComponentName(
                context,
                LyricsWidgetProvider::class.java
            )

        val remaining =
            manager.getAppWidgetIds(
                component
            )

        if (
            remaining.isEmpty()
        ) {

            stopPositionUpdates()
        }
    }
        override fun onDisabled(
        context: Context
    ) {

        stopPositionUpdates()

        /*
         * Invalidate every outstanding request.
         */
        requestGeneration++

        currentSongKey =
            ""

        document =
            LyricsDocument(
                emptyList()
            )

        lastDisplayedIndex =
            Int.MIN_VALUE

        activeRequests.clear()

        appContext =
            null
    }
}
