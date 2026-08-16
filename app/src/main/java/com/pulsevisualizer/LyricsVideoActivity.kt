package com.pulsevisualizer

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

class LyricsVideoActivity :
    ComponentActivity() {

    private lateinit var lyricsView:
        LyricsVideoView

    private var lyricsJob:
        Job? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        lyricsView =
            LyricsVideoView(
                this
            )

        setContentView(
            lyricsView
        )

        MediaRepository.start(
            this
        )

        lifecycleScope.launch {

            MediaRepository.media.collect {

                mediaChanged()
            }
        }

        startPositionLoop()

        mediaChanged()
    }

    private fun mediaChanged() {

        val media =
            MediaRepository.media.value

        lyricsView.setSong(
            media.title,
            media.artist,
            media.artwork
        )

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

            lyricsJob?.cancel()

            lyricsView.setLoading(
                false
            )

            lyricsView.setLyrics(
                null
            )

            return
        }

        loadLyrics(
            title,
            artist
        )
    }

    private fun loadLyrics(
        title: String,
        artist: String
    ) {

        lyricsJob?.cancel()

        lyricsView.setLyrics(
            null
        )

        lyricsView.setLoading(
            true
        )

        lyricsJob =
            lifecycleScope.launch {

                val result =
                    kotlinx.coroutines
                        .withContext(
                            kotlinx.coroutines.Dispatchers.IO
                        ) {

                            try {

                                LyricsRepository.getLyrics(
                                    this@LyricsVideoActivity,
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
                        }

                if (
                    isFinishing
                ) {
                    return@launch
                }

                lyricsView.setLyrics(
                    result
                )

                lyricsView.setLoading(
                    false
                )
            }
    }

    private fun startPositionLoop() {

        lifecycleScope.launch {

            while (
                !isFinishing
            ) {

                lyricsView.setPosition(
                    MediaRepository
                        .getCurrentPositionMs()
                )

                delay(40L)
            }
        }
    }

    override fun onDestroy() {

        lyricsJob?.cancel()

        super.onDestroy()
    }
}

private class LyricsVideoView(
    context: android.content.Context
) : View(context) {

    private val paint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.SUBPIXEL_TEXT_FLAG
        )

    private val glowPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    private var title =
        "Nothing playing"

    private var artist =
        ""

    private var artwork:
        Bitmap? = null

    private var lyrics:
        LyricsDocument? = null

    private var position =
        0L

    private var loading =
        false

    private var style =
        0

    private var previousIndex =
        Int.MIN_VALUE

    private var animationStart =
        SystemClock.uptimeMillis()

    private var downX =
        0f

    private var downY =
        0f

    private val styles =
        5

    fun setSong(
        title: String,
        artist: String,
        artwork: Bitmap?
    ) {

        val changed =
            this.title != title ||
                this.artist != artist

        this.title =
            title.ifBlank {
                "Nothing playing"
            }

        this.artist =
            artist

        this.artwork =
            artwork

        if (changed) {

            previousIndex =
                Int.MIN_VALUE

            animationStart =
                SystemClock.uptimeMillis()
        }

        invalidate()
    }

    fun setLyrics(
        document: LyricsDocument?
    ) {

        lyrics =
            document

        previousIndex =
            Int.MIN_VALUE

        animationStart =
            SystemClock.uptimeMillis()

        invalidate()
    }

    fun setLoading(
        value: Boolean
    ) {

        loading =
            value

        invalidate()
    }

    fun setPosition(
        value: Long
    ) {

        position =
            value.coerceAtLeast(
                0L
            )

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(
            canvas
        )

        val width =
            width.toFloat()

        val height =
            height.toFloat()

        drawBackground(
            canvas,
            width,
            height
        )

        drawArtworkGlow(
            canvas,
            width,
            height
        )

        drawHeader(
            canvas,
            width
        )

        if (
            loading
        ) {

            drawLoading(
                canvas,
                width,
                height
            )

            postInvalidateDelayed(
                30L
            )

            return
        }

        val document =
            lyrics

        if (
            document == null ||
            document.lines.isEmpty()
        ) {

            drawNoLyrics(
                canvas,
                width,
                height
            )

            return
        }

        drawLyrics(
            canvas,
            width,
            height,
            document
        )

        postInvalidateDelayed(
            30L
        )
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val colors =
            when (
                style
            ) {

                0 ->
                    intArrayOf(
                        Color.rgb(
                            78,
                            25,
                            130
                        ),
                        Color.rgb(
                            8,
                            5,
                            15
                        ),
                        Color.BLACK
                    )

                1 ->
                    intArrayOf(
                        Color.rgb(
                            8,
                            55,
                            95
                        ),
                        Color.rgb(
                            20,
                            8,
                            55
                        ),
                        Color.BLACK
                    )

                2 ->
                    intArrayOf(
                        Color.rgb(
                            110,
                            30,
                            10
                        ),
                        Color.rgb(
                            45,
                            8,
                            20
                        ),
                        Color.BLACK
                    )

                3 ->
                    intArrayOf(
                        Color.rgb(
                            8,
                            90,
                            65
                        ),
                        Color.rgb(
                            5,
                            25,
                            25
                        ),
                        Color.BLACK
                    )

                else ->
                    intArrayOf(
                        Color.rgb(
                            100,
                            80,
                            10
                        ),
                        Color.rgb(
                            35,
                            25,
                            5
                        ),
                        Color.BLACK
                    )
            }

        paint.shader =
            LinearGradient(
                0f,
                0f,
                width,
                height,
                colors,
                null,
                Shader.TileMode.CLAMP
            )

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            paint
        )

        paint.shader =
            null
    }

    private fun drawArtworkGlow(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val bitmap =
            artwork
                ?: return

        if (
            bitmap.isRecycled
        ) {
            return
        }

        glowPaint.shader =
            BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )

        glowPaint.alpha =
            35

        canvas.drawCircle(
            width * 0.5f,
            height * 0.43f,
            min(
                width,
                height
            ) * 0.55f,
            glowPaint
        )

        glowPaint.shader =
            null
    }
        private fun drawHeader(
        canvas: Canvas,
        width: Float
    ) {

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            Color.WHITE

        paint.alpha =
            220

        paint.textSize =
            13f *
                resources.displayMetrics
                    .scaledDensity

        canvas.drawText(
            "LYRICS VIDEO",
            28f,
            44f,
            paint
        )

        paint.textSize =
            10f *
                resources.displayMetrics
                    .scaledDensity

        paint.alpha =
            130

        canvas.drawText(
            "${style + 1}/$styles",
            width - 48f,
            44f,
            paint
        )

        paint.textSize =
            20f *
                resources.displayMetrics
                    .scaledDensity

        paint.alpha =
            255

        canvas.drawText(
            ellipsize(
                title,
                width - 56f
            ),
            28f,
            84f,
            paint
        )

        paint.textSize =
            13f *
                resources.displayMetrics
                    .scaledDensity

        paint.alpha =
            150

        if (
            artist.isNotBlank()
        ) {

            canvas.drawText(
                artist,
                28f,
                106f,
                paint
            )
        }

        paint.alpha =
            255
    }

    private fun drawLoading(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val density =
            resources.displayMetrics
                .scaledDensity

        paint.color =
            Color.WHITE

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.textSize =
            20f * density

        val pulse =
            (
                SystemClock.uptimeMillis()
                    % 1200L
            ).toFloat() /
                1200f

        paint.alpha =
            (
                120f +
                    100f *
                        (
                            0.5f +
                                0.5f *
                                    kotlin.math
                                        .sin(
                                            pulse *
                                                Math.PI *
                                                2.0
                                        )
                        )
            ).toInt()

        val text =
            "Finding lyrics..."

        canvas.drawText(
            text,
            (
                width -
                    paint.measureText(
                        text
                    )
            ) / 2f,
            height * 0.55f,
            paint
        )

        paint.alpha =
            255
    }

    private fun drawNoLyrics(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        if (
            title.equals(
                "Nothing playing",
                ignoreCase = true
            )
        ) {
            return
        }

        paint.color =
            Color.WHITE

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.textSize =
            19f *
                resources.displayMetrics
                    .scaledDensity

        paint.alpha =
            145

        val text =
            "No lyrics found"

        canvas.drawText(
            text,
            (
                width -
                    paint.measureText(
                        text
                    )
            ) / 2f,
            height * 0.55f,
            paint
        )

        paint.alpha =
            255
    }

    private fun drawLyrics(
        canvas: Canvas,
        width: Float,
        height: Float,
        document: LyricsDocument
    ) {

        val lines =
            document.lines

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

        val current =
            lines[index]

        if (
            position >=
            current.endMs
        ) {

            return
        }

        if (
            index !=
            previousIndex
        ) {

            previousIndex =
                index

            animationStart =
                SystemClock.uptimeMillis()
        }

        val elapsed =
            SystemClock.uptimeMillis() -
                animationStart

        val progress =
            (
                elapsed
                    .coerceAtMost(
                        450L
                    )
                    .toFloat()
                / 450f
            )

        val eased =
            1f -
                (
                    1f -
                        progress
                ) *
                    (
                        1f -
                            progress
                    )

        val centerY =
            height * 0.52f

        val previous =
            lines.getOrNull(
                index - 1
            )

        val next =
            lines.getOrNull(
                index + 1
            )

        if (
            previous != null
        ) {

            drawCenteredText(
                canvas,
                previous.text,
                width,
                centerY - 115f,
                17f,
                65
            )
        }

        drawCurrentLine(
            canvas,
            current,
            width,
            centerY,
            eased
        )

        if (
            next != null
        ) {

            drawCenteredText(
                canvas,
                next.text,
                width,
                centerY + 115f,
                17f,
                65
            )
        }
    }

    private fun drawCurrentLine(
        canvas: Canvas,
        line: LyricLine,
        width: Float,
        centerY: Float,
        animation: Float
    ) {

        val density =
            resources.displayMetrics
                .scaledDensity

        val size =
            34f *
                density *
                (
                    0.96f +
                        0.04f *
                            animation
                )

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.textSize =
            size

        val words =
            line.words

        if (
            words.isEmpty()
        ) {

            drawCenteredWrappedText(
                canvas,
                line.text,
                width,
                centerY,
                size,
                255
            )

            return
        }

        val currentWord =
            findCurrentWord(
                words
            )

        val maxWidth =
            width * 0.90f

        val completeWidth =
            words.sumOf {
                paint.measureText(
                    it.text
                ).toDouble()
            }.toFloat() +
                (
                    words.size - 1
                ) *
                    paint.measureText(
                        " "
                    )

        if (
            completeWidth <=
            maxWidth
        ) {

            var x =
                (
                    width -
                        completeWidth
                ) / 2f

            for (
                i in words.indices
            ) {

                val word =
                    words[i]

                val wordWidth =
                    paint.measureText(
                        word.text
                    )

                drawWord(
                    canvas,
                    word,
                    i,
                    currentWord,
                    x,
                    centerY,
                    size
                )

                x +=
                    wordWidth +
                        paint.measureText(
                            " "
                        )
            }

        } else {

            drawWrappedWords(
                canvas,
                words,
                currentWord,
                width,
                centerY,
                size
            )
        }
    }

    private fun findCurrentWord(
        words: List<LyricWord>
    ): Int {

        for (
            i in words.indices
        ) {

            if (
                position >=
                words[i].startMs &&
                position <
                words[i].endMs
            ) {

                return i
            }
        }

        return -1
    }

    private fun drawWord(
        canvas: Canvas,
        word: LyricWord,
        index: Int,
        currentWord: Int,
        x: Float,
        y: Float,
        size: Float
    ) {

        paint.textSize =
            size

        if (
            index ==
            currentWord
        ) {

            glowPaint.color =
                Color.WHITE

            glowPaint.textSize =
                size

            glowPaint.typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )

            glowPaint.alpha =
                90

            canvas.drawText(
                word.text,
                x,
                y,
                glowPaint
            )

            paint.color =
                Color.WHITE

            paint.alpha =
                255

        } else {

            paint.color =
                Color.WHITE

            paint.alpha =
                if (
                    currentWord >= 0 &&
                    index <
                    currentWord
                ) {
                    235
                } else {
                    105
                }
        }

        canvas.drawText(
            word.text,
            x,
            y,
            paint
        )

        paint.alpha =
            255
    }

    private fun drawWrappedWords(
        canvas: Canvas,
        words: List<LyricWord>,
        currentWord: Int,
        width: Float,
        centerY: Float,
        size: Float
    ) {

        paint.textSize =
            size

        val maxWidth =
            width * 0.88f

        val rows =
            mutableListOf<
                MutableList<Pair<Int, LyricWord>>
            >()

        var row =
            mutableListOf<
                Pair<Int, LyricWord>
            >()

        var rowWidth =
            0f

        for (
            i in words.indices
        ) {

            val word =
                words[i]

            val wordWidth =
                paint.measureText(
                    word.text
                )

            val space =
                if (
                    row.isEmpty()
                ) {
                    0f
                } else {
                    paint.measureText(
                        " "
                    )
                }

            if (
                row.isNotEmpty() &&
                rowWidth +
                    space +
                    wordWidth >
                    maxWidth
            ) {

                rows.add(
                    row
                )

                row =
                    mutableListOf()

                rowWidth =
                    0f
            }

            row.add(
                i to word
            )

            rowWidth +=
                if (
                    row.size == 1
                ) {
                    wordWidth
                } else {
                    space +
                        wordWidth
                }
        }

        if (
            row.isNotEmpty()
        ) {

            rows.add(
                row
            )
        }

        val lineHeight =
            size * 1.18f

        val totalHeight =
            rows.size *
                lineHeight

        var y =
            centerY -
                totalHeight / 2f +
                size

        for (
            currentRow in rows
        ) {

            var totalWidth =
                0f

            for (
                (_, word) in currentRow
            ) {

                totalWidth +=
                    paint.measureText(
                        word.text
                    ) +
                        paint.measureText(
                            " "
                        )
            }

            var x =
                (
                    width -
                        totalWidth
                ) / 2f

            for (
                (index, word)
                in currentRow
            ) {

                val wordWidth =
                    paint.measureText(
                        word.text
                    )

                drawWord(
                    canvas,
                    word,
                    index,
                    currentWord,
                    x,
                    y,
                    size
                )

                x +=
                    wordWidth +
                        paint.measureText(
                            " "
                        )
            }

            y +=
                lineHeight
        }
    }
        private fun drawCenteredWrappedText(
        canvas: Canvas,
        text: String,
        width: Float,
        centerY: Float,
        size: Float,
        alpha: Int
    ) {

        paint.textSize =
            size

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            Color.WHITE

        paint.alpha =
            alpha

        val maxWidth =
            width * 0.88f

        val words =
            text.trim()
                .split(
                    Regex(
                        "\\s+"
                    )
                )

        val rows =
            mutableListOf<String>()

        var current =
            ""

        for (
            word in words
        ) {

            val candidate =
                if (
                    current.isBlank()
                ) {
                    word
                } else {
                    "$current $word"
                }

            if (
                paint.measureText(
                    candidate
                ) <= maxWidth
            ) {

                current =
                    candidate

            } else {

                if (
                    current.isNotBlank()
                ) {

                    rows.add(
                        current
                    )
                }

                current =
                    word
            }
        }

        if (
            current.isNotBlank()
        ) {

            rows.add(
                current
            )
        }

        val lineHeight =
            size * 1.15f

        val totalHeight =
            rows.size *
                lineHeight

        var y =
            centerY -
                totalHeight / 2f +
                size

        for (
            row in rows
        ) {

            val rowWidth =
                paint.measureText(
                    row
                )

            canvas.drawText(
                row,
                (
                    width -
                        rowWidth
                ) / 2f,
                y,
                paint
            )

            y +=
                lineHeight
        }

        paint.alpha =
            255
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        width: Float,
        y: Float,
        size: Float,
        alpha: Int
    ) {

        paint.textSize =
            size *
                resources.displayMetrics
                    .scaledDensity

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            Color.WHITE

        paint.alpha =
            alpha

        val x =
            (
                width -
                    paint.measureText(
                        text
                    )
            ) / 2f

        canvas.drawText(
            text,
            x,
            y,
            paint
        )

        paint.alpha =
            255
    }

    private fun ellipsize(
        text: String,
        maxWidth: Float
    ): String {

        if (
            paint.measureText(
                text
            ) <= maxWidth
        ) {
            return text
        }

        var result =
            text

        while (
            result.length > 1 &&
            paint.measureText(
                "$result…"
            ) > maxWidth
        ) {

            result =
                result.dropLast(1)
        }

        return "$result…"
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (
            event.actionMasked
        ) {

            MotionEvent.ACTION_DOWN -> {

                downX =
                    event.x

                downY =
                    event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val dx =
                    event.x -
                        downX

                val dy =
                    event.y -
                        downY

                if (
                    abs(dx) >
                    100f &&
                    abs(dx) >
                    abs(dy)
                ) {

                    style =
                        if (
                            dx < 0
                        ) {
                            (
                                style + 1
                            ) %
                                styles
                        } else {
                            (
                                style -
                                    1 +
                                    styles
                            ) %
                                styles
                        }

                    invalidate()

                    return true
                }

                return true
            }
        }

        return true
    }
}
