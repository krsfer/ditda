package com.morse.master.coach

data class VoiceCoachSettings(
    val sessionDurationMs: Long = 8 * 60 * 1000L,
    val answerTimeoutMs: Long = 10000L,
    val maxSessionExpansions: Int = 1,
    val wakePhraseRequired: Boolean = true,
    val feedbackVerbose: Boolean = false,
    val stableRoundsRequired: Int = 2,
    val minEffectiveWpm: Int = 5,
    val characterWpm: Int = 25,
    val effectiveWpm: Int = 8
)
