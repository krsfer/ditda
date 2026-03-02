package com.morse.master.ai

import com.morse.master.coach.CoachDecision
import com.morse.master.coach.VoiceRoundMetrics
import com.morse.master.session.SessionMetrics

class AiOrchestrator(
    private val nano: GeminiNanoGateway,
    private val fallback: CurriculumDecisionEngine,
    private val coachFallback: CoachDecisionEngine = CoachDecisionEngine()
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

    fun nextCoachDecision(
        currentList: List<Char>,
        metrics: SessionMetrics,
        roundMetrics: VoiceRoundMetrics,
        consecutiveStableRounds: Int,
        unstableRounds: Int,
        newCharacter: Char?,
        sessionExpansions: Int,
        maxSessionExpansions: Int
    ): CoachDecision {
        if (nano.checkAvailability() == NanoAvailability.AVAILABLE) {
            val response = nano.runPromptOrNull(
                buildCoachPrompt(
                    currentList = currentList,
                    metrics = metrics,
                    roundMetrics = roundMetrics,
                    consecutiveStableRounds = consecutiveStableRounds,
                    unstableRounds = unstableRounds
                )
            )
            parseCoachDecision(response)?.let { return it }
        }

        return coachFallback.decide(
            currentList = currentList,
            roundMetrics = roundMetrics,
            consecutiveStableRounds = consecutiveStableRounds,
            unstableRounds = unstableRounds,
            newCharacter = newCharacter,
            sessionExpansions = sessionExpansions,
            maxSessionExpansions = maxSessionExpansions
        )
    }

    private fun buildCoachPrompt(
        currentList: List<Char>,
        metrics: SessionMetrics,
        roundMetrics: VoiceRoundMetrics,
        consecutiveStableRounds: Int,
        unstableRounds: Int
    ): String {
        return """{"current_list":${currentList.map { "\"$it\"" }},"metrics":{"accuracy_percent":${metrics.accuracyPercent},"median_latency_ms":${metrics.medianLatencyMs}},"round":{"accuracy_percent":${roundMetrics.accuracyPercent},"median_latency_ms":${roundMetrics.medianLatencyMs},"median_confidence":${roundMetrics.medianConfidence},"assist_rate":${roundMetrics.assistRate}},"streaks":{"stable":$consecutiveStableRounds,"unstable":$unstableRounds}}"""
    }

    private fun parseCoachDecision(response: String?): CoachDecision? {
        val body = response ?: return null
        return when {
            "\"FREEZE_PROGRESS\"" in body -> CoachDecision.FREEZE_PROGRESS
            "\"REINFORCE_NEW_CHAR\"" in body -> CoachDecision.REINFORCE_NEW_CHAR
            "\"REDUCE_SPEED\"" in body -> CoachDecision.REDUCE_SPEED
            "\"EXPAND_LIST\"" in body -> CoachDecision.EXPAND_LIST
            "\"KEEP_LIST\"" in body -> CoachDecision.KEEP_LIST
            else -> null
        }
    }
}
