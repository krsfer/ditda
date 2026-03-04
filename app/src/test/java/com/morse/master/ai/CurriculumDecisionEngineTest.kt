package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import com.morse.master.session.SessionMetrics
import com.morse.master.session.TrainingSetPerformanceSnapshot
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

    @Test
    fun `speeds up after two stable training-set iterations`() {
        val command = CurriculumDecisionEngine().decideTrainingSetAdjustment(
            currentList = listOf('K', 'M', 'U'),
            metrics = TrainingSetPerformanceSnapshot(
                totalChars = 12,
                accuracyPercent = 96,
                medianLatencyMs = 360,
                failedChars = emptyList(),
                missesByChar = emptyMap(),
                problemCharacters = emptySet(),
                easyCharacters = setOf('K')
            ),
            stableIterations = 2,
            unstableIterations = 0,
            characterWpm = 30,
            effectiveWpm = 20
        )

        assertThat(command.type).isEqualTo(CommandType.SPEED_UP)
    }

    @Test
    fun `expands list after two stable iterations when spacing is closed`() {
        val command = CurriculumDecisionEngine().decideTrainingSetAdjustment(
            currentList = listOf('K', 'M'),
            metrics = TrainingSetPerformanceSnapshot(
                totalChars = 20,
                accuracyPercent = 98,
                medianLatencyMs = 280,
                failedChars = emptyList(),
                missesByChar = emptyMap(),
                problemCharacters = emptySet(),
                easyCharacters = setOf('K', 'M')
            ),
            stableIterations = 2,
            unstableIterations = 0,
            characterWpm = 30,
            effectiveWpm = 30
        )

        assertThat(command.type).isEqualTo(CommandType.EXPAND_LIST)
        assertThat(command.newCharacter).isEqualTo('U')
    }

    @Test
    fun `slows down after two unstable training-set iterations`() {
        val command = CurriculumDecisionEngine().decideTrainingSetAdjustment(
            currentList = listOf('K', 'M', 'U'),
            metrics = TrainingSetPerformanceSnapshot(
                totalChars = 14,
                accuracyPercent = 70,
                medianLatencyMs = 1100,
                failedChars = listOf('U'),
                missesByChar = mapOf('U' to 4),
                problemCharacters = setOf('U'),
                easyCharacters = emptySet()
            ),
            stableIterations = 0,
            unstableIterations = 2,
            characterWpm = 30,
            effectiveWpm = 8
        )

        assertThat(command.type).isEqualTo(CommandType.SPEED_DOWN)
    }

    @Test
    fun `removes latest character when unstable at speed floor`() {
        val command = CurriculumDecisionEngine().decideTrainingSetAdjustment(
            currentList = listOf('K', 'M', 'U'),
            metrics = TrainingSetPerformanceSnapshot(
                totalChars = 12,
                accuracyPercent = 65,
                medianLatencyMs = 1200,
                failedChars = listOf('U'),
                missesByChar = mapOf('U' to 5),
                problemCharacters = setOf('U'),
                easyCharacters = emptySet()
            ),
            stableIterations = 0,
            unstableIterations = 2,
            characterWpm = 10,
            effectiveWpm = 5
        )

        assertThat(command.type).isEqualTo(CommandType.REMOVE_LATEST)
    }
}
