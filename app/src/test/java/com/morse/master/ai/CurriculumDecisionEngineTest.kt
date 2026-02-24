package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import org.junit.Test

class CurriculumDecisionEngineTest {
    @Test
    fun `expands list when thresholds are met`() {
        val command = CurriculumDecisionEngine().decide(
            currentList = listOf('K', 'M'),
            metrics = SessionMetrics(totalChars = 50, accuracyPercent = 94, medianLatencyMs = 385, failedChars = listOf('M'))
        )
        assertThat(command.newCharacter).isEqualTo('U')
        assertThat(command.type).isEqualTo(CommandType.EXPAND_LIST)
    }
}
