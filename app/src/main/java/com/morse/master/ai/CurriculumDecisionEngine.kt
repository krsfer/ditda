package com.morse.master.ai

import com.morse.master.domain.KochSequence
import com.morse.master.session.SessionMetrics
import com.morse.master.session.TrainingSetPerformanceSnapshot

class CurriculumDecisionEngine {
    fun decide(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        if (metrics.accuracyPercent > 90 && metrics.medianLatencyMs < 400) {
            val next = KochSequence.full().firstOrNull { it !in currentList }
            return CurriculumCommand(CommandType.EXPAND_LIST, next)
        }
        return CurriculumCommand(CommandType.KEEP_LIST)
    }

    fun decideTrainingSetAdjustment(
        currentList: List<Char>,
        metrics: TrainingSetPerformanceSnapshot,
        stableIterations: Int,
        unstableIterations: Int,
        characterWpm: Int,
        effectiveWpm: Int,
        minCharacterWpm: Int = 10,
        minEffectiveWpm: Int = 5
    ): CurriculumCommand {
        if (unstableIterations >= 2) {
            if (effectiveWpm <= minEffectiveWpm &&
                characterWpm <= minCharacterWpm &&
                currentList.size > BASE_CURRICULUM_SIZE
            ) {
                return CurriculumCommand(CommandType.REMOVE_LATEST)
            }
            return CurriculumCommand(
                type = CommandType.SPEED_DOWN,
                effectiveWpmDelta = -1
            )
        }

        if (stableIterations >= 2) {
            if (effectiveWpm >= characterWpm && metrics.problemCharacters.isEmpty()) {
                val next = KochSequence.full().firstOrNull { it !in currentList }
                if (next != null) {
                    return CurriculumCommand(
                        type = CommandType.EXPAND_LIST,
                        newCharacter = next
                    )
                }
            }
            return CurriculumCommand(
                type = CommandType.SPEED_UP,
                effectiveWpmDelta = 1
            )
        }

        return CurriculumCommand(CommandType.KEEP_LIST)
    }

    private companion object {
        const val BASE_CURRICULUM_SIZE = 2
    }
}
