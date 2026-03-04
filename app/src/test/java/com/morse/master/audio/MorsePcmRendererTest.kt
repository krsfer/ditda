package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import com.morse.master.ui.DitDaSettings
import kotlin.math.abs
import org.junit.Test

class MorsePcmRendererTest {
    @Test
    fun `builds deterministic PCM and gap samples from Morse plan`() {
        val renderer = MorsePcmRenderer(
            sampleRate = 1000,
            toneGenerator = MorseToneGenerator()
        )

        val pcm = renderer.render(
            segments = listOf(
                MorseSegment.Tone(100),
                MorseSegment.Gap(50),
                MorseSegment.Tone(200)
            ),
            settings = DitDaSettings(characterWpm = 25, effectiveWpm = 8, toneHz = 600, darkMode = true)
        )

        assertThat(pcm.size).isEqualTo(350)
        assertThat(pcm[120].toInt()).isEqualTo(0)
    }

    @Test
    fun `applies mild gain compensation for high-speed settings`() {
        val renderer = MorsePcmRenderer(
            sampleRate = 8_000,
            toneGenerator = MorseToneGenerator()
        )
        val segments = listOf(MorseSegment.Tone(durationMs = 40))

        val baseline = renderer.render(
            segments = segments,
            settings = DitDaSettings(characterWpm = 25, effectiveWpm = 8, toneHz = 650, darkMode = true)
        )
        val highSpeed = renderer.render(
            segments = segments,
            settings = DitDaSettings(characterWpm = 30, effectiveWpm = 10, toneHz = 650, darkMode = true)
        )

        val baselinePeak = baseline.maxOf { abs(it.toInt()) }
        val highSpeedPeak = highSpeed.maxOf { abs(it.toInt()) }
        assertThat(highSpeedPeak).isGreaterThan(baselinePeak)
    }
}
