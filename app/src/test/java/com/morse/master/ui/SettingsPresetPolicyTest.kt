package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsPresetPolicyTest {
    @Test
    fun `detects when the 30 WPM beginner preset is active`() {
        assertThat(
            isThirtyWpmBeginnerPresetActive(
                DitDaSettings(
                    characterWpm = 30,
                    effectiveWpm = 10,
                    toneHz = 650
                )
            )
        ).isTrue()

        assertThat(
            isThirtyWpmBeginnerPresetActive(
                DitDaSettings(
                    characterWpm = 30,
                    effectiveWpm = 9,
                    toneHz = 650
                )
            )
        ).isFalse()
    }
}
