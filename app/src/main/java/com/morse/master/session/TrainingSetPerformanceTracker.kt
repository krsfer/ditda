package com.morse.master.session

import kotlin.math.roundToInt

class TrainingSetPerformanceTracker(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS
) {
    private val events = ArrayDeque<TrainingSetTapEvent>()

    fun record(expected: Char, actual: Char, latencyMs: Int) {
        if (maxEvents <= 0) return
        if (events.size == maxEvents) {
            events.removeFirst()
        }
        events.addLast(
            TrainingSetTapEvent(
                expected = expected,
                actual = actual,
                latencyMs = latencyMs.coerceAtLeast(0)
            )
        )
    }

    fun snapshot(): TrainingSetPerformanceSnapshot {
        if (events.isEmpty()) {
            return TrainingSetPerformanceSnapshot(
                totalChars = 0,
                accuracyPercent = 0,
                medianLatencyMs = 0,
                failedChars = emptyList(),
                missesByChar = emptyMap(),
                problemCharacters = emptySet(),
                easyCharacters = emptySet()
            )
        }

        val eventList = events.toList()
        val total = eventList.size
        val correctCount = eventList.count { it.isCorrect }
        val accuracy = ((correctCount * 100.0) / total.toDouble()).roundToInt()
        val medianLatency = eventList.map { it.latencyMs }.median()
        val misses = eventList
            .filterNot { it.isCorrect }
            .groupingBy { it.expected }
            .eachCount()
        val failed = misses.keys.toList()

        val groupedByExpected = eventList.groupBy { it.expected }
        val problemChars = mutableSetOf<Char>()
        val easyChars = mutableSetOf<Char>()

        groupedByExpected.forEach { (char, attempts) ->
            if (attempts.size < MIN_ATTEMPTS_FOR_LABEL) {
                return@forEach
            }

            val missesForChar = attempts.count { !it.isCorrect }
            val errorRate = missesForChar.toDouble() / attempts.size.toDouble()
            val medianForChar = attempts.map { it.latencyMs }.median()

            val isProblem = errorRate >= PROBLEM_ERROR_RATE_THRESHOLD ||
                (medianForChar >= PROBLEM_LATENCY_THRESHOLD_MS &&
                    errorRate > PROBLEM_LATENCY_ERROR_RATE_THRESHOLD)
            val isEasy = errorRate <= EASY_ERROR_RATE_THRESHOLD &&
                medianForChar <= EASY_LATENCY_THRESHOLD_MS

            if (isProblem) {
                problemChars += char
            } else if (isEasy) {
                easyChars += char
            }
        }

        return TrainingSetPerformanceSnapshot(
            totalChars = total,
            accuracyPercent = accuracy,
            medianLatencyMs = medianLatency,
            failedChars = failed,
            missesByChar = misses,
            problemCharacters = problemChars,
            easyCharacters = easyChars
        )
    }

    fun clear() {
        events.clear()
    }

    private fun List<Int>.median(): Int {
        if (isEmpty()) return 0
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToInt()
        } else {
            sorted[middle]
        }
    }

    private companion object {
        const val DEFAULT_MAX_EVENTS = 40
        const val MIN_ATTEMPTS_FOR_LABEL = 3
        const val PROBLEM_ERROR_RATE_THRESHOLD = 0.35
        const val PROBLEM_LATENCY_THRESHOLD_MS = 900
        const val PROBLEM_LATENCY_ERROR_RATE_THRESHOLD = 0.20
        const val EASY_ERROR_RATE_THRESHOLD = 0.05
        const val EASY_LATENCY_THRESHOLD_MS = 400
    }
}
