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

        private val executor =
            Executors.newCachedThreadPool()

        private val handler =
            Handler(Looper.getMainLooper())

        private var appContext:
            Context? = null

        private var currentSongKey =
            ""

        private var document =
            LyricsDocument(
                emptyList()
            )

        /*
         * Instead of one global loading flag, keep track
         * of which specific songs are currently loading.
         *
         * This means:
         *
         * Song A loading
         *      ↓
         * Song B starts
         *      ↓
         * Song B can load immediately
         */
        private val activeRequests =
            ConcurrentHashMap.newKeySet<String>()

        /*
         * Protects asynchronous requests from an old
         * song updating a newer song.
         */
        private var requestGeneration =
            0L

        private var lastDisplayedIndex =
            Int.MIN_VALUE

        private const val UPDATE_INTERVAL =
            100L

        private const val SILENCE_GAP_MS =
            2500L

        private val positionUpdater =
            object : Runnable {

                override fun run() {

                    try {

                        updateCurrentLyric()

                    } catch (
                        _: Exception
                    ) {
                        /*
                         * Never allow a widget update error
                         * to kill the updater.
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

            if (
                ids.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

            /*
             * IMPORTANT:
             *
             * Do NOT call MediaRepository.start()
             * from here.
             *
             * MediaRepository.updateWidget() calls this
             * method, and MediaRepository.start() calls
             * refresh(), which can call updateWidget()
             * again. That creates a recursive loop.
             *
             * The media repository is already started by
             * the application/media service.
             */

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
             * Completely blank the lyric widget.
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
             * Detect a new song.
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
                 * Immediately clear the previous song's
                 * lyrics.
                 */
                updateWidgetText(
                    context,
                    ""
                )

                fetchLyrics(
                    context = context,
                    title = cleanedTitle,
                    artist = artist,
                    songKey = songKey,
                    generation = generation
                )
            }

            /*
             * Keep checking playback position while
             * something is playing.
             *
             * This updater does NOT fetch lyrics and does
             * NOT redraw the widget unless necessary.
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
             * Already fetching this exact song.
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
                            emptyList(),
                            source = "none",
                            confidence = 0f
                        )
                    }

                /*
                 * This request is finished.
                 */
                activeRequests.remove(
                    songKey
                )

                handler.post {

                    /*
                     * Ignore a result from an old song.
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
                     * Lyrics successfully arrived.
                     */
                    document =
                        result

                    lastDisplayedIndex =
                        Int.MIN_VALUE

                    /*
                     * Immediately calculate which lyric
                     * should be showing RIGHT NOW.
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
             * Only one updater can exist.
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
             * Lyrics are still loading or unavailable.
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
             * Instrumental intro:
             *
             * Before the first timestamp, show nothing.
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

            /*
             * Find the latest lyric timestamp that has
             * already been reached.
             */
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
             * Empty lyric line.
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
             * If the lyric has an explicit end time,
             * don't leave it stuck on screen.
             */
            if (
                position >=
                current.endMs
            ) {

                val nextIndex =
                    currentIndex + 1

                /*
                 * Last lyric has finished.
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
                 * We are between lyric lines.
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
             * The lyric has not changed.
             *
             * Do NOT redraw the widget every 100 ms.
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
             * The lyric widget displays ONLY lyrics.
             */
            views.setTextViewText(
                R.id.lyrics_text,
                text
            )

            /*
             * Completely invisible when blank.
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
             * Tap widget to open the application.
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
             * Update every lyrics widget instance.
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
             * IMPORTANT:
             *
             * Case-insensitivity belongs on the Regex,
             * not as an argument to String.replace().
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
             * Collapse repeated whitespace.
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

        /*
         * Do not start MediaRepository here.
         *
         * The app/media service owns MediaRepository.
         *
         * Starting it from here can create a recursive
         * MediaRepository -> LyricsWidgetProvider loop.
         */
        updateAll(
            context
        )
    }


    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {

        /*
         * Android normally supplies the deleted widget IDs.
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
         * Invalidate all outstanding asynchronous requests.
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
