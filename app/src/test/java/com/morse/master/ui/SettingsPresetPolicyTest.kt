package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsPresetPolicyTest {
    @Test
    fun `detects when the 30 WPM expert preset is active`() {
        assertThat(
            isThirtyWpmExpertPresetActive(
                DitDaSettings(
                    characterWpm = 30,
                    effectiveWpm = 8,
                    toneHz = 650
                )
            )
        ).isTrue()

        assertThat(
            isThirtyWpmExpertPresetActive(
                DitDaSettings(
                    characterWpm = 30,
                    effectiveWpm = 9,
                    toneHz = 650
                )
            )
        ).isFalse()
    }

    @Test
    fun `classifies expert path phases from speed pair`() {
        assertThat(
            expertPathPhase(
                DitDaSettings(characterWpm = 30, effectiveWpm = 8, toneHz = 650)
            )
        ).isEqualTo(ExpertPathPhase.LEARNING)

        assertThat(
            expertPathPhase(
                DitDaSettings(characterWpm = 30, effectiveWpm = 22, toneHz = 650)
            )
        ).isEqualTo(ExpertPathPhase.CLOSING)

        assertThat(
            expertPathPhase(
                DitDaSettings(characterWpm = 30, effectiveWpm = 30, toneHz = 650)
            )
        ).isEqualTo(ExpertPathPhase.MASTERY)

        assertThat(
            expertPathPhase(
                DitDaSettings(characterWpm = 40, effectiveWpm = 40, toneHz = 650)
            )
        ).isEqualTo(ExpertPathPhase.ULTRA)

        assertThat(
            expertPathPhase(
                DitDaSettings(characterWpm = 24, effectiveWpm = 12, toneHz = 600)
            )
        ).isEqualTo(ExpertPathPhase.CUSTOM)
    }
}
