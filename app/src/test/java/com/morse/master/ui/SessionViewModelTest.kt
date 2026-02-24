package com.morse.master.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionViewModelTest {
    @Test
    fun `emits nudge when response timeout is exceeded`() = runTest {
        val vm = SessionViewModel()
        vm.state.test {
            assertThat(awaitItem().showNudge).isFalse()
            vm.onPlaybackFinished(nowMs = 0L)
            assertThat(awaitItem().showNudge).isFalse()
            vm.onTick(nowMs = 1501L)
            assertThat(awaitItem().showNudge).isTrue()
        }
    }
}
