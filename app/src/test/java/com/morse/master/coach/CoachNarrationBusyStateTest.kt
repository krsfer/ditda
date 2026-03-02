package com.morse.master.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoachNarrationBusyStateTest {
    @Test
    fun `treats queued utterances as busy before playback starts`() {
        val busyState = CoachNarrationBusyState()

        busyState.onMessageQueued()
        assertThat(busyState.isBusy()).isTrue()

        busyState.onMessageDequeuedForPlayback()
        busyState.onUtteranceSubmitted()
        assertThat(busyState.isBusy()).isTrue()

        busyState.onUtteranceFinished()
        assertThat(busyState.isBusy()).isFalse()
    }

    @Test
    fun `clear resets busy state counters`() {
        val busyState = CoachNarrationBusyState()
        busyState.onMessageQueued()
        busyState.onMessageDequeuedForPlayback()
        busyState.onUtteranceSubmitted()

        busyState.clear()

        assertThat(busyState.isBusy()).isFalse()
    }

    @Test
    fun `finishing utterances never underflows the in flight count`() {
        val busyState = CoachNarrationBusyState()

        busyState.onUtteranceFinished()
        busyState.onUtteranceFinished()

        assertThat(busyState.isBusy()).isFalse()
    }
}
