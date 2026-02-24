package com.morse.master.ai

import com.morse.master.domain.KochSequence
import com.morse.master.session.SessionMetrics

class CurriculumDecisionEngine {
    fun decide(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        if (metrics.accuracyPercent > 90 && metrics.medianLatencyMs < 400) {
            val next = KochSequence.full().firstOrNull { it !in currentList }
            return CurriculumCommand(CommandType.EXPAND_LIST, next)
        }
        return CurriculumCommand(CommandType.KEEP_LIST)
    }
}
