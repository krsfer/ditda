package com.morse.master.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrainingSetPerformanceTrackerTest {
    @Test
    fun `classifies problem and easy characters from rolling attempts`() {
        val tracker = TrainingSetPerformanceTracker(maxEvents = 40)

        repeat(4) {
            tracker.record(expected = 'K', actual = 'K', latencyMs = 240)
        }
        tracker.record(expected = 'U', actual = 'M', latencyMs = 1100)
        tracker.record(expected = 'U', actual = 'K', latencyMs = 980)
        tracker.record(expected = 'U', actual = 'U', latencyMs = 910)
        tracker.record(expected = 'U', actual = 'U', latencyMs = 860)

        val snapshot = tracker.snapshot()

        assertThat(snapshot.problemCharacters).containsExactly('U')
        assertThat(snapshot.easyCharacters).containsExactly('K')
        assertThat(snapshot.failedChars).containsExactly('U')
        assertThat(snapshot.missesByChar['U']).isEqualTo(2)
    }

    @Test
    fun `keeps only configured rolling window size`() {
        val tracker = TrainingSetPerformanceTracker(maxEvents = 3)

        tracker.record(expected = 'K', actual = 'K', latencyMs = 200)
        tracker.record(expected = 'M', actual = 'M', latencyMs = 210)
        tracker.record(expected = 'U', actual = 'M', latencyMs = 900)
        tracker.record(expected = 'R', actual = 'R', latencyMs = 220)

        val snapshot = tracker.snapshot()

        assertThat(snapshot.totalChars).isEqualTo(3)
        assertThat(snapshot.failedChars).containsExactly('U')
        assertThat(snapshot.missesByChar['U']).isEqualTo(1)
    }
}
