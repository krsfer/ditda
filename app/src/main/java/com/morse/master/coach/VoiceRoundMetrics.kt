package com.morse.master.coach

data class VoiceRoundMetrics(
    val accuracyPercent: Int,
    val medianLatencyMs: Int,
    val medianConfidence: Double,
    val assistRate: Double,
    val missesByChar: Map<Char, Int>
) {
    fun isStable(
        minAccuracyPercent: Int = MIN_ACCURACY_PERCENT,
        maxMedianLatencyMs: Int = MAX_MEDIAN_LATENCY_MS,
        minMedianConfidence: Double = MIN_MEDIAN_CONFIDENCE,
        maxAssistRate: Double = MAX_ASSIST_RATE
    ): Boolean {
        return accuracyPercent >= minAccuracyPercent &&
            medianLatencyMs <= maxMedianLatencyMs &&
            medianConfidence >= minMedianConfidence &&
            assistRate <= maxAssistRate
    }

    companion object {
        const val MIN_ACCURACY_PERCENT = 90
        const val MAX_MEDIAN_LATENCY_MS = 15000
        const val MIN_MEDIAN_CONFIDENCE = 0.78
        const val MAX_ASSIST_RATE = 0.20

        fun fromAttempts(attempts: List<VoiceAttempt>): VoiceRoundMetrics {
            if (attempts.isEmpty()) {
                return VoiceRoundMetrics(
                    accuracyPercent = 0,
                    medianLatencyMs = 0,
                    medianConfidence = 0.0,
                    assistRate = 1.0,
                    missesByChar = emptyMap()
                )
            }

            val correctCount = attempts.count { it.isCorrect }
            val accuracy = ((correctCount * 100.0) / attempts.size).toInt()
            val medianLatency = attempts.map { it.latencyMs }.median()
            val medianConfidence = attempts.map { it.asrConfidence }.medianDouble()
            val assistRate = attempts.count { it.assistLevel > 0 }.toDouble() / attempts.size.toDouble()
            val misses = attempts
                .filterNot { it.isCorrect }
                .groupingBy { it.expectedChar }
                .eachCount()

            return VoiceRoundMetrics(
                accuracyPercent = accuracy,
                medianLatencyMs = medianLatency,
                medianConfidence = medianConfidence,
                assistRate = assistRate,
                missesByChar = misses
            )
        }

        private fun List<Int>.median(): Int {
            if (isEmpty()) return 0
            val sorted = sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                ((sorted[middle - 1] + sorted[middle]) / 2.0).toInt()
            } else {
                sorted[middle]
            }
        }

        private fun List<Double>.medianDouble(): Double {
            if (isEmpty()) return 0.0
            val sorted = sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            } else {
                sorted[middle]
            }
        }
    }
}
