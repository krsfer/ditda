package com.morse.master.ai

import com.morse.master.session.SessionMetrics

class AiOrchestrator(
    private val nano: GeminiNanoGateway,
    private val fallback: CurriculumDecisionEngine
) {
    fun nextCommand(currentList: List<Char>, metrics: SessionMetrics): CurriculumCommand {
        if (nano.checkAvailability() == NanoAvailability.AVAILABLE) {
            val response = nano.runPromptOrNull(
                """{"current_list":${currentList.map { "\"$it\"" }},"metrics":{"accuracy_percent":${metrics.accuracyPercent},"median_latency_ms":${metrics.medianLatencyMs}}}"""
            )
            if (response != null && response.contains("\"EXPAND_LIST\"")) {
                return CurriculumCommand(CommandType.EXPAND_LIST, 'U')
            }
        }
        return when (nano.checkAvailability()) {
            NanoAvailability.AVAILABLE -> fallback.decide(currentList, metrics)
            NanoAvailability.DOWNLOADING,
            NanoAvailability.RETRYABLE_UNAVAILABLE,
            NanoAvailability.UNAVAILABLE -> fallback.decide(currentList, metrics)
        }
    }
}
