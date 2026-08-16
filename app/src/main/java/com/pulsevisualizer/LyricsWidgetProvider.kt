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
         * A cached thread pool means a new song is not forced
         * to wait for a previous lyric request to finish.
         */
        private val executor =
            Executors.newCachedThreadPool()

        private val handler =
            Handler(Looper.getMainLooper())

        private var appContext: Context? = null

        private var currentSongKey =
            ""

        private var document =
            LyricsDocument(
                emptyList()
            )

        /*
         * Song -> lyrics cache.
         *
         * This is the biggest speed improvement when:
         * - changing songs
         * - replaying a song
         * - switching back to a previous song
         */
        private val lyricsCache =
            ConcurrentHashMap<
                String,
                LyricsDocument
            >()

        /*
         * Prevent multiple simultaneous requests
         * for the exact same song.
         */
        private val activeRequests =
            ConcurrentHashMap.newKeySet<String>()

        private var lastDisplayedIndex =
            Int.MIN_VALUE

        /*
         * The position is checked frequently so the lyric
         * changes almost immediately when its timestamp arrives.
         */
        private const val UPDATE_INTERVAL =
            100L

        /*
         * If there is a large gap before the next lyric,
         * show nothing.
         */
        private const val SILENCE_GAP_MS =
            2500L

        /*
         * Don't allow the in-memory cache to grow forever.
         */
        private const val MAX_CACHE_SIZE =
            50

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

            if (
                ids.isEmpty()
            ) {

                stopPositionUpdates()

                return
            }

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
             * Keep the widget completely blank.
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
             * New song.
             */

            if (
                songKey !=
                currentSongKey
            ) {

                currentSongKey =
                    songKey

                lastDisplayedIndex =
                    Int.MIN_VALUE

                /*
                 * FIRST check the memory cache.
                 *
                 * This happens before any network request.
                 */
                val cached =
                    lyricsCache[
                        songKey
                    ]

                if (
                    cached != null
                ) {

                    document =
                        cached

                    /*
                     * Calculate the correct lyric
                     * immediately.
                     */
                    updateCurrentLyric(
                        force = true
                    )

                } else {

                    /*
                     * Blank while lyrics are being
                     * retrieved. We deliberately do
                     * not show "Loading lyrics".
                     */
                    document =
                        LyricsDocument(
                            emptyList()
                        )

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
            }

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
            songKey: String
        ) {

            /*
             * If another request for THIS exact song is
             * already running, don't start another one.
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

                    } catch (_: Exception) {

                        LyricsDocument(
                            emptyList(),
                            source = "none",
                            confidence = 0f
                        )
                    }

                /*
                 * Always remove the request marker,
                 * even if the request failed.
                 */
                activeRequests.remove(
                    songKey
                )

                /*
                 * Only cache actual lyric results.
                 *
                 * Empty results are intentionally NOT cached,
                 * because lyrics might become available later
                 * or a temporary network failure might have
                 * occurred.
                 */
                if (
                    result.lines.isNotEmpty()
                ) {

                    addToCache(
                        songKey,
                        result
                    )
                }

                handler.post {

                    /*
                     * The user may have changed songs while
                     * this request was running.
                     *
                     * Never put an old song's lyrics onto
                     * the new song.
                     */
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

                    document =
                        result

                    lastDisplayedIndex =
                        Int.MIN_VALUE

                    /*
                     * Immediately work out which lyric
                     * should currently be visible.
                     *
                     * This is important if the network
                     * request took 2 seconds and the song
                     * has already progressed.
                     */
                    updateCurrentLyric(
                        force = true
                    )
                }
            }
        }


        private fun addToCache(
            key: String,
            value: LyricsDocument
        ) {

            /*
             * Simple bounded cache.
             *
             * If it gets too large, remove an arbitrary
             * older entry. The cache is only an optimisation,
             * so exact eviction order isn't important.
             */
            if (
                lyricsCache.size >=
                MAX_CACHE_SIZE
            ) {

                val firstKey =
                    lyricsCache
                        .keys
                        .firstOrNull()

                if (
                    firstKey != null
                ) {

                    lyricsCache.remove(
                        firstKey
                    )
                }
            }

            lyricsCache[
                key
            ] = value
        }


        private fun startPositionUpdates(
            context: Context
        ) {

            appContext =
                context.applicationContext

            /*
             * Never allow multiple copies of the
             * position updater to run simultaneously.
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
             * No lyrics loaded yet.
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
             * This keeps an instrumental intro blank.
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
             * Find the most recent lyric timestamp.
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
             * Don't leave a lyric stuck on screen after
             * its calculated end time.
             */
            if (
                position >=
                current.endMs
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

                /*
                 * Instrumental / silent section.
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
                 * We're between two lyrics.
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
             * Blank lyric lines are never displayed.
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
             * Most important anti-flashing optimisation:
             *
             * The widget is NOT redrawn every 100 ms.
             *
             * It is only redrawn when the actual lyric
             * changes.
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
             * The widget contains ONLY the lyric.
             */
            views.setTextViewText(
                R.id.lyrics_text,
                text
            )

            /*
             * Blank means invisible.
             *
             * This prevents "Nothing playing",
             * "Loading lyrics", etc. from remaining
             * on the widget.
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
             * Update every lyrics-widget instance.
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

                /*
                 * Explicit named arguments prevent Kotlin
                 * from selecting the wrong replace overload.
                 */
                result =
                    result.replace(
                        oldValue = item,
                        newValue = "",
                        ignoreCase = true
                    )
            }

            /*
             * Remove common suffixes such as:
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
             * Collapse repeated spaces.
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
         * Android normally supplies the IDs that were
         * deleted. If there are no remaining IDs,
         * stop the updater.
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

        currentSongKey =
            ""

        document =
            LyricsDocument(
                emptyList()
            )

        lastDisplayedIndex =
            Int.MIN_VALUE

        activeRequests.clear()

        /*
         * Keep the cache while the provider is alive,
         * but clear it when Android actually disables
         * the widget provider.
         */
        lyricsCache.clear()

        appContext =
            null
    }
}
