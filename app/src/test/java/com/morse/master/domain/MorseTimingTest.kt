package com.morse.master.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MorseTimingTest {
    @Test
    fun `computes standard dit and dah for 25wpm`() {
        val t = MorseTiming(characterWpm = 25, effectiveWpm = 8)
        assertThat(t.dotMs).isEqualTo(48)
        assertThat(t.dashMs).isEqualTo(144)
        assertThat(t.intraSymbolGapMs).isEqualTo(48)
    }
}
