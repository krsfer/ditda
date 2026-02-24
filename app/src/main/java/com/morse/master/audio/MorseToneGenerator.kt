package com.morse.master.audio

import kotlin.math.cos
import kotlin.math.sin

class MorseToneGenerator {
    fun generatePulse(durationMs: Int, sampleRate: Int = 44100): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val rampSamples = (sampleRate * 0.005).toInt()

        return ShortArray(numSamples) { i ->
            val angle = 2.0 * Math.PI * i * 600.0 / sampleRate
            val volume = when {
                i < rampSamples -> 0.5 * (1 - cos(Math.PI * i / rampSamples))
                i > numSamples - rampSamples -> 0.5 * (1 - cos(Math.PI * (numSamples - i) / rampSamples))
                else -> 1.0
            }
            (sin(angle) * Short.MAX_VALUE * volume).toInt().toShort()
        }
    }
}
