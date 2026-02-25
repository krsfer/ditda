package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MorseToneGeneratorFrequencyTest {
    @Test
    fun `supports configurable tone frequency`() {
        val generator = MorseToneGenerator()

        val pulse600 = generator.generatePulse(durationMs = 60, sampleRate = 44100, frequencyHz = 600)
        val pulse800 = generator.generatePulse(durationMs = 60, sampleRate = 44100, frequencyHz = 800)

        assertThat(pulse800.contentEquals(pulse600)).isFalse()
    }
}
