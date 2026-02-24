package com.morse.master.session

data class SessionMetrics(
    val totalChars: Int,
    val accuracyPercent: Int,
    val medianLatencyMs: Int,
    val failedChars: List<Char>
)
