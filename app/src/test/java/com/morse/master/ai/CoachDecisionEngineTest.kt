package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.coach.CoachDecision
import com.morse.master.coach.VoiceRoundMetrics
import org.junit.Test

class CoachDecisionEngineTest {
    private val engine = CoachDecisionEngine()

    @Test
    fun `expands when two stable rounds are met and session cap is open`() {
        val decision = engine.decide(
            currentList = listOf('K', 'M'),
            roundMetrics = stableMetrics(),
            consecutiveStableRounds = 2,
            unstableRounds = 0,
            newCharacter = 'U',
            sessionExpansions = 0,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.EXPAND_LIST)
    }

    @Test
    fun `keeps list when stable but session expansion cap reached`() {
        val decision = engine.decide(
            currentList = listOf('K', 'M'),
            roundMetrics = stableMetrics(),
            consecutiveStableRounds = 3,
            unstableRounds = 0,
            newCharacter = 'U',
            sessionExpansions = 1,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.KEEP_LIST)
    }

    @Test
    fun `returns freeze progress after four unstable rounds`() {
        val decision = engine.decide(
            currentList = listOf('K', 'M', 'U'),
            roundMetrics = unstableMetrics(medianLatencyMs = 700, missesByChar = mapOf('U' to 3)),
            consecutiveStableRounds = 0,
            unstableRounds = 4,
            newCharacter = 'U',
            sessionExpansions = 1,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.FREEZE_PROGRESS)
    }

    @Test
    fun `returns reinforce when new character is top miss on unstable round`() {
        val decision = engine.decide(
            currentList = listOf('K', 'M', 'U'),
            roundMetrics = unstableMetrics(medianLatencyMs = 460, missesByChar = mapOf('U' to 4, 'M' to 1)),
            consecutiveStableRounds = 0,
            unstableRounds = 1,
            newCharacter = 'U',
            sessionExpansions = 1,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.REINFORCE_NEW_CHAR)
    }

    @Test
    fun `returns reduce speed when unstable from high latency`() {
        val decision = engine.decide(
            currentList = listOf('K', 'M', 'U'),
            roundMetrics = unstableMetrics(medianLatencyMs = 16000, missesByChar = mapOf('M' to 2)),
            consecutiveStableRounds = 0,
            unstableRounds = 1,
            newCharacter = 'U',
            sessionExpansions = 1,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.REDUCE_SPEED)
    }

    private fun stableMetrics(): VoiceRoundMetrics = VoiceRoundMetrics(
        accuracyPercent = 95,
        medianLatencyMs = 360,
        medianConfidence = 0.84,
        assistRate = 0.1,
        missesByChar = emptyMap()
    )

    private fun unstableMetrics(medianLatencyMs: Int, missesByChar: Map<Char, Int>): VoiceRoundMetrics =
        VoiceRoundMetrics(
            accuracyPercent = 75,
            medianLatencyMs = medianLatencyMs,
            medianConfidence = 0.6,
            assistRate = 0.4,
            missesByChar = missesByChar
        )
}
