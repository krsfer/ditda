package com.morse.master.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceRoundMetricsTest {
    @Test
    fun `treats threshold boundary as stable`() {
        val attempts = buildList {
            repeat(8) {
                add(
                    VoiceAttempt(
                        expectedChar = 'K',
                        spokenToken = "kilo",
                        resolvedChar = 'K',
                        latencyMs = 450,
                        asrConfidence = 0.78,
                        isCorrect = true,
                        assistLevel = 0
                    )
                )
            }
            add(
                VoiceAttempt(
                    expectedChar = 'M',
                    spokenToken = "mike",
                    resolvedChar = 'M',
                    latencyMs = 450,
                    asrConfidence = 0.78,
                    isCorrect = true,
                    assistLevel = 1
                )
            )
            add(
                VoiceAttempt(
                    expectedChar = 'M',
                    spokenToken = "kilo",
                    resolvedChar = 'K',
                    latencyMs = 450,
                    asrConfidence = 0.78,
                    isCorrect = false,
                    assistLevel = 1
                )
            )
        }

        val metrics = VoiceRoundMetrics.fromAttempts(attempts)

        assertThat(metrics.accuracyPercent).isEqualTo(90)
        assertThat(metrics.medianLatencyMs).isEqualTo(450)
        assertThat(metrics.medianConfidence).isEqualTo(0.78)
        assertThat(metrics.assistRate).isEqualTo(0.2)
        assertThat(metrics.isStable()).isTrue()
    }

    @Test
    fun `fails stability when assist rate exceeds threshold`() {
        val attempts = listOf(
            VoiceAttempt('K', "kilo", 'K', 350, 0.9, true, 2),
            VoiceAttempt('M', "mike", 'M', 350, 0.9, true, 2),
            VoiceAttempt('K', "kilo", 'K', 350, 0.9, true, 2),
            VoiceAttempt('M', "mike", 'M', 350, 0.9, true, 0),
            VoiceAttempt('K', "kilo", 'K', 350, 0.9, true, 0),
            VoiceAttempt('M', "mike", 'M', 350, 0.9, true, 0),
            VoiceAttempt('K', "kilo", 'K', 350, 0.9, true, 0),
            VoiceAttempt('M', "mike", 'M', 350, 0.9, true, 0),
            VoiceAttempt('K', "kilo", 'K', 350, 0.9, true, 0),
            VoiceAttempt('M', "mike", 'M', 350, 0.9, true, 0)
        )

        val metrics = VoiceRoundMetrics.fromAttempts(attempts)

        assertThat(metrics.assistRate).isGreaterThan(0.2)
        assertThat(metrics.isStable()).isFalse()
    }
}
