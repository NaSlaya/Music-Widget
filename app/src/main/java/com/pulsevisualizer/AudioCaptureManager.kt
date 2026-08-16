package com.pulsevisualizer

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object AudioCaptureManager {

    private const val BAND_COUNT = 64
    private const val SAMPLE_RATE = 44100
    private const val FFT_SIZE = 2048

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

    private var projection: MediaProjection? = null

    private var recorder: AudioRecord? = null

    private var captureJob: Job? = null

    private var scope: CoroutineScope? = null

    @Synchronized
    fun start(
        resultCode: Int,
        data: Intent
    ): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        stop()

        val context =
            AppContextHolder.context

        val manager =
            context.getSystemService(
                MediaProjectionManager::class.java
            ) ?: return false

        val newProjection =
            try {
                manager.getMediaProjection(
                    resultCode,
                    data
                )
            } catch (_: Exception) {
                null
            } ?: return false

        projection = newProjection

        val captureConfig =
            try {
                AudioPlaybackCaptureConfiguration
                    .Builder(newProjection)
                    .addMatchingUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_GAME
                    )
                    .build()
            } catch (_: Exception) {

                projection?.stop()
                projection = null

                return false
            }

        val audioFormat =
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

        if (minimumBuffer <= 0) {

            projection?.stop()
            projection = null

            return false
        }

        val bufferSize =
            maxOf(
                minimumBuffer * 2,
                FFT_SIZE * 2
            )

        val newRecorder =
            try {
                AudioRecord.Builder()
                    .setAudioFormat(
                        audioFormat
                    )
                    .setBufferSizeInBytes(
                        bufferSize
                    )
                    .setAudioPlaybackCaptureConfig(
                        captureConfig
                    )
                    .build()
            } catch (_: Exception) {

                projection?.stop()
                projection = null

                return false
            }

        recorder = newRecorder

        scope =
            CoroutineScope(
                Dispatchers.Default
            )

        try {
            newRecorder.startRecording()
        } catch (_: Exception) {

            try {
                newRecorder.release()
            } catch (_: Exception) {
            }

            recorder = null

            projection?.stop()
            projection = null

            scope?.cancel()
            scope = null

            return false
        }

        _isCapturing.value = true

        captureJob =
            scope?.launch {
                captureLoop(newRecorder)
            }

        return true
    }

    private suspend fun captureLoop(
        audioRecord: AudioRecord
    ) {

        val samples =
            ShortArray(FFT_SIZE)

        val real =
            DoubleArray(FFT_SIZE)

        val imaginary =
            DoubleArray(FFT_SIZE)

        while (
            currentCoroutineContext().isActive &&
            _isCapturing.value
        ) {

            val read =
                try {
                    audioRecord.read(
                        samples,
                        0,
                        samples.size
                    )
                } catch (_: Exception) {
                    -1
                }

            if (read <= 0) {
                delay(10L)
                continue
            }

            for (i in 0 until FFT_SIZE) {

                val sample =
                    if (i < read) {
                        samples[i].toDouble()
                    } else {
                        0.0
                    }

                val window =
                    0.5 -
                        0.5 *
                        cos(
                            2.0 *
                                PI *
                                i /
                                (
                                    FFT_SIZE - 1
                                )
                        )

                real[i] =
                    sample * window

                imaginary[i] = 0.0
            }

            fft(
                real,
                imaginary
            )

            val output =
                FloatArray(BAND_COUNT)

            val maxFrequency =
                SAMPLE_RATE / 2.0

            for (
                band in 0 until BAND_COUNT
            ) {

                val normalizedStart =
                    band.toDouble() /
                        BAND_COUNT

                val normalizedEnd =
                    (band + 1).toDouble() /
                        BAND_COUNT

                val minFrequency =
                    40.0 *
                        Math.pow(
                            maxFrequency / 40.0,
                            normalizedStart
                        )

                val maxBandFrequency =
                    40.0 *
                        Math.pow(
                            maxFrequency / 40.0,
                            normalizedEnd
                        )

                val minBin =
                    maxOf(
                        1,
                        (
                            minFrequency *
                                FFT_SIZE /
                                SAMPLE_RATE
                        ).toInt()
                    )

                val maxBin =
                    minOf(
                        FFT_SIZE / 2 - 1,
                        (
                            maxBandFrequency *
                                FFT_SIZE /
                                SAMPLE_RATE
                        ).toInt()
                    )

                var energy = 0.0
                var count = 0

                if (maxBin >= minBin) {

                    for (
                        bin in minBin..maxBin
                    ) {

                        val magnitude =
                            sqrt(
                                real[bin] *
                                    real[bin] +
                                    imaginary[bin] *
                                    imaginary[bin]
                            )

                        energy += magnitude
                        count++
                    }
                }

                val average =
                    if (count > 0) {
                        energy / count
                    } else {
                        0.0
                    }

                /*
                 * Convert the FFT magnitude into
                 * a stable 0..1 visualiser value.
                 *
                 * The logarithmic compression makes
                 * quieter music visible while still
                 * allowing loud bass hits to produce
                 * strong reactions.
                 */
                val compressed =
                    if (average > 0.0) {
                        (
                            Math.log10(
                                1.0 +
                                    average / 250.0
                            ) / 2.0
                        )
                    } else {
                        0.0
                    }

                output[band] =
                    compressed
                        .coerceIn(
                            0.0,
                            1.0
                        )
                        .toFloat()
            }

            smooth(output)

            _bands.value = output

            delay(8L)
        }
    }

    private fun smooth(
        values: FloatArray
    ) {

        val previous =
            _bands.value

        for (i in values.indices) {

            val old =
                previous.getOrElse(i) {
                    0f
                }

            /*
             * Attack is deliberately faster than
             * release so bass hits are visible,
             * while the visualiser doesn't flicker.
             */
            val attack =
                0.62f

            val release =
                0.30f

            values[i] =
                if (values[i] > old) {

                    old * (1f - attack) +
                        values[i] * attack

                } else {

                    old * (1f - release) +
                        values[i] * release
                }
        }
    }

    private fun fft(
        real: DoubleArray,
        imaginary: DoubleArray
    ) {

        val n =
            real.size

        var j = 0

        for (i in 1 until n) {

            var bit =
                n shr 1

            while (
                j and bit != 0
            ) {

                j =
                    j xor bit

                bit =
                    bit shr 1
            }

            j =
                j xor bit

            if (i < j) {

                val realTemp =
                    real[i]

                real[i] =
                    real[j]

                real[j] =
                    realTemp

                val imaginaryTemp =
                    imaginary[i]

                imaginary[i] =
                    imaginary[j]

                imaginary[j] =
                    imaginaryTemp
            }
        }

        var length = 2

        while (length <= n) {

            val angle =
                -2.0 *
                    PI /
                    length

            val wLengthReal =
                cos(angle)

            val wLengthImaginary =
                sin(angle)

            var i = 0

            while (i < n) {

                var wReal = 1.0
                var wImaginary = 0.0

                val half =
                    length shr 1

                for (
                    k in 0 until half
                ) {

                    val even =
                        i + k

                    val odd =
                        even + half

                    val oddReal =
                        real[odd] *
                            wReal -
                            imaginary[odd] *
                            wImaginary

                    val oddImaginary =
                        real[odd] *
                            wImaginary +
                            imaginary[odd] *
                            wReal

                    real[odd] =
                        real[even] -
                            oddReal

                    imaginary[odd] =
                        imaginary[even] -
                            oddImaginary

                    real[even] +=
                        oddReal

                    imaginary[even] +=
                        oddImaginary

                    val nextWReal =
                        wReal *
                            wLengthReal -
                            wImaginary *
                            wLengthImaginary

                    wImaginary =
                        wReal *
                            wLengthImaginary +
                            wImaginary *
                            wLengthReal

                    wReal =
                        nextWReal
                }

                i += length
            }

            length =
                length shl 1
        }
    }

    @Synchronized
    fun stop() {

        _isCapturing.value = false

        captureJob?.cancel()
        captureJob = null

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null

        try {
            projection?.stop()
        } catch (_: Exception) {
        }

        projection = null

        scope?.cancel()
        scope = null

        _bands.value =
            FloatArray(BAND_COUNT)
    }

    fun setBands(
        values: FloatArray
    ) {

        val output =
            FloatArray(BAND_COUNT)

        val count =
            minOf(
                values.size,
                BAND_COUNT
            )

        for (i in 0 until count) {

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
