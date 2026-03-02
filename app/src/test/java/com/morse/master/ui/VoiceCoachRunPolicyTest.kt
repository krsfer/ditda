package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import com.morse.master.coach.CoachState
import org.junit.Test

class VoiceCoachRunPolicyTest {
    @Test
    fun `does not run command listener outside practice tab`() {
        assertThat(
            shouldRunVoiceCommandListener(
                activeTab = AppTab.SETTINGS,
                handsFreeEnabled = true,
                micPermissionGranted = true,
                coachState = CoachState.IDLE,
                voiceControlArmed = true
            )
        ).isFalse()
    }

    @Test
    fun `does not run command listener when voice control is not armed`() {
        assertThat(
            shouldRunVoiceCommandListener(
                activeTab = AppTab.PRACTICE,
                handsFreeEnabled = true,
                micPermissionGranted = true,
                coachState = CoachState.IDLE,
                voiceControlArmed = false
            )
        ).isFalse()
    }

    @Test
    fun `runs round loop only on practice when round is active`() {
        assertThat(
            shouldRunVoiceRoundLoop(
                activeTab = AppTab.PRACTICE,
                handsFreeEnabled = true,
                micPermissionGranted = true,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isTrue()

        assertThat(
            shouldRunVoiceRoundLoop(
                activeTab = AppTab.SETTINGS,
                handsFreeEnabled = true,
                micPermissionGranted = true,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isFalse()
    }
}
