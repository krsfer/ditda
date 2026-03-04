package com.morse.master.audio

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.math.sin

private const val DEFAULT_RAMP_MS = 5.0

internal fun rampSamplesForPulse(
    numSamples: Int,
    sampleRate: Int,
    rampMs: Double = DEFAULT_RAMP_MS
): Int {
    if (numSamples <= 0) return 0
    val nominalRampSamples = (sampleRate * (rampMs / 1000.0)).toInt().coerceAtLeast(1)
    val maxRampForSustain = ((numSamples - 2) / 2).coerceAtLeast(1)
    return nominalRampSamples.coerceAtMost(maxRampForSustain)
}

class MorseToneGenerator {
    private val nominalRampTableBySampleRate = ConcurrentHashMap<Int, DoubleArray>()

    fun generatePulse(
        durationMs: Int,
        sampleRate: Int = 44100,
        frequencyHz: Int = 600,
        gain: Double = 0.25
    ): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        if (numSamples <= 0) return ShortArray(0)

        val nominalRampTable = nominalRampTableBySampleRate.getOrPut(sampleRate) {
            buildRampTable((sampleRate * (DEFAULT_RAMP_MS / 1000.0)).toInt().coerceAtLeast(1))
        }
        val rampSamples = rampSamplesForPulse(
            numSamples = numSamples,
            sampleRate = sampleRate
        )
        val rampLastIndex = rampSamples - 1
        val nominalRampLastIndex = nominalRampTable.size - 1

        return ShortArray(numSamples) { i ->
            val angle = 2.0 * Math.PI * i * frequencyHz / sampleRate
            val startGain = if (i < rampSamples) {
                rampValueAt(
                    position = i,
                    rampLastIndex = rampLastIndex,
                    nominalRamp = nominalRampTable,
                    nominalRampLastIndex = nominalRampLastIndex
                )
            } else {
                1.0
            }
            val samplesFromEnd = numSamples - 1 - i
            val endGain = if (samplesFromEnd < rampSamples) {
                rampValueAt(
                    position = samplesFromEnd,
                    rampLastIndex = rampLastIndex,
                    nominalRamp = nominalRampTable,
                    nominalRampLastIndex = nominalRampLastIndex
                )
            } else {
                1.0
            }
            val volume = min(startGain, endGain)
            val sample = sin(angle) * volume * gain
            val clampedSample = sample.coerceIn(-1.0, 1.0)
            (clampedSample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun rampValueAt(
        position: Int,
        rampLastIndex: Int,
        nominalRamp: DoubleArray,
        nominalRampLastIndex: Int
    ): Double {
        if (rampLastIndex <= 0 || nominalRampLastIndex <= 0) return 0.0
        val normalizedPosition = position.toDouble() / rampLastIndex.toDouble()
        val nominalIndex = (normalizedPosition * nominalRampLastIndex)
            .toInt()
            .coerceIn(0, nominalRampLastIndex)
        return nominalRamp[nominalIndex]
    }

    private fun buildRampTable(size: Int): DoubleArray {
        if (size <= 1) return doubleArrayOf(0.0)
        val denominator = (size - 1).toDouble()
        return DoubleArray(size) { index ->
            val t = index / denominator
            sin((Math.PI / 2.0) * t).let { it * it }
        }
    }
}
