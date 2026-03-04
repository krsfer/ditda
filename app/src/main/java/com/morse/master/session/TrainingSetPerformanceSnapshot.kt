package com.morse.master.session

data class TrainingSetPerformanceSnapshot(
    val totalChars: Int,
    val accuracyPercent: Int,
    val medianLatencyMs: Int,
    val failedChars: List<Char>,
    val missesByChar: Map<Char, Int>,
    val problemCharacters: Set<Char>,
    val easyCharacters: Set<Char>
)
