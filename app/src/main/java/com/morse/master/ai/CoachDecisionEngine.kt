package com.morse.master.ai

import com.morse.master.coach.CoachDecision
import com.morse.master.coach.VoiceRoundMetrics
import com.morse.master.domain.KochSequence

class CoachDecisionEngine {
    fun decide(
        currentList: List<Char>,
        roundMetrics: VoiceRoundMetrics,
        consecutiveStableRounds: Int,
        unstableRounds: Int,
        newCharacter: Char?,
        sessionExpansions: Int,
        maxSessionExpansions: Int,
        stableRoundsRequired: Int = 2
    ): CoachDecision {
        if (unstableRounds >= 4) {
            return CoachDecision.FREEZE_PROGRESS
        }

        if (roundMetrics.isStable()) {
            val canExpand = consecutiveStableRounds >= stableRoundsRequired &&
                sessionExpansions < maxSessionExpansions &&
                hasNextCharacter(currentList)
            return if (canExpand) {
                CoachDecision.EXPAND_LIST
            } else {
                CoachDecision.KEEP_LIST
            }
        }

        val topMiss = roundMetrics.missesByChar.maxByOrNull { it.value }?.key
        if (newCharacter != null && topMiss == newCharacter) {
            return CoachDecision.REINFORCE_NEW_CHAR
        }

        if (roundMetrics.medianLatencyMs > VoiceRoundMetrics.MAX_MEDIAN_LATENCY_MS) {
            return CoachDecision.REDUCE_SPEED
        }

        return CoachDecision.KEEP_LIST
    }

    private fun hasNextCharacter(currentList: List<Char>): Boolean {
        return KochSequence.full().any { it !in currentList }
    }
}
