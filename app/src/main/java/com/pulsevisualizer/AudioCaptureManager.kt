package com.pulsevisualizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AudioCaptureManager {

    private const val BAND_COUNT = 64

    private val _bands =
        MutableStateFlow(FloatArray(BAND_COUNT))

    val bands: StateFlow<FloatArray> =
        _bands

    private val _isCapturing =
        MutableStateFlow(false)

    val isCapturing: StateFlow<Boolean> =
        _isCapturing

    fun initialize(
        context: android.content.Context
    ) {
        // Intentionally empty.
        //
        // This version does NOT use:
        // - MediaProjection
        // - AudioPlaybackCaptureConfiguration
        // - AudioRecord
        // - RECORD_AUDIO
        //
        // Therefore Android will never ask the user
        // to share/capture the screen.
    }

    fun start(
        resultCode: Int,
        data: android.content.Intent
    ): Boolean {
        return false
    }

    fun stop() {
        _isCapturing.value = false
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
                values[i].coerceIn(
                    0f,
                    1f
                )
        }

        _bands.value = output
    }
}
