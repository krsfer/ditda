package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import com.morse.master.ui.DitDaSettings
import org.junit.Test

class MorseSymbolPlannerTimingTest {
    @Test
    fun `uses character WPM for dot duration`() {
        val planner = MorseSymbolPlanner()

        val slow = planner.planFor('E', DitDaSettings(characterWpm = 10, effectiveWpm = 8, toneHz = 600, darkMode = true))
        val fast = planner.planFor('E', DitDaSettings(characterWpm = 30, effectiveWpm = 8, toneHz = 600, darkMode = true))

        val slowDot = (slow.single() as MorseSegment.Tone).durationMs
        val fastDot = (fast.single() as MorseSegment.Tone).durationMs

        assertThat(slowDot).isGreaterThan(fastDot)
        assertThat(slowDot).isEqualTo(120)
        assertThat(fastDot).isEqualTo(40)
    }
}
