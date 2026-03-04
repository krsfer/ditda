package com.morse.master.audio

import com.morse.master.ui.DitDaSettings

class MorsePcmRenderer(
    private val sampleRate: Int = 44100,
    private val toneGenerator: MorseToneGenerator = MorseToneGenerator()
) {
    private companion object {
        private const val HIGH_SPEED_CHARACTER_WPM = 30
        private const val BEGINNER_HIGH_SPEED_GAIN_COMPENSATION = 1.05
    }

    fun render(segments: List<MorseSegment>, settings: DitDaSettings): ShortArray {
        val rampMs = rampMsForCharacterWpm(settings.characterWpm)
        val toneGain = if (settings.characterWpm >= HIGH_SPEED_CHARACTER_WPM) {
            BEGINNER_HIGH_SPEED_GAIN_COMPENSATION * 0.25
        } else {
            0.25
        }
        val chunks = segments.map { segment ->
            when (segment) {
                is MorseSegment.Tone -> toneGenerator.generatePulse(
                    durationMs = segment.durationMs,
                    sampleRate = sampleRate,
                    frequencyHz = settings.toneHz,
                    gain = toneGain,
                    rampMs = rampMs
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
