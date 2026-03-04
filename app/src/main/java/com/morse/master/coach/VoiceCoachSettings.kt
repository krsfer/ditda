package com.morse.master.coach

data class VoiceCoachSettings(
    val sessionDurationMs: Long = 8 * 60 * 1000L,
    val answerTimeoutMs: Long = 10000L,
    val maxSessionExpansions: Int = 1,
    val wakePhraseRequired: Boolean = true,
    val feedbackVerbose: Boolean = false,
    val stableRoundsRequired: Int = 2,
    val minCharacterWpm: Int = 10,
    val minEffectiveWpm: Int = 5,
    val characterWpm: Int = 30,
    val effectiveWpm: Int = 8,
    val ultraPhaseEnabled: Boolean = false,
    val maxCharacterWpm: Int = 60,
    val maxEffectiveWpm: Int = 60,
    val learningCharacterWpm: Int = 30,
    val learningEffectiveWpm: Int = 8,
    val masteryCharacterWpm: Int = 30,
    val masteryEffectiveWpm: Int = 30,
    val gestaltLettersRequired: Int = 26
)
