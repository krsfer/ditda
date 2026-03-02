package com.morse.master.coach

data class VoiceAttempt(
    val expectedChar: Char,
    val spokenToken: String?,
    val resolvedChar: Char?,
    val latencyMs: Int,
    val asrConfidence: Double,
    val isCorrect: Boolean,
    val assistLevel: Int
)
