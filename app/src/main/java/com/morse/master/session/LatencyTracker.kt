package com.morse.master.session

import kotlin.math.roundToInt

class LatencyTracker {
    private val events = mutableListOf<Triple<Char, Char, Int>>()

    fun record(expected: Char, actual: Char, latencyMs: Int) {
        events += Triple(expected, actual, latencyMs)
    }

    fun snapshot(): SessionMetrics {
        val total = events.size.coerceAtLeast(1)
        val correct = events.count { it.first == it.second }
        val sorted = events.map { it.third }.sorted()
        val median = if (sorted.isEmpty()) 0 else sorted[sorted.size / 2]
        val failed = events.filter { it.first != it.second }.map { it.first }.distinct()

        return SessionMetrics(
            totalChars = events.size,
            accuracyPercent = ((correct * 100.0) / total).roundToInt(),
            medianLatencyMs = median,
            failedChars = failed
        )
    }
}
