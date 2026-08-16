package com.pulsevisualizer

import android.content.ComponentName
import android.graphics.*
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class LyricsVideoActivity : ComponentActivity() {

    private lateinit var view: LyricsVideoView

    private var lyricsJob: Job? = null

    private var mediaManager:
        MediaSessionManager? = null

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

        mediaManager =
            getSystemService(
                MediaSessionManager::class.java
            )

        view =
            LyricsVideoView(this)

        setContentView(view)

        MediaRepository.start(this)

        lifecycleScope.launch {

            MediaRepository.media.collect {
                mediaChanged()
            }
        }

        startPositionLoop()
    }

    private fun mediaChanged() {

        val media =
            MediaRepository.media.value

        view.setSong(
            media.title,
            media.artist,
            media.artwork
        )

        loadLyrics(
            media.title,
            media.artist
        )
    }

    private fun loadLyrics(
        title: String,
        artist: String
    ) {

        lyricsJob?.cancel()

        if (
            title.isBlank() ||
            title == "Nothing playing"
        ) {

            view.setLyrics(null)

            return
        }

        lyricsJob =
            lifecycleScope.launch {

                view.setLoading(true)

                val result =
                    LyricsRepository.getLyrics(
                        title,
                        artist
                    )

                if (
                    !isFinishing
                ) {
                    view.setLyrics(result)
                    view.setLoading(false)
                }
            }
    }

    private fun startPositionLoop() {

        lifecycleScope.launch {

            while (
                !isFinishing
            ) {

                view.setPosition(
                    currentPosition()
                )

                delay(50)
            }
        }
    }

    private fun currentController():
        MediaController? {

        val manager =
            mediaManager
                ?: return null

        return try {

            val component =
                ComponentName(
                    this,
                    MediaListenerService::class.java
                )

            val sessions =
                manager.getActiveSessions(
                    component
                )

            sessions.firstOrNull {
                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            } ?: sessions.firstOrNull {
                it.metadata != null
            }

        } catch (
            _: SecurityException
        ) {

            null
        }
    }

    private fun currentPosition():
        Long {

        val controller =
            currentController()
                ?: return 0L

        val state =
            controller.playbackState
                ?: return 0L

        var position =
            state.position.coerceAtLeast(0L)

        if (
            state.state ==
            PlaybackState.STATE_PLAYING
        ) {

            val elapsed =
                SystemClock.elapsedRealtime() -
                    state.lastPositionUpdateTime

            position += elapsed
        }

        val duration =
            controller.metadata
                ?.getLong(
                    MediaMetadata.METADATA_KEY_DURATION
                )
                ?: Long.MAX_VALUE

        return position.coerceIn(
            0L,
            max(
                0L,
                duration
            )
        )
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
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private var title =
        "Nothing playing"

    private var artist =
        ""

    private var artwork:
        Bitmap? = null

    private var lyrics:
        LyricsResult? = null

    private var position =
        0L

    private var loading =
        false

    private var style =
        0

    private var downX =
        0f

    private var downY =
        0f

    private var previousIndex =
        -1

    private var animationStart =
        SystemClock.uptimeMillis()

    private val styles =
        4

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
            previousIndex = -1
        }

        invalidate()
    }

    fun setLyrics(
        result: LyricsResult?
    ) {

        lyrics =
            result

        previousIndex = -1

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
            value

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

        if (loading) {

            drawLoading(
                canvas,
                width,
                height
            )

            return
        }

        val result =
            lyrics

        if (
            result == null
        ) {

            drawNoLyrics(
                canvas,
                width,
                height
            )

            drawStyleHint(
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
            result
        )

        drawStyleHint(
            canvas,
            width,
            height
        )
    }

    private fun drawBackground(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val colors =
            when (style) {

                0 -> intArrayOf(
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

                1 -> intArrayOf(
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

                2 -> intArrayOf(
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

                else -> intArrayOf(
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
            }

        val shader =
            LinearGradient(
                0f,
                0f,
                width,
                height,
                colors,
                null,
                Shader.TileMode.CLAMP
            )

        paint.shader =
            shader

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
            artwork ?: return

        val radius =
            min(width, height) *
                0.55f

        val cx =
            width * 0.5f

        val cy =
            height * 0.43f

        glowPaint.shader =
            BitmapShader(
                bitmap,
                Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP
            )

        glowPaint.alpha =
            42

        canvas.drawCircle(
            cx,
            cy,
            radius,
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

        paint.textSize =
            13f * resources.displayMetrics.scaledDensity

        paint.color =
            Color.WHITE

        paint.alpha =
            230

        canvas.drawText(
            "LYRICS VIDEO",
            28f,
            44f,
            paint
        )

        paint.textSize =
            10f *
            resources.displayMetrics.scaledDensity

        paint.alpha =
            130

        val counter =
            "${style + 1}/$styles"

        canvas.drawText(
            counter,
            width - 48f,
            44f,
            paint
        )

        paint.textSize =
            20f *
            resources.displayMetrics.scaledDensity

        paint.alpha =
            255

        val titleWidth =
            width - 56f

        val titleText =
            ellipsize(
                title,
                titleWidth
            )

        canvas.drawText(
            titleText,
            28f,
            84f,
            paint
        )

        paint.textSize =
            13f *
            resources.displayMetrics.scaledDensity

        paint.alpha =
            150

        canvas.drawText(
            artist,
            28f,
            106f,
            paint
        )
    }

    private fun drawLyrics(
        canvas: Canvas,
        width: Float,
        height: Float,
        result: LyricsResult
    ) {

        val lines =
            result.lines

        if (
            lines.isEmpty()
        ) {
            return
        }

        val index =
            findCurrentIndex(
                lines
            )

        if (
            index != previousIndex
        ) {

            animationStart =
                SystemClock.uptimeMillis()

            previousIndex =
                index
        }

        val now =
            SystemClock.uptimeMillis()

        val animation =
            (
                now -
                    animationStart
                ).coerceAtMost(450L)
                .toFloat() / 450f

        val current =
            lines.getOrNull(index)

        val previous =
            lines.getOrNull(
                index - 1
            )

        val next =
            lines.getOrNull(
                index + 1
            )

        val centerY =
            height * 0.52f

        if (
            previous != null
        ) {

            drawCenteredText(
                canvas,
                previous.text,
                width,
                centerY - 120f,
                18f,
                Color.WHITE,
                65
            )
        }

        if (
            current != null
        ) {

            val scale =
                1f +
                    0.08f * animation

            paint.textSize =
                32f *
                resources.displayMetrics.scaledDensity *
                scale

            paint.typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    Typeface.BOLD
                )

            paint.color =
                Color.WHITE

            paint.alpha =
                255

            val text =
                current.text

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
                centerY,
                paint
            )

            drawLyricProgress(
                canvas,
                width,
                height,
                lines,
                index
            )
        }

        if (
            next != null
        ) {

            drawCenteredText(
                canvas,
                next.text,
                width,
                centerY + 95f,
                18f,
                Color.WHITE,
                80
            )
        }

        drawSmallStatus(
            canvas,
            width,
            height,
            result.synced
        )
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        width: Float,
        y: Float,
        size: Float,
        color: Int,
        alpha: Int
    ) {

        paint.textSize =
            size *
            resources.displayMetrics.scaledDensity

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            color

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
    }

    private fun drawLyricProgress(
        canvas: Canvas,
        width: Float,
        height: Float,
        lines: List<LyricLine>,
        index: Int
    ) {

        val start =
            lines[index].timeMs

        val end =
            lines.getOrNull(
                index + 1
            )?.timeMs
                ?: start + 4000L

        val progress =
            if (
                end > start
            ) {

                (
                    position -
                        start
                    ).toFloat() /
                    (
                        end -
                            start
                        ).toFloat()

            } else {

                0f
            }

        val p =
            progress.coerceIn(
                0f,
                1f
            )

        val left =
            32f

        val right =
            width - 32f

        val y =
            height - 70f

        paint.color =
            Color.WHITE

        paint.alpha =
            55

        canvas.drawRoundRect(
            left,
            y,
            right,
            y + 3f,
            5f,
            5f,
            paint
        )

        paint.alpha =
            230

        canvas.drawRoundRect(
            left,
            y,
            left +
                (right - left) * p,
            y + 3f,
            5f,
            5f,
            paint
        )
    }

    private fun drawSmallStatus(
        canvas: Canvas,
        width: Float,
        height: Float,
        synced: Boolean
    ) {

        paint.textSize =
            9f *
            resources.displayMetrics.scaledDensity

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            Color.WHITE

        paint.alpha =
            100

        val text =
            if (synced) {
                "SYNCED LYRICS"
            } else {
                "LYRICS"
            }

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
            height - 40f,
            paint
        )
    }

    private fun drawLoading(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        drawCenteredText(
            canvas,
            "Finding lyrics...",
            width,
            height * 0.52f,
            22f,
            Color.WHITE,
            230
        )
    }

    private fun drawNoLyrics(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        drawCenteredText(
            canvas,
            "No lyrics found",
            width,
            height * 0.52f,
            24f,
            Color.WHITE,
            220
        )
    }

    private fun drawStyleHint(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        paint.textSize =
            9f *
            resources.displayMetrics.scaledDensity

        paint.typeface =
            Typeface.create(
                Typeface.DEFAULT,
                Typeface.BOLD
            )

        paint.color =
            Color.WHITE

        paint.alpha =
            90

        val text =
            "SWIPE LEFT / RIGHT TO CHANGE STYLE"

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
            height - 18f,
            paint
        )
    }

    private fun findCurrentIndex(
        lines: List<LyricLine>
    ): Int {

        var index =
            0

        for (
            i in lines.indices
        ) {

            if (
                lines[i].timeMs <=
                position
            ) {
                index = i
            } else {
                break
            }
        }

        return index
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
                    event.x - downX

                val dy =
                    event.y - downY

                if (
                    abs(dx) > 120f &&
                    abs(dx) > abs(dy)
                ) {

                    if (
                        dx < 0
                    ) {

                        style =
                            (
                                style + 1
                            ) % styles

                    } else {

                        style =
                            if (
                                style == 0
                            ) {
                                styles - 1
                            } else {
                                style - 1
                            }
                    }

                    invalidate()

                    return true
                }

                if (
                    event.y < 70f &&
                    event.x > width - 80f
                ) {

                    (
                        context as?
                            android.app.Activity
                    )?.finish()

                    return true
                }

                return true
            }
        }

        return true
    }
}
