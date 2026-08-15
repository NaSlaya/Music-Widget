package com.pulsevisualizer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object AudioCaptureManager {

    private const val SAMPLE_RATE = 44100
    private const val FFT_SIZE = 2048
    private const val BAND_COUNT = 48

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null

    private var captureThread: Thread? = null
    private var running = false

    private lateinit var appContext: Context

    private val _bands =
        MutableStateFlow(FloatArray(BAND_COUNT))

    val bands: StateFlow<FloatArray> = _bands

    private val _isCapturing =
        MutableStateFlow(false)

    val isCapturing: StateFlow<Boolean> = _isCapturing

    private val fftReal =
        DoubleArray(FFT_SIZE)

    private val fftImag =
        DoubleArray(FFT_SIZE)

    private val fftMagnitude =
        DoubleArray(FFT_SIZE / 2)

    private val window =
        DoubleArray(FFT_SIZE)

    private val smoothed =
        FloatArray(BAND_COUNT)

    private val peaks =
        FloatArray(BAND_COUNT)

    init {

        for (i in 0 until FFT_SIZE) {

            window[i] =
                0.5 -
                    0.5 *
                    cos(
                        2.0 * PI * i /
                            (FFT_SIZE - 1)
                    )
        }
    }

    fun initialize(context: Context) {

        appContext =
            context.applicationContext
    }

    fun hasRecordPermission(): Boolean {

        if (!::appContext.isInitialized) {
            return false
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start(
        resultCode: Int,
        data: Intent
    ): Boolean {

        if (!::appContext.isInitialized) {
            return false
        }

        if (!hasRecordPermission()) {
            return false
        }

        stop()

        try {

            val manager =
                appContext.getSystemService(
                    android.media.projection.MediaProjectionManager::class.java
                )

            val mediaProjection =
                manager.getMediaProjection(
                    resultCode,
                    data
                )

            projection = mediaProjection

            mediaProjection.registerCallback(
                object : MediaProjection.Callback() {

                    override fun onStop() {
                        stop()
                    }
                },
                null
            )

            val captureConfig =
                AudioPlaybackCaptureConfiguration.Builder(
                    mediaProjection
                )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_GAME
                    )
                    .build()

            val audioFormat =
                AudioFormat.Builder()
                    .setEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(
                        AudioFormat.CHANNEL_IN_MONO
                    )
                    .build()

            val minBuffer =
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            val bufferSize =
                max(
                    minBuffer,
                    FFT_SIZE * 4
                )

            val audioRecord =
                AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(
                        captureConfig
                    )
                    .build()

            if (
                audioRecord.state !=
                AudioRecord.STATE_INITIALIZED
            ) {

                audioRecord.release()
                projection?.stop()
                projection = null

                return false
            }

            recorder = audioRecord

            audioRecord.startRecording()

            if (
                audioRecord.recordingState !=
                AudioRecord.RECORDSTATE_RECORDING
            ) {

                audioRecord.release()
                recorder = null

                projection?.stop()
                projection = null

                return false
            }

            running = true
            _isCapturing.value = true

            captureThread =
                Thread {

                    captureLoop(audioRecord)

                }.apply {

                    name = "PulseAudioCapture"
                    start()
                }

            true

        } catch (_: SecurityException) {

            stop()
            false

        } catch (_: Exception) {

            stop()
            false
        }
    }

    fun stop() {

        running = false

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

        captureThread = null

        _isCapturing.value = false

        synchronized(smoothed) {

            smoothed.fill(0f)
            peaks.fill(0f)

            _bands.value =
                FloatArray(BAND_COUNT)
        }
    }

    private fun captureLoop(
        audioRecord: AudioRecord
    ) {

        val samples =
            ShortArray(FFT_SIZE)

        while (running) {

            val read =
                try {

                    audioRecord.read(
                        samples,
                        0,
                        samples.size,
                        AudioRecord.READ_BLOCKING
                    )

                } catch (_: Exception) {

                    -1
                }

            if (read <= 0) {
                continue
            }

            if (read < FFT_SIZE) {

                for (i in read until FFT_SIZE) {
                    samples[i] = 0
                }
            }

            analyse(samples)
        }
    }

    private fun analyse(
        samples: ShortArray
    ) {

        for (i in 0 until FFT_SIZE) {

            fftReal[i] =
                samples[i].toDouble() /
                    32768.0 *
                    window[i]

            fftImag[i] = 0.0
        }

        fft()

        for (i in 0 until FFT_SIZE / 2) {

            val real =
                fftReal[i]

            val imag =
                fftImag[i]

            fftMagnitude[i] =
                sqrt(
                    real * real +
                        imag * imag
                )
        }

        val result =
            FloatArray(BAND_COUNT)

        val minFrequency = 30.0
        val maxFrequency =
            SAMPLE_RATE.toDouble() / 2.0

        for (band in 0 until BAND_COUNT) {

            val low =
                minFrequency *
                    Math.pow(
                        maxFrequency /
                            minFrequency,
                        band.toDouble() /
                            BAND_COUNT
                    )

            val high =
                minFrequency *
                    Math.pow(
                        maxFrequency /
                            minFrequency,
                        (band + 1).toDouble() /
                            BAND_COUNT
                    )

            val lowBin =
                max(
                    1,
                    (
                        low *
                            FFT_SIZE /
                            SAMPLE_RATE
                        ).toInt()
                )

            val highBin =
                min(
                    FFT_SIZE / 2 - 1,
                    (
                        high *
                            FFT_SIZE /
                            SAMPLE_RATE
                        ).toInt()
                )

            var total = 0.0
            var count = 0

            for (bin in lowBin..max(
                lowBin,
                highBin
            )) {

                total += fftMagnitude[bin]
                count++
            }

            val average =
                if (count > 0) {
                    total / count
                } else {
                    0.0
                }

            val db =
                20.0 *
                    log10(
                        max(
                            average,
                            0.000001
                        )
                    )

            val normalized =
                (
                    (db + 55.0) /
                        55.0
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

            result[band] =
                normalized.toFloat()
        }

        synchronized(smoothed) {

            for (i in 0 until BAND_COUNT) {

                val target =
                    result[i]

                /*
                 * Fast attack.
                 *
                 * The visualiser responds immediately
                 * to drums and transients.
                 */
                val attack =
                    if (target > smoothed[i]) {
                        0.42f
                    } else {
                        0.12f
                    }

                smoothed[i] +=
                    (
                        target -
                            smoothed[i]
                        ) * attack

                /*
                 * Peak indicator with decay.
                 */
                if (
                    smoothed[i] >
                    peaks[i]
                ) {

                    peaks[i] =
                        smoothed[i]

                } else {

                    peaks[i] *=
                        0.96f
                }

                result[i] =
                    (
                        smoothed[i] * 0.82f +
                            peaks[i] * 0.18f
                        )
                            .coerceIn(
                                0f,
                                1f
                            )
            }

            _bands.value =
                result.copyOf()
        }
    }

    private fun fft() {

        var j = 0

        for (i in 1 until FFT_SIZE) {

            var bit =
                FFT_SIZE shr 1

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

                var temp =
                    fftReal[i]

                fftReal[i] =
                    fftReal[j]

                fftReal[j] =
                    temp

                temp =
                    fftImag[i]

                fftImag[i] =
                    fftImag[j]

                fftImag[j] =
                    temp
            }
        }

        var length = 2

        while (
            length <= FFT_SIZE
        ) {

            val angle =
                -2.0 *
                    PI /
                    length

            val wLenReal =
                cos(angle)

            val wLenImag =
                sin(angle)

            var i = 0

            while (
                i < FFT_SIZE
            ) {

                var wReal = 1.0
                var wImag = 0.0

                val half =
                    length / 2

                for (k in 0 until half) {

                    val evenIndex =
                        i + k

                    val oddIndex =
                        i + k + half

                    val oddReal =
                        fftReal[oddIndex]

                    val oddImag =
                        fftImag[oddIndex]

                    val tempReal =
                        wReal * oddReal -
                            wImag * oddImag

                    val tempImag =
                        wReal * oddImag +
                            wImag * oddReal

                    val evenReal =
                        fftReal[evenIndex]

                    val evenImag =
                        fftImag[evenIndex]

                    fftReal[evenIndex] =
                        evenReal +
                            tempReal

                    fftImag[evenIndex] =
                        evenImag +
                            tempImag

                    fftReal[oddIndex] =
                        evenReal -
                            tempReal

                    fftImag[oddIndex] =
                        evenImag -
                            tempImag

                    val nextWReal =
                        wReal *
                            wLenReal -
                            wImag *
                            wLenImag

                    wImag =
                        wReal *
                            wLenImag +
                            wImag *
                            wLenReal

                    wReal =
                        nextWReal
                }

                i += length
            }

            length =
                length shl 1
        }
    }
}
