package com.pulsevisualizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import kotlin.math.max
import kotlin.math.min

class LyricsVideoActivity : ComponentActivity() {

    private lateinit var lyricsView: LyricsVideoView

    private var lyricsJob: Job? = null

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

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
            LyricsVideoView(this)

        setContentView(
            lyricsView
        )

        MediaRepository.start(this)

        lifecycleScope.launch {

            MediaRepository.media.collect {
                updateMedia()
            }
        }

        startPositionLoop()

        updateMedia()
    }

    private fun updateMedia() {

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

            lyricsView.setLoading(false)
            lyricsView.setLyrics(null)

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

        val key =
            (
                title.trim() +
                    "|" +
                    artist.trim()
                ).lowercase()

        if (
            lyricsView.currentSongKey ==
            key
        ) {
            return
        }

        lyricsView.currentSongKey =
            key

        lyricsJob?.cancel()

        lyricsJob =
            lifecycleScope.launch {

                lyricsView.setLoading(true)

                val result =
                    kotlinx.coroutines
                        .withContext(
                            kotlinx.coroutines.Dispatchers.IO
                        ) {
                            LyricsRepository.getLyrics(
                                this@LyricsVideoActivity,
                                title,
                                artist
                            )
                        }

                if (
                    !isFinishing
                ) {
                    lyricsView.setLyrics(
                        result
                    )

                    lyricsView.setLoading(
                        false
                    )
                }
            }
    }

    private fun startPositionLoop() {

        lifecycleScope.launch {

            while (
                true
            ) {

                if (
                    isFinishing
                ) {
                    break
                }

                lyricsView.setPosition(
                    MediaRepository
                        .getCurrentPositionMs()
                )

                delay(30L)
            }
        }
    }

    override fun onDestroy() {

        lyricsJob?.cancel()

        super.onDestroy()
    }
}

private class LyricsVideoView(
    context: Context
) : View(context) {

    private val textPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG or
                Paint.SUBPIXEL_TEXT_FLAG
        )

    private val glowPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    private val backgroundPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        )

    private var title =
        ""

    private var artist =
        ""

    private var artwork:
        Bitmap? = null

    private var document:
        LyricsDocument? = null

    private var positionMs =
        0L

    private var loading =
        false

    private var style =
        0

    private var lastLineIndex =
        Int.MIN_VALUE

    private var animationStart =
        0L

    private var touchDownX =
        0f

    private var touchDownY =
        0f

    var currentSongKey =
        ""

    private val styleCount =
        5

    init {

        isFocusable =
            true

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )
    }

    fun setSong(
        title: String,
        artist: String,
        artwork: Bitmap?
    ) {

        val newTitle =
            title.trim()

        val newArtist =
            artist.trim()

        val changed =
            this.title != newTitle ||
            this.artist != newArtist

        this.title =
            newTitle

        this.artist =
            newArtist

        this.artwork =
            artwork

        if (
            changed
        ) {

            currentSongKey =
                ""

            lastLineIndex =
                Int.MIN_VALUE

            document =
                null
        }

        invalidate()
    }

    fun setLyrics(
        document: LyricsDocument?
    ) {

        this.document =
            document

        lastLineIndex =
            Int.MIN_VALUE

        animationStart =
            SystemClock.uptimeMillis()

        invalidate()
    }

    fun setLoading(
        loading: Boolean
    ) {

        this.loading =
            loading

        invalidate()
    }

    fun setPosition(
        position: Long
    ) {

        positionMs =
            position.coerceAtLeast(0L)

        invalidate()
    }

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(canvas)

        val width =
            width.toFloat()

        val height =
            height.toFloat()

        if (
            width <= 0f ||
            height <= 0f
        ) {
            return
        }

        drawBackground(
            canvas,
            width,
            height
        )

        drawArtworkAtmosphere(
            canvas,
            width,
            height
        )

        drawTopBar(
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
                100L
            )

            return
        }

        val currentDocument =
            document

        if (
            currentDocument == null ||
            currentDocument.lines.isEmpty()
        ) {

            drawEmptyState(
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
            currentDocument
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
                            62,
                            20,
                            110
                        ),
                        Color.rgb(
                            20,
                            8,
                            38
                        ),
                        Color.BLACK
                    )

                1 ->
                    intArrayOf(
                        Color.rgb(
                            5,
                            75,
                            115
                        ),
                        Color.rgb(
                            8,
                            20,
                            55
                        ),
                        Color.BLACK
                    )

                2 ->
                    intArrayOf(
                        Color.rgb(
                            115,
                            30,
                            20
                        ),
                        Color.rgb(
                            50,
                            8,
                            20
                        ),
                        Color.BLACK
                    )

                3 ->
                    intArrayOf(
                        Color.rgb(
                            8,
                            100,
                            75
                        ),
                        Color.rgb(
                            5,
                            35,
                            35
                        ),
                        Color.BLACK
                    )

                else ->
                    intArrayOf(
                        Color.rgb(
                            85,
                            70,
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

        backgroundPaint.shader =
            LinearGradient(
                0f,
                0f,
                width,
                height,
                colors,
                floatArrayOf(
                    0f,
                    0.48f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            backgroundPaint
        )

        backgroundPaint.shader =
            null
    }

    private fun drawArtworkAtmosphere(
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

        val radius =
            min(
                width,
                height
            ) * 0.62f

        glowPaint.shader =
            BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )

        glowPaint.alpha =
            30

        glowPaint.maskFilter =
            BlurMaskFilter(
                70f,
                BlurMaskFilter.Blur.NORMAL
            )

        canvas.drawCircle(
            width * 0.5f,
            height * 0.48f,
            radius,
            glowPaint
        )

        glowPaint.maskFilter =
            null

        glowPaint.shader =
            null
    }

    private fun drawTopBar(
        canvas: Canvas,
        width: Float
    ) {

        val density =
            resources.displayMetrics.density

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            12f * density

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
            170

        canvas.drawText(
            "LYRICS",
            24f * density,
            30f * density,
            textPaint
        )

        textPaint.textSize =
            9f * density

        textPaint.alpha =
            90

        val styleText =
            "${style + 1}/$styleCount"

        canvas.drawText(
            styleText,
            width -
                24f * density -
                textPaint.measureText(
                    styleText
                ),
            30f * density,
            textPaint
        )

        textPaint.textSize =
            17f * density

        textPaint.alpha =
            245

        val safeTitle =
            if (
                title.isBlank()
            ) {
                "Nothing playing"
            } else {
                title
            }

        canvas.drawText(
            truncateText(
                safeTitle,
                width -
                    48f * density
            ),
            24f * density,
            57f * density,
            textPaint
        )

        if (
            artist.isNotBlank()
        ) {

            textPaint.textSize =
                11f * density

            textPaint.alpha =
                130

            canvas.drawText(
                truncateText(
                    artist,
                    width -
                        48f * density
                ),
                24f * density,
                76f * density,
                textPaint
            )
        }

        textPaint.alpha =
            255
    }

    private fun drawLoading(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val density =
            resources.displayMetrics.density

        val pulse =
            (
                SystemClock.uptimeMillis()
                    % 1200L
            ).toFloat() /
                1200f

        val alpha =
            (
                120f +
                    100f *
                    (
                        0.5f +
                            0.5f *
                            kotlin.math.sin(
                                pulse *
                                    Math.PI *
                                    2.0
                            )
                    )
            ).toInt()

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            20f * density

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
            alpha

        val text =
            "Finding lyrics..."

        canvas.drawText(
            text,
            (
                width -
                    textPaint.measureText(
                        text
                    )
            ) / 2f,
            height * 0.53f,
            textPaint
        )

        textPaint.alpha =
            255

        postInvalidateDelayed(
            30L
        )
    }

    private fun drawEmptyState(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val density =
            resources.displayMetrics.density

        val media =
            MediaRepository.media.value

        if (
            media.title.isBlank() ||
            media.title.equals(
                "Nothing playing",
                ignoreCase = true
            ) ||
            media.title.equals(
                "Unknown title",
                ignoreCase = true
            )
        ) {
            return
        }

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            20f * density

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
            150

        val text =
            "No lyrics found"

        canvas.drawText(
            text,
            (
                width -
                    textPaint.measureText(
                        text
                    )
            ) / 2f,
            height * 0.53f,
            textPaint
        )

        textPaint.alpha =
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

        if (
            lines.isEmpty()
        ) {
            return
        }

        val currentIndex =
            findCurrentLineIndex(
                lines
            )

        if (
            currentIndex !=
            lastLineIndex
        ) {

            lastLineIndex =
                currentIndex

            animationStart =
                SystemClock.uptimeMillis()
        }

        if (
            currentIndex < 0
        ) {

            drawInstrumentalState(
                canvas,
                width,
                height
            )

            return
        }

        val current =
            lines.getOrNull(
                currentIndex
            )

        if (
            current == null
        ) {
            return
        }

        val previous =
            lines.getOrNull(
                currentIndex - 1
            )

        val next =
            lines.getOrNull(
                currentIndex + 1
            )

        val active =
            isLineActive(
                current
            )

        if (
            !active
        ) {

            drawInstrumentalState(
                canvas,
                width,
                height
            )

            return
        }

        val elapsed =
            (
                SystemClock.uptimeMillis() -
                    animationStart
            )
                .coerceAtLeast(0L)

        val rawProgress =
            (
                elapsed
                    .coerceAtMost(
                        500L
                    )
                    .toFloat()
                / 500f
            )

        val eased =
            1f -
                (
                    1f -
                        rawProgress
                ) *
                (
                    1f -
                        rawProgress
                )

        val centerY =
            height * 0.51f

        if (
            previous != null
        ) {

            drawSecondaryLine(
                canvas,
                previous.text,
                width,
                centerY -
                    105f,
                0.28f
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

            drawSecondaryLine(
                canvas,
                next.text,
                width,
                centerY +
                    112f,
                0.36f
            )
        }

        drawProgressBar(
            canvas,
            width,
            height,
            current
        )
    }

    private fun drawInstrumentalState(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val density =
            resources.displayMetrics.density

        val pulse =
            (
                SystemClock.uptimeMillis()
                    % 1800L
            ).toFloat() /
                1800f

        val alpha =
            (
                70f +
                    50f *
                    (
                        0.5f +
                            0.5f *
                            kotlin.math.sin(
                                pulse *
                                    Math.PI *
                                    2.0
                            )
                    )
            ).toInt()

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            15f * density

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
            alpha

        val text =
            "♪"

        canvas.drawText(
            text,
            (
                width -
                    textPaint.measureText(
                        text
                    )
            ) / 2f,
            height * 0.53f,
            textPaint
        )

        textPaint.alpha =
            255

        postInvalidateDelayed(
            30L
        )
    }

    private fun drawCurrentLine(
        canvas: Canvas,
        line: LyricLine,
        width: Float,
        centerY: Float,
        animation: Float
    ) {

        val density =
            resources.displayMetrics.density

        val baseSize =
            32f * density

        val scale =
            0.96f +
                0.04f * animation

        val size =
            baseSize * scale

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
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
            LyricsTiming.findCurrentWord(
                words,
                positionMs
            )

        val allText =
            words.joinToString(
                " "
            ) {
                it.text
            }

        val maxWidth =
            width * 0.90f

        val fullWidth =
            textPaint.measureText(
                allText
            )

        if (
            fullWidth <= maxWidth
        ) {

            drawSingleLineWords(
                canvas,
                words,
                currentWord,
                width,
                centerY,
                size
            )

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

    private fun drawSingleLineWords(
        canvas: Canvas,
        words: List<LyricWord>,
        currentWord: Int,
        width: Float,
        centerY: Float,
        size: Float
    ) {

        textPaint.textSize =
            size

        var totalWidth =
            0f

        for (
            word in words
        ) {

            totalWidth +=
                textPaint.measureText(
                    word.text
                )

            totalWidth +=
                textPaint.measureText(
                    " "
                )
        }

        val startX =
            (
                width -
                    totalWidth
            ) / 2f

        var x =
            startX

        for (
            index in words.indices
        ) {

            val word =
                words[index]

            val wordWidth =
                textPaint.measureText(
                    word.text
                )

            drawWord(
                canvas,
                word,
                index,
                currentWord,
                x,
                centerY,
                size
            )

            x +=
                wordWidth +
                    textPaint.measureText(
                        " "
                    )
        }
    }

    private fun drawWrappedWords(
        canvas: Canvas,
        words: List<LyricWord>,
        currentWord: Int,
        width: Float,
        centerY: Float,
        size: Float
    ) {

        textPaint.textSize =
            size

        val maxWidth =
            width * 0.88f

        val rows =
            mutableListOf<
                MutableList<Pair<Int, LyricWord>>
            >()

        var currentRow =
            mutableListOf<
                Pair<Int, LyricWord>
            >()

        var rowWidth =
            0f

        for (
            index in words.indices
        ) {

            val word =
                words[index]

            val widthOfWord =
                textPaint.measureText(
                    word.text
                )

            val space =
                if (
                    currentRow.isEmpty()
                ) {
                    0f
                } else {
                    textPaint.measureText(
                        " "
                    )
                }

            if (
                currentRow.isNotEmpty() &&
                rowWidth +
                    space +
                    widthOfWord >
                    maxWidth
            ) {

                rows.add(
                    currentRow
                )

                currentRow =
                    mutableListOf()

                rowWidth =
                    0f
            }

            currentRow.add(
                index to word
            )

            rowWidth +=
                if (
                    currentRow.size == 1
                ) {
                    widthOfWord
                } else {
                    space +
                        widthOfWord
                }
        }

        if (
            currentRow.isNotEmpty()
        ) {
            rows.add(
                currentRow
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
            row in rows
        ) {

            var rowWidth =
                0f

            for (
                (_, word) in row
            ) {

                rowWidth +=
                    textPaint.measureText(
                        word.text
                    )

                rowWidth +=
                    textPaint.measureText(
                        " "
                    )
            }

            var x =
                (
                    width -
                        rowWidth
                ) / 2f

            for (
                (index, word) in row
            ) {

                val wordWidth =
                    textPaint.measureText(
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
                    textPaint.measureText(
                        " "
                    )
            }

            y +=
                lineHeight
        }
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

        val isCurrent =
            index == currentWord

        val hasPassed =
            currentWord >= 0 &&
                index < currentWord

        val isFuture =
            !hasPassed &&
                !isCurrent

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            size

        if (
            isCurrent
        ) {

            drawHighlightedWord(
                canvas,
                word,
                x,
                y,
                size
            )

        } else {

            textPaint.color =
                Color.WHITE

            textPaint.alpha =
                when {

                    hasPassed ->
                        240

                    isFuture ->
                        115

                    else ->
                        150
                }

            canvas.drawText(
                word.text,
                x,
                y,
                textPaint
            )
        }

        textPaint.alpha =
            255
    }

    private fun drawHighlightedWord(
        canvas: Canvas,
        word: LyricWord,
        x: Float,
        y: Float,
        size: Float
    ) {

        textPaint.textSize =
            size

        val progress =
            LyricsTiming.wordProgress(
                word,
                positionMs
            )
                .coerceIn(
                    0f,
                    1f
                )

        val glowAlpha =
            (
                65f +
                    50f *
                    kotlin.math.sin(
                        progress *
                            Math.PI
                    )
            )
                .toInt()
                .coerceIn(
                    45,
                    125
                )

        glowPaint.color =
            Color.WHITE

        glowPaint.alpha =
            glowAlpha

        glowPaint.textSize =
            size

        glowPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        glowPaint.maskFilter =
            BlurMaskFilter(
                14f,
                BlurMaskFilter.Blur.NORMAL
            )

        canvas.drawText(
            word.text,
            x,
            y,
            glowPaint
        )

        glowPaint.maskFilter =
            null

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
            255

        canvas.drawText(
            word.text,
            x,
            y,
            textPaint
        )
    }
        private fun drawSecondaryLine(
        canvas: Canvas,
        text: String,
        width: Float,
        centerY: Float,
        alphaFactor: Float
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        val density =
            resources.displayMetrics.density

        val size =
            16f * density

        drawCenteredWrappedText(
            canvas,
            text,
            width,
            centerY,
            size,
            (
                255f *
                    alphaFactor
            )
                .toInt()
                .coerceIn(
                    0,
                    255
                )
        )
    }

    private fun drawCenteredWrappedText(
        canvas: Canvas,
        text: String,
        width: Float,
        centerY: Float,
        size: Float,
        alpha: Int
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        textPaint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        textPaint.textSize =
            size

        textPaint.color =
            Color.WHITE

        textPaint.alpha =
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
                textPaint.measureText(
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

        if (
            rows.isEmpty()
        ) {
            return
        }

        val lineHeight =
            size * 1.16f

        val totalHeight =
            (
                rows.size - 1
            ) * lineHeight

        var y =
            centerY -
                totalHeight / 2f

        for (
            row in rows
        ) {

            val rowWidth =
                textPaint.measureText(
                    row
                )

            val x =
                (
                    width -
                        rowWidth
                ) / 2f

            canvas.drawText(
                row,
                x,
                y,
                textPaint
            )

            y +=
                lineHeight
        }

        textPaint.alpha =
            255
    }

    private fun drawProgressBar(
        canvas: Canvas,
        width: Float,
        height: Float,
        line: LyricLine
    ) {

        if (
            line.endMs <=
            line.timeMs
        ) {
            return
        }

        val duration =
            line.endMs -
                line.timeMs

        val elapsed =
            positionMs -
                line.timeMs

        val progress =
            (
                elapsed.toFloat() /
                    duration.toFloat()
            )
                .coerceIn(
                    0f,
                    1f
                )

        val density =
            resources.displayMetrics.density

        val left =
            24f * density

        val right =
            width -
                24f * density

        val y =
            height -
                38f * density

        backgroundPaint.color =
            Color.WHITE

        backgroundPaint.alpha =
            40

        canvas.drawRoundRect(
            left,
            y,
            right,
            y +
                2f * density,
            4f * density,
            4f * density,
            backgroundPaint
        )

        backgroundPaint.alpha =
            220

        canvas.drawRoundRect(
            left,
            y,
            left +
                (
                    right -
                        left
                ) *
                progress,
            y +
                2f * density,
            4f * density,
            4f * density,
            backgroundPaint
        )

        backgroundPaint.alpha =
            255
    }

    private fun findCurrentLineIndex(
        lines: List<LyricLine>
    ): Int {

        if (
            lines.isEmpty()
        ) {
            return -1
        }

        var result =
            -1

        for (
            index in lines.indices
        ) {

            if (
                lines[index].timeMs <=
                positionMs
            ) {

                result =
                    index

            } else {

                break
            }
        }

        return result
    }

    private fun isLineActive(
        line: LyricLine
    ): Boolean {

        if (
            positionMs <
            line.timeMs
        ) {
            return false
        }

        if (
            positionMs >=
            line.endMs
        ) {
            return false
        }

        if (
            line.text.isBlank()
        ) {
            return false
        }

        return true
    }

    private fun truncateText(
        text: String,
        maxWidth: Float
    ): String {

        if (
            text.isBlank()
        ) {
            return ""
        }

        if (
            textPaint.measureText(
                text
            ) <= maxWidth
        ) {
            return text
        }

        var result =
            text

        while (
            result.length > 1 &&
            textPaint.measureText(
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

                touchDownX =
                    event.x

                touchDownY =
                    event.y

                return true
            }

            MotionEvent.ACTION_UP -> {

                val dx =
                    event.x -
                        touchDownX

                val dy =
                    event.y -
                        touchDownY

                if (
                    abs(dx) >
                    100f &&
                    abs(dx) >
                    abs(dy)
                ) {

                    if (
                        dx < 0f
                    ) {

                        style =
                            (
                                style + 1
                            ) %
                            styleCount

                    } else {

                        style =
                            if (
                                style == 0
                            ) {
                                styleCount - 1
                            } else {
                                style - 1
                            }
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
