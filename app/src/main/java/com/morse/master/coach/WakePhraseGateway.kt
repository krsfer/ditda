package com.morse.master.coach

interface WakePhraseGateway {
    fun isWakePhraseDetected(transcript: String): Boolean
}

class PrefixWakePhraseGateway(
    private val wakePhrase: String = "coach"
) : WakePhraseGateway {
    override fun isWakePhraseDetected(transcript: String): Boolean {
        val normalized = transcript
            .lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized.startsWith("$wakePhrase ")
    }
}
