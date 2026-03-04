package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test

class MorseToneGeneratorTest {
    @Test
    fun `applies start and end smoothing`() {
        val pcm = MorseToneGenerator().generatePulse(durationMs = 120, sampleRate = 44100)
        assertThat(abs(pcm.first().toInt())).isLessThan(200)
        assertThat(abs(pcm.last().toInt())).isLessThan(200)
        assertThat(pcm.size).isEqualTo((44100 * 0.120).toInt())
    }

    @Test
    fun `ends exactly at silence for low sample rates`() {
        val pcm = MorseToneGenerator().generatePulse(durationMs = 100, sampleRate = 1000, frequencyHz = 600)
        assertThat(abs(pcm.first().toInt())).isEqualTo(0)
        assertThat(abs(pcm.last().toInt())).isEqualTo(0)
    }

    @Test
    fun `caps ramp samples to preserve sustain core on short pulses`() {
        assertThat(rampSamplesForPulse(numSamples = 8, sampleRate = 1000)).isEqualTo(3)
        assertThat(rampSamplesForPulse(numSamples = 40, sampleRate = 1000)).isEqualTo(5)
    }
}
