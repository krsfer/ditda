package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import com.morse.master.ui.DitDaSettings
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
}
