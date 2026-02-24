package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class PromptContractTest {
    @Test
    fun `falls back when prompt call throws`() {
        val gateway = GeminiNanoGateway(fakeStatus = "AVAILABLE", failPrompt = true)
        val orchestrator = AiOrchestrator(gateway, CurriculumDecisionEngine())
        val cmd = orchestrator.nextCommand(listOf('K', 'M'), SessionMetrics(50, 95, 300, emptyList()))
        assertThat(cmd.newCharacter).isEqualTo('U')
    }
}
