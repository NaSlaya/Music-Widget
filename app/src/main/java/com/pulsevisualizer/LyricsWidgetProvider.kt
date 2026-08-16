package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.RemoteViews
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class LyricsWidgetProvider :
    AppWidgetProvider() {

    companion object {

        private const val POSITION_UPDATE_MS = 100L

        /*
         * If there is a long gap between lyrics,
         * treat it as an instrumental section.
         */
        private const val SILENCE_GAP_MS = 2500L

        private const val MIN_FONT_SIZE = 34f
        private const val MAX_FONT_SIZE = 58f
        private const val DEFAULT_FONT_SIZE = 52f

        /*
         * Karaoke colours.
         */
        private const val CURRENT_COLOR =
            0xFFFFFFFF.toInt()

        private const val PAST_COLOR =
            0xFFB9B9C7.toInt()

        private const val FUTURE_COLOR =
            0xFF666674.toInt()

        private var appContext: Context? = null

        private var document: LyricsDocument? = null

        private var songKey = ""

        private var loading = false

        /*
         * -1 = nothing selected
         * -2 = instrumental gap
         */
        private var currentLineIndex = -1

        private var currentWordIndex = -1

        private var lastRenderedState = ""

        private val executor =
            Executors.newSingleThreadExecutor()

        private val handler =
            Handler(
                Looper.getMainLooper()
            )

        private val updater =
            object : Runnable {

                override fun run() {

                    updatePlayback()

                    handler.postDelayed(
                        this,
                        POSITION_UPDATE_MS
                    )
                }
            }


        fun updateAll(
            suppliedContext: Context
        ) {

            val app =
                suppliedContext.applicationContext

            appContext = app

            val manager =
                AppWidgetManager
                    .getInstance(app)

            val component =
                ComponentName(
                    app,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (ids.isEmpty()) {

                stopUpdater()

                return
            }

            val media =
                MediaRepository.media.value

            val title =
                media.title.trim()

            val artist =
                media.artist.trim()

            /*
             * Treat placeholders as no track.
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

                clearWidget()

                document = null
                songKey = ""

                currentLineIndex = -1
                currentWordIndex = -1

                stopUpdater()

                return
            }

            val cleanTitle =
                cleanTitle(title)

            val cleanArtist =
                artist.trim()

            val newKey =
                (
                    cleanTitle +
                        "|" +
                        cleanArtist
                ).lowercase()

            /*
             * New song.
             */
            if (newKey != songKey) {

                songKey = newKey

                document = null

                currentLineIndex = -1
                currentWordIndex = -1

                lastRenderedState = ""

                clearWidget()

                fetchLyrics(
                    app,
                    cleanTitle,
                    cleanArtist,
                    newKey
                )
            }

            startUpdater()

            updatePlayback()
        }


        private fun fetchLyrics(
            app: Context,
            title: String,
            artist: String,
            expectedKey: String
        ) {

            if (loading) {
                return
            }

            loading = true

            executor.execute {

                val result =
                    LyricsRepository.getLyrics(
                        app,
                        title,
                        artist
                    )

                handler.post {

                    loading = false

                    /*
                     * Ignore an old request if the
                     * user changed songs while it
                     * was downloading.
                     */
                    if (
                        expectedKey != songKey
                    ) {
                        return@post
                    }

                    document = result

                    currentLineIndex = -1
                    currentWordIndex = -1

                    lastRenderedState = ""

                    updatePlayback()
                }
            }
        }


        private fun updatePlayback() {

            val doc =
                document
                    ?: return

            if (doc.lines.isEmpty()) {

                clearWidget()

                return
            }

            val position =
                MediaRepository
                    .getCurrentPositionMs()

            val index =
                findLine(
                    doc.lines,
                    position
                )

            /*
             * Intro / before first lyric.
             */
            if (index < 0) {

                if (
                    currentLineIndex != -1 ||
                    lastRenderedState.isNotEmpty()
                ) {

                    currentLineIndex = -1
                    currentWordIndex = -1

                    clearWidget()
                }

                return
            }

            val line =
                doc.lines[index]

            val next =
                doc.lines
                    .getOrNull(index + 1)

            /*
             * Blank during long instrumental
             * sections.
             */
            if (
                next != null &&
                next.timeMs - position >
                SILENCE_GAP_MS
            ) {

                if (currentLineIndex != -2) {

                    currentLineIndex = -2
                    currentWordIndex = -1

                    clearWidget()
                }

                return
            }

            val wordIndex =
                LyricsTiming.findCurrentWord(
                    line.words,
                    position
                )

            /*
             * Critical optimisation:
             *
             * Do NOT redraw every 100ms.
             *
             * Only redraw when the visible
             * lyric state changes.
             */
            if (
                index == currentLineIndex &&
                wordIndex == currentWordIndex
            ) {
                return
            }

            currentLineIndex = index
            currentWordIndex = wordIndex

            renderLine(
                line,
                wordIndex
            )
        }


        private fun findLine(
            lines: List<LyricLine>,
            position: Long
        ): Int {

            if (lines.isEmpty()) {
                return -1
            }

            if (
                position <
                lines.first().timeMs
            ) {
                return -1
            }

            var low = 0
            var high = lines.lastIndex
            var result = -1

            while (low <= high) {

                val middle =
                    (low + high) ushr 1

                if (
                    lines[middle].timeMs <=
                    position
                ) {

                    result = middle
                    low = middle + 1

                } else {

                    high = middle - 1
                }
            }

            return result
        }
                private fun renderLine(
            line: LyricLine,
            wordIndex: Int
        ) {

            val app =
                appContext
                    ?: return

            val manager =
                AppWidgetManager
                    .getInstance(app)

            val component =
                ComponentName(
                    app,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (ids.isEmpty()) {
                return
            }

            val styled =
                buildStyledText(
                    line,
                    wordIndex
                )

            val fontSize =
                calculateFontSize(
                    line.text
                )

            val state =
                line.text +
                    "|" +
                    wordIndex +
                    "|" +
                    fontSize

            if (
                state == lastRenderedState
            ) {
                return
            }

            lastRenderedState = state

            val views =
                RemoteViews(
                    app.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_text,
                styled
            )

            views.setTextViewTextSize(
                R.id.lyrics_text,
                TypedValue.COMPLEX_UNIT_SP,
                fontSize
            )

            setClickAction(
                app,
                views,
                ids
            )

            for (id in ids) {

                manager.updateAppWidget(
                    id,
                    views
                )
            }
        }


        private fun buildStyledText(
            line: LyricLine,
            wordIndex: Int
        ): SpannedString {

            val text =
                line.text

            val builder =
                SpannableString(text)

            /*
             * Everything starts dim.
             */
            builder.setSpan(
                ForegroundColorSpan(
                    FUTURE_COLOR
                ),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            var cursor = 0

            for (
                index in line.words.indices
            ) {

                val word =
                    line.words[index].text

                val start =
                    text.indexOf(
                        word,
                        cursor
                    )

                if (start < 0) {
                    continue
                }

                val end =
                    (
                        start +
                            word.length
                    ).coerceAtMost(
                        text.length
                    )

                val colour =
                    when {

                        index < wordIndex ->
                            PAST_COLOR

                        index == wordIndex ->
                            CURRENT_COLOR

                        else ->
                            FUTURE_COLOR
                    }

                builder.setSpan(
                    ForegroundColorSpan(
                        colour
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                /*
                 * Current word is bold.
                 */
                if (
                    index == wordIndex
                ) {

                    builder.setSpan(
                        StyleSpan(
                            Typeface.BOLD
                        ),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                cursor = end
            }

            /*
             * Before the first estimated word,
             * everything remains dim.
             */
            if (wordIndex < 0) {

                builder.setSpan(
                    ForegroundColorSpan(
                        FUTURE_COLOR
                    ),
                    0,
                    text.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            return SpannedString(builder)
        }


        private fun calculateFontSize(
            text: String
        ): Float {

            val length = text.length

            return when {

                length <= 12 ->
                    MAX_FONT_SIZE

                length <= 20 ->
                    54f

                length <= 30 ->
                    DEFAULT_FONT_SIZE

                length <= 42 ->
                    46f

                length <= 56 ->
                    40f

                length <= 72 ->
                    36f

                else ->
                    MIN_FONT_SIZE
            }
        }


        private fun clearWidget() {

            val app =
                appContext
                    ?: return

            val manager =
                AppWidgetManager
                    .getInstance(app)

            val component =
                ComponentName(
                    app,
                    LyricsWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            if (ids.isEmpty()) {
                return
            }

            val views =
                RemoteViews(
                    app.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_text,
                ""
            )

            setClickAction(
                app,
                views,
                ids
            )

            for (id in ids) {

                manager.updateAppWidget(
                    id,
                    views
                )
            }

            lastRenderedState = ""
        }


        private fun startUpdater() {

            handler.removeCallbacks(
                updater
            )

            handler.post(updater)
        }


        private fun stopUpdater() {

            handler.removeCallbacks(
                updater
            )
        }


        private fun setClickAction(
            app: Context,
            views: RemoteViews,
            ids: IntArray
        ) {

            val intent =
                Intent(
                    app,
                    MainActivity::class.java
                ).apply {

                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

            val requestCode =
                ids.firstOrNull()
                    ?: 9001

            val pending =
                PendingIntent.getActivity(
                    app,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.lyrics_root,
                pending
            )
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

            for (item in removable) {

                result =
                    result.replace(
                        item,
                        "",
                        ignoreCase = true
                    )
            }

            result =
                result.replace(
                    Regex(
                        """\s*[-|]\s*(official|lyrics?|lyric video|audio|visualizer).*$"""
                    ),
                    "",
                    ignoreCase = true
                )

            return result
                .replace(
                    Regex("\\s{2,}"),
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

        updateAll(context)
    }


    override fun onEnabled(
        context: Context
    ) {

        updateAll(context)
    }


    override fun onDeleted(
        context: Context,
        ids: IntArray
    ) {

        /*
         * The updater is shared by all widget
         * instances, so only stop it when no
         * lyric widgets remain.
         */
        val manager =
            AppWidgetManager
                .getInstance(context)

        val component =
            ComponentName(
                context,
                LyricsWidgetProvider::class.java
            )

        val remaining =
            manager.getAppWidgetIds(
                component
            )

        if (remaining.isEmpty()) {
            stopUpdater()
        }
    }


    override fun onDisabled(
        context: Context
    ) {

        stopUpdater()

        document = null
        songKey = ""

        currentLineIndex = -1
        currentWordIndex = -1

        lastRenderedState = ""

        appContext = null
    }
}
