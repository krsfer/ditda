package com.morse.master.audio

import kotlin.math.min
import kotlin.math.sin

class MorseToneGenerator {
    fun generatePulse(
        durationMs: Int,
        sampleRate: Int = 44100,
        frequencyHz: Int = 600,
        gain: Double = 0.25
    ): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        if (numSamples <= 0) return ShortArray(0)

        val rampSamples = (sampleRate * 0.005).toInt()
            .coerceAtLeast(1)
            .coerceAtMost(numSamples)
        val rampDenominator = (rampSamples - 1).coerceAtLeast(1).toDouble()

        return ShortArray(numSamples) { i ->
            val angle = 2.0 * Math.PI * i * frequencyHz / sampleRate
            val startGain = if (i < rampSamples) {
                val t = i / rampDenominator
                sin((Math.PI / 2.0) * t).let { it * it }
            } else {
                1.0
            }
            val samplesFromEnd = numSamples - 1 - i
            val endGain = if (samplesFromEnd < rampSamples) {
                val t = samplesFromEnd / rampDenominator
                sin((Math.PI / 2.0) * t).let { it * it }
            } else {
                1.0
            }
            val volume = min(startGain, endGain)
            (sin(angle) * Short.MAX_VALUE * volume * gain).toInt().toShort()
        }
    }
}
