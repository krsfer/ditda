package com.morse.master.coach

data class VoiceCoachSessionState(
    val coachState: CoachState = CoachState.IDLE,
    val roundIndex: Int = 0,
    val sessionElapsedMs: Long = 0L,
    val lastCoachMessage: String? = null,
    val voiceControlArmed: Boolean = false,
    val currentCharacters: List<Char> = listOf('K', 'M'),
    val consecutiveStableRounds: Int = 0,
    val unstableRounds: Int = 0,
    val sessionExpansions: Int = 0,
    val progressionFrozen: Boolean = false,
    val reinforceRoundsRemaining: Int = 0,
    val newCharacterInSession: Char? = null,
    val characterWpm: Int = 30,
    val effectiveWpm: Int = 8,
    val lastRoundMetrics: VoiceRoundMetrics? = null
)
