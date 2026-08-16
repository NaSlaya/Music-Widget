package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.concurrent.Executors

class LyricsWidgetProvider :
    AppWidgetProvider() {

    companion object {

        private val executor =
            Executors.newSingleThreadExecutor()

        private var context:
            Context? = null

        private var songKey =
            ""

        private var document:
            LyricsDocument? = null

        private var lastDisplayed =
            ""

        private const val UPDATE_MS =
            100L

        private const val SILENCE_GAP_MS =
            2500L

        fun updateAll(
            context: Context
        ) {

            val appContext =
                context.applicationContext

            this.context =
                appContext

            MediaRepository.start(
                appContext
            )

            val manager =
                AppWidgetManager.getInstance(
                    appContext
                )

            val component =
                ComponentName(
                    appContext,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (
                ids.isEmpty()
            ) {
                return
            }

            val media =
                MediaRepository.media.value

            val title =
                cleanTitle(
                    media.title
                )

            val artist =
                media.artist.trim()

            if (
                title.isBlank() ||
                title.equals(
                    "Nothing playing",
                    ignoreCase = true
                )
            ) {

                songKey =
                    ""

                document =
                    null

                lastDisplayed =
                    ""

                updateText(
                    appContext,
                    ""
                )

                return
            }

            val newKey =
                (
                    title +
                    "|" +
                    artist
                ).lowercase()

            if (
                newKey != songKey
            ) {

                songKey =
                    newKey

                document =
                    null

                lastDisplayed =
                    ""

                updateText(
                    appContext,
                    ""
                )

                loadLyrics(
                    appContext,
                    title,
                    artist,
                    newKey
                )
            }

            updateCurrentLine(
                appContext
            )
        }

        private fun loadLyrics(
            context: Context,
            title: String,
            artist: String,
            expectedKey: String
        ) {

            executor.execute {

                val result =
                    LyricsRepository.getLyrics(
                        context,
                        title,
                        artist
                    )

                val current =
                    MediaRepository.media.value

                val currentTitle =
                    cleanTitle(
                        current.title
                    )

                val currentKey =
                    (
                        currentTitle +
                        "|" +
                        current.artist.trim()
                    ).lowercase()

                if (
                    currentKey !=
                    expectedKey
                ) {
                    return@execute
                }

                document =
                    result

                updateCurrentLine(
                    context
                )
            }
        }

        private fun updateCurrentLine(
            context: Context
        ) {

            val currentDocument =
                document
                    ?: return

            val lines =
                currentDocument.lines

            if (
                lines.isEmpty()
            ) {

                updateText(
                    context,
                    ""
                )

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()

            val first =
                lines.first()

            if (
                position <
                first.timeMs
            ) {

                if (
                    lastDisplayed != ""
                ) {

                    lastDisplayed =
                        ""

                    updateText(
                        context,
                        ""
                    )
                }

                return
            }

            var index =
                -1

            for (
                i in lines.indices
            ) {

                if (
                    lines[i].timeMs <=
                    position
                ) {

                    index =
                        i

                } else {

                    break
                }
            }

            if (
                index < 0
            ) {
                return
            }

            val line =
                lines[index]

            if (
                position >=
                line.endMs
            ) {

                if (
                    index <
                    lines.lastIndex
                ) {

                    val next =
                        lines[index + 1]

                    if (
                        next.timeMs -
                        position >
                        SILENCE_GAP_MS
                    ) {

                        if (
                            lastDisplayed != ""
                        ) {

                            lastDisplayed =
                                ""

                            updateText(
                                context,
                                ""
                            )
                        }

                        return
                    }
                }
            }

            if (
                line.text ==
                lastDisplayed
            ) {
                return
            }

            lastDisplayed =
                line.text

            updateText(
                context,
                line.text
            )
        }

        private fun updateText(
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
                )

            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    8001,
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
            title: String
        ): String {

            var result =
                title.trim()

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
                    "(Audio)",
                    "[Audio]",
                    "(Visualizer)",
                    "[Visualizer]"
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
                        "\\s{2,}"
                    ),
                    " "
                )
                .trim()
        }
    }

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
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

    override fun onDisabled(
        context: Context
    ) {

        document =
            null

        songKey =
            ""

        lastDisplayed =
            ""
    }
}
