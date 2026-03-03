package com.morse.master.audio

import com.google.common.truth.Truth.assertThat
import com.morse.master.ui.DitDaSettings
import org.junit.Test

class MorseSymbolPlannerTest {
    @Test
    fun `builds expected K sequence with timing-derived durations`() {
        val planner = MorseSymbolPlanner()
        val settings = DitDaSettings(characterWpm = 25, effectiveWpm = 8, toneHz = 600, darkMode = true)

        val plan = planner.planFor('K', settings)

        assertThat(plan).containsExactly(
            MorseSegment.Tone(144),
            MorseSegment.Gap(48),
            MorseSegment.Tone(48),
            MorseSegment.Gap(48),
            MorseSegment.Tone(144)
        ).inOrder()
    }

    @Test
    fun `returns empty plan for unsupported character`() {
        val planner = MorseSymbolPlanner()
        val plan = planner.planFor('*', DitDaSettings())
        assertThat(plan).isEmpty()
    }

    @Test
    fun `supports digits and punctuation`() {
        val planner = MorseSymbolPlanner()
        val settings = DitDaSettings()

        assertThat(planner.planFor('5', settings)).isNotEmpty()
        assertThat(planner.planFor('?', settings)).isNotEmpty()
        assertThat(planner.planFor('@', settings)).isNotEmpty()
    }
}
