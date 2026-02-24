package com.morse.master.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LatencyTrackerTest {
    @Test
    fun `computes median and accuracy`() {
        val tracker = LatencyTracker()
        tracker.record(expected = 'K', actual = 'K', latencyMs = 300)
        tracker.record(expected = 'M', actual = 'K', latencyMs = 520)
        tracker.record(expected = 'U', actual = 'U', latencyMs = 380)

        val metrics = tracker.snapshot()
        assertThat(metrics.totalChars).isEqualTo(3)
        assertThat(metrics.accuracyPercent).isEqualTo(67)
        assertThat(metrics.medianLatencyMs).isEqualTo(380)
        assertThat(metrics.failedChars).containsExactly('M')
    }
}
