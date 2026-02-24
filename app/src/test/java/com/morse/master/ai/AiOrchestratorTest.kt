package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class AiOrchestratorTest {
    @Test
    fun `falls back when nano is retryable unavailable`() {
        val orchestrator = AiOrchestrator(
            nano = GeminiNanoGateway(fakeStatus = "BUSY"),
            fallback = CurriculumDecisionEngine()
        )
        val cmd = orchestrator.nextCommand(
            currentList = listOf('K', 'M'),
            metrics = SessionMetrics(50, 94, 385, listOf('M'))
        )
        assertThat(cmd.type).isEqualTo(CommandType.EXPAND_LIST)
        assertThat(cmd.newCharacter).isEqualTo('U')
    }
}
