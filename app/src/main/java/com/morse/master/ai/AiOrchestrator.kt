package com.morse.master.ai

import com.morse.master.session.SessionMetrics

class AiOrchestrator(
    private val nano: GeminiNanoGateway,
    private val fallback: CurriculumDecisionEngine
) {
    fun nextCommand(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        return when (nano.checkAvailability()) {
            NanoAvailability.AVAILABLE -> fallback.decide(currentList, metrics)
            NanoAvailability.DOWNLOADING,
            NanoAvailability.RETRYABLE_UNAVAILABLE,
            NanoAvailability.UNAVAILABLE -> fallback.decide(currentList, metrics)
        }
    }
}
