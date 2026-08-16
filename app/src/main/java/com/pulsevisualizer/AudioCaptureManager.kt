package com.pulsevisualizer

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

object AudioCaptureManager {

    private const val BAND_COUNT = 64
    private const val FFT_SIZE = 2048
    private const val SAMPLE_RATE = 44100

    private val _bands =
        MutableStateFlow(FloatArray(BAND_COUNT))

    val bands: StateFlow<FloatArray> = _bands

    private val _isCapturing =
        MutableStateFlow(false)

    val isCapturing: StateFlow<Boolean> =
        _isCapturing

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var mediaProjection: MediaProjection? = null

    private var smoothedBands =
        FloatArray(BAND_COUNT)

    private var lastBass = 0f

    fun initialize(context: Context) {
        // Kept for compatibility with the rest of the project.
    }

    fun start(
        resultCode: Int,
        data: Intent
    ): Boolean {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        stop()

        return try {

            val projectionManager =
                android.media.projection.MediaProjectionManagerHolder
        } catch (_: Throwable) {
            startInternal(resultCode, data)
        }
    }

    private fun startInternal(
        resultCode: Int,
        data: Intent
    ): Boolean {

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

            val audioConfig =
                AudioPlaybackCaptureConfiguration
                    .Builder(projection)
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

            if (minBuffer <= 0) {
                return false
            }

            val bufferSize =
                maxOf(
                    minBuffer * 4,
                    FFT_SIZE * 4
                )

            val record =
                AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(
                        audioConfig
                    )
                    .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            audioRecord = record

            smoothedBands =
                FloatArray(BAND_COUNT)

            lastBass = 0f

            record.startRecording()

            _isCapturing.value = true

            captureJob =
                CoroutineScope(Dispatchers.Default).launch {

                    val samples =
                        ShortArray(FFT_SIZE)

                    while (
                        isActive &&
                        _isCapturing.value
                    ) {

                        var totalRead = 0

                        while (
                            totalRead < FFT_SIZE &&
                            isActive &&
                            _isCapturing.value
                        ) {

                            val read =
                                record.read(
                                    samples,
                                    totalRead,
                                    FFT_SIZE -
                                        totalRead,
                                    AudioRecord.READ_BLOCKING
                                )

                            if (read <= 0) {
                                break
                            }

                            totalRead += read
                        }

                        if (totalRead == FFT_SIZE) {
                            processAudio(samples)
                        }
                    }
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

    private fun processAudio(
        samples: ShortArray
    ) {

        val real =
            DoubleArray(FFT_SIZE)

        val imag =
            DoubleArray(FFT_SIZE)

        for (i in 0 until FFT_SIZE) {

            val sample =
                samples[i] / 32768.0

            // Hann window
            val window =
                0.5 *
                    (
                        1.0 -
                            cos(
                                2.0 *
                                    PI *
                                    i /
                                    (FFT_SIZE - 1)
                            )
                        )

            real[i] =
                sample * window

            imag[i] = 0.0
        }

        fft(
            real,
            imag
        )

        val magnitudes =
            DoubleArray(
                FFT_SIZE / 2
            )

        for (i in magnitudes.indices) {

            val magnitude =
                sqrt(
                    real[i] * real[i] +
                        imag[i] * imag[i]
                )

            magnitudes[i] =
                magnitude /
                    (FFT_SIZE / 2)
        }

        val output =
            FloatArray(BAND_COUNT)

        val nyquist =
            SAMPLE_RATE / 2.0

        for (band in 0 until BAND_COUNT) {

            val lowFrequency =
                20.0 *
                    Math.pow(
                        nyquist / 20.0,
                        band.toDouble() /
                            BAND_COUNT
                    )

            val highFrequency =
                20.0 *
                    Math.pow(
                        nyquist / 20.0,
                        (band + 1).toDouble() /
                            BAND_COUNT
                    )

            val lowBin =
                (
                    lowFrequency /
                        SAMPLE_RATE *
                        FFT_SIZE
                    ).toInt()
                        .coerceIn(
                            1,
                            magnitudes.lastIndex
                        )

            val highBin =
                (
                    highFrequency /
                        SAMPLE_RATE *
                        FFT_SIZE
                    ).toInt()
                        .coerceIn(
                            lowBin,
                            magnitudes.lastIndex
                        )

            var sum = 0.0
            var count = 0

            for (bin in lowBin..highBin) {

                sum += magnitudes[bin]
                count++
            }

            val average =
                if (count > 0) {
                    sum / count
                } else {
                    0.0
                }

            // Log-style amplification so quiet music
            // still produces useful visual movement.
            val amplified =
                (
                    average * 38.0
                ).coerceIn(
                    0.0,
                    1.0
                )

            output[band] =
                amplified.toFloat()
        }

        // Additional bass energy measurement.
        var bassSum = 0.0
        var bassCount = 0

        for (bin in 1 until magnitudes.size) {

            val frequency =
                bin.toDouble() *
                    SAMPLE_RATE /
                    FFT_SIZE

            if (
                frequency >= 30.0 &&
                frequency <= 180.0
            ) {

                bassSum +=
                    magnitudes[bin]

                bassCount++
            }
        }

        val bass =
            if (bassCount > 0) {
                (
                    bassSum /
                        bassCount *
                        65.0
                    ).coerceIn(
                        0.0,
                        1.0
                    ).toFloat()
            } else {
                0f
            }

        // Smooth the spectrum.
        for (i in output.indices) {

            val previous =
                smoothedBands[i]

            val target =
                output[i]

            val speed =
                if (target > previous) {
                    0.55f
                } else {
                    0.18f
                }

            smoothedBands[i] =
                previous +
                    (
                        target -
                            previous
                        ) *
                    speed
        }

        val bassSmoothed =
            lastBass +
                (
                    bass -
                        lastBass
                    ) *
                    if (bass > lastBass) {
                        0.65f
                    } else {
                        0.20f
                    }

        lastBass =
            bassSmoothed

        _bands.value =
            smoothedBands.copyOf()
    }

    private fun fft(
        real: DoubleArray,
        imag: DoubleArray
    ) {

        val n = real.size

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

                val imagTemp =
                    imag[i]

                imag[i] =
                    imag[j]

                imag[j] =
                    imagTemp
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

            val wLengthImag =
                kotlin.math.sin(angle)

            var i = 0

            while (i < n) {

                var wReal = 1.0
                var wImag = 0.0

                for (
                    k in 0 until length / 2
                ) {

                    val even =
                        i + k

                    val odd =
                        i +
                            k +
                            length / 2

                    val oddReal =
                        real[odd] *
                            wReal -
                            imag[odd] *
                            wImag

                    val oddImag =
                        real[odd] *
                            wImag +
                            imag[odd] *
                            wReal

                    real[odd] =
                        real[even] -
                            oddReal

                    imag[odd] =
                        imag[even] -
                            oddImag

                    real[even] +=
                        oddReal

                    imag[even] +=
                        oddImag

                    val nextWReal =
                        wReal *
                            wLengthReal -
                            wImag *
                            wLengthImag

                    wImag =
                        wReal *
                            wLengthImag +
                            wImag *
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

    fun stop() {

        _isCapturing.value =
            false

        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }

        audioRecord = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null

        smoothedBands =
            FloatArray(BAND_COUNT)

        lastBass = 0f

        _bands.value =
            FloatArray(BAND_COUNT)
    }
}


/*
 * Small application-context holder so the singleton can create
 * AudioPlaybackCaptureConfiguration without changing MainActivity.
 */
object AppContextHolder {

    var context: android.content.Context? = null
}
