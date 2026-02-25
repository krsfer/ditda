package com.morse.master.audio

import com.morse.master.ui.DitDaSettings

class MorsePcmRenderer(
    private val sampleRate: Int = 44100,
    private val toneGenerator: MorseToneGenerator = MorseToneGenerator()
) {
    fun render(segments: List<MorseSegment>, settings: DitDaSettings): ShortArray {
        val chunks = segments.map { segment ->
            when (segment) {
                is MorseSegment.Tone -> toneGenerator.generatePulse(
                    durationMs = segment.durationMs,
                    sampleRate = sampleRate,
                    frequencyHz = settings.toneHz
                )

                is MorseSegment.Gap -> {
                    val silenceSamples = (sampleRate * (segment.durationMs / 1000.0)).toInt()
                    ShortArray(silenceSamples)
                }
            }
        }

        val totalSamples = chunks.sumOf { it.size }
        val result = ShortArray(totalSamples)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }
}
