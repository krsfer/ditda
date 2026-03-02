package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.coach.CoachDecision
import com.morse.master.coach.VoiceRoundMetrics
import com.morse.master.session.SessionMetrics
import org.junit.Test

class AiOrchestratorCoachDecisionTest {
    @Test
    fun `uses nano coach decision when available`() {
        val orchestrator = AiOrchestrator(
            nano = GeminiNanoGateway(
                fakeStatus = "AVAILABLE",
                fakeResponse = "{\"decision\":\"REDUCE_SPEED\"}"
            ),
            fallback = CurriculumDecisionEngine(),
            coachFallback = CoachDecisionEngine()
        )

        val decision = orchestrator.nextCoachDecision(
            currentList = listOf('K', 'M', 'U'),
            metrics = SessionMetrics(50, 95, 300, emptyList()),
            roundMetrics = VoiceRoundMetrics(
                accuracyPercent = 95,
                medianLatencyMs = 360,
                medianConfidence = 0.85,
                assistRate = 0.1,
                missesByChar = emptyMap()
            ),
            consecutiveStableRounds = 2,
            unstableRounds = 0,
            newCharacter = 'U',
            sessionExpansions = 0,
            maxSessionExpansions = 1
        )

        assertThat(decision).isEqualTo(CoachDecision.REDUCE_SPEED)
    }

    @Test
    fun `falls back to deterministic coach decision when nano unavailable`() {
        val fallback = CoachDecisionEngine()
        val orchestrator = AiOrchestrator(
            nano = GeminiNanoGateway(fakeStatus = "BUSY"),
            fallback = CurriculumDecisionEngine(),
            coachFallback = fallback
        )

        val roundMetrics = VoiceRoundMetrics(
            accuracyPercent = 92,
            medianLatencyMs = 390,
            medianConfidence = 0.8,
            assistRate = 0.1,
            missesByChar = emptyMap()
        )

        val expected = fallback.decide(
            currentList = listOf('K', 'M'),
            roundMetrics = roundMetrics,
            consecutiveStableRounds = 2,
            unstableRounds = 0,
            newCharacter = 'U',
            sessionExpansions = 0,
            maxSessionExpansions = 1
        )

        val actual = orchestrator.nextCoachDecision(
            currentList = listOf('K', 'M'),
            metrics = SessionMetrics(50, 94, 385, listOf('M')),
            roundMetrics = roundMetrics,
            consecutiveStableRounds = 2,
            unstableRounds = 0,
            newCharacter = 'U',
            sessionExpansions = 0,
            maxSessionExpansions = 1
        )

        assertThat(actual).isEqualTo(expected)
    }
}
