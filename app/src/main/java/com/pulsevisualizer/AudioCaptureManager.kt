package com.pulsevisualizer

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaProjection
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

object AudioCaptureManager {

    private const val BAND_COUNT = 64

    private const val SAMPLE_RATE = 44100

    private const val BUFFER_SIZE = 2048

    private val _bands =
        MutableStateFlow(
            FloatArray(BAND_COUNT)
        )

    val bands: StateFlow<FloatArray> =
        _bands

    private val _isCapturing =
        MutableStateFlow(false)

    val isCapturing: StateFlow<Boolean> =
        _isCapturing

    private var audioRecord: AudioRecord? = null

    private var mediaProjection: MediaProjection? =
        null

    private var captureJob: Job? = null

    private var running = false

    fun initialize(
        context: Context
    ) {
        // Playback capture is started after the
        // user grants MediaProjection permission.
    }

    fun start(
        resultCode: Int,
        data: Intent
    ): Boolean {

        if (Build.VERSION.SDK_INT < 29) {
            return false
        }

        stop()

        return try {

            val context =
                AppContextHolder.context
                    ?: return false

            val projectionManager =
                context.getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as android.media.projection.MediaProjectionManager

            mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    data
                )

            val projection =
                mediaProjection
                    ?: return false

            val playbackConfig =
                AudioPlaybackCaptureConfiguration
                    .Builder(projection)
                    .addMatchingUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_GAME
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_UNKNOWN
                    )
                    .build()

            val format =
                AudioFormat.Builder()
                    .setEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    .setSampleRate(
                        SAMPLE_RATE
                    )
                    .setChannelMask(
                        AudioFormat.CHANNEL_IN_MONO
                    )
                    .build()

            val minimumBuffer =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            val bufferSize =
                maxOf(
                    minimumBuffer * 2,
                    BUFFER_SIZE * 2
                )

            audioRecord =
                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(
                        bufferSize
                    )
                    .setAudioPlaybackCaptureConfig(
                        playbackConfig
                    )
                    .build()

            if (
                audioRecord?.state !=
                AudioRecord.STATE_INITIALIZED
            ) {
                stop()
                return false
            }

            audioRecord?.startRecording()

            if (
                audioRecord?.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {
                stop()
                return false
            }

            running = true

            _isCapturing.value = true

            captureJob =
                CoroutineScope(
                    Dispatchers.Default
                ).launch {

                    captureAudio(
                        bufferSize
                    )
                }

            true

        } catch (
            _: SecurityException
        ) {

            stop()
            false

        } catch (
            _: Exception
        ) {

            stop()
            false
        }
    }

    private suspend fun captureAudio(
        bufferSize: Int
    ) {

        val samples =
            ShortArray(
                BUFFER_SIZE
            )

        val previousBands =
            FloatArray(
                BAND_COUNT
            )

        while (
            isActive &&
            running
        ) {

            val read =
                try {

                    audioRecord?.read(
                        samples,
                        0,
                        samples.size,
                        AudioRecord.READ_BLOCKING
                    ) ?: -1

                } catch (
                    _: Exception
                ) {

                    -1
                }

            if (read <= 0) {
                continue
            }

            calculateBands(
                samples,
                read,
                previousBands
            )
        }
    }

    private fun calculateBands(
        samples: ShortArray,
        sampleCount: Int,
        previous: FloatArray
    ) {

        if (sampleCount <= 0) {
            return
        }

        val output =
            FloatArray(
                BAND_COUNT
            )

        /*
         * First calculate overall RMS.
         * This gives us a reliable indication
         * that music is actually playing.
         */
        var energy = 0.0

        for (i in 0 until sampleCount) {

            val value =
                samples[i].toDouble() /
                    32768.0

            energy +=
                value * value
        }

        val rms =
            sqrt(
                energy /
                    sampleCount
            )

        /*
         * Convert PCM into a normalized level.
         */
        val level =
            (
                rms * 8.0
            )
                .coerceIn(
                    0.0,
                    1.0
                )
                .toFloat()

        /*
         * 64 frequency bands.
         *
         * This is a lightweight DFT-style
         * analyser. It isn't intended to be
         * an audiophile spectrum analyser;
         * it is designed to provide smooth,
         * responsive visualizer data.
         */
        val maxFrequency =
            SAMPLE_RATE / 2.0

        for (
            band in 0 until BAND_COUNT
        ) {

            val lowFrequency =
                25.0 +
                    (
                        maxFrequency -
                            25.0
                        ) *
                    band.toDouble() /
                    BAND_COUNT

            val highFrequency =
                25.0 +
                    (
                        maxFrequency -
                            25.0
                        ) *
                    (band + 1).toDouble() /
                    BAND_COUNT

            val frequency =
                (
                    lowFrequency +
                        highFrequency
                    ) / 2.0

            val omega =
                2.0 *
                    PI *
                    frequency /
                    SAMPLE_RATE

            var real = 0.0
            var imag = 0.0

            /*
             * Sample every second PCM
             * value for performance.
             */
            var i = 0

            while (
                i < sampleCount
            ) {

                val sample =
                    samples[i].toDouble() /
                        32768.0

                val angle =
                    omega * i

                real +=
                    sample *
                        cos(angle)

                imag +=
                    sample *
                        kotlin.math.sin(
                            angle
                        )

                i += 2
            }

            val magnitude =
                sqrt(
                    real * real +
                        imag * imag
                ) /
                    (
                        sampleCount / 2.0
                    )

            val normalized =
                (
                    magnitude * 14.0
                )
                    .coerceIn(
                        0.0,
                        1.0
                    )
                    .toFloat()

            /*
             * Bass gets extra weight.
             */
            val bassBoost =
                if (band < 12) {
                    1.25f
                } else {
                    1f
                }

            output[band] =
                (
                    normalized *
                        bassBoost
                )
                    .coerceIn(
                        0f,
                        1f
                    )
        }

        /*
         * If the spectrum calculation is
         * extremely quiet, retain a small
         * amount of the overall RMS so that
         * the visualizer still reacts.
         */
        for (
            i in output.indices
        ) {

            val fallback =
                level *
                    (
                        0.35f +
                            0.65f *
                            (
                                1f -
                                    i.toFloat() /
                                    BAND_COUNT
                                )
                        )

            output[i] =
                maxOf(
                    output[i],
                    fallback
                )
        }

        /*
         * Attack/release smoothing.
         *
         * Fast attack = punchy bass.
         * Slow release = no ugly flickering.
         */
        for (
            i in output.indices
        ) {

            val current =
                output[i]

            val old =
                previous[i]

            previous[i] =
                if (
                    current > old
                ) {

                    old +
                        (
                            current -
                                old
                            ) *
                            0.65f

                } else {

                    old +
                        (
                            current -
                                old
                            ) *
                            0.16f
                }
        }

        _bands.value =
            previous.copyOf()
    }

    fun stop() {

        running = false

        _isCapturing.value =
            false

        captureJob?.cancel()

        captureJob = null

        try {
            audioRecord?.stop()
        } catch (
            _: Exception
        ) {
        }

        try {
            audioRecord?.release()
        } catch (
            _: Exception
        ) {
        }

        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (
            _: Exception
        ) {
        }

        mediaProjection = null

        _bands.value =
            FloatArray(
                BAND_COUNT
            )
    }

    fun setBands(
        values: FloatArray
    ) {

        val output =
            FloatArray(
                BAND_COUNT
            )

        val count =
            minOf(
                values.size,
                BAND_COUNT
            )

        for (
            i in 0 until count
        ) {

            output[i] =
                values[i]
                    .coerceIn(
                        0f,
                        1f
                    )
        }

        _bands.value =
            output
    }
}
