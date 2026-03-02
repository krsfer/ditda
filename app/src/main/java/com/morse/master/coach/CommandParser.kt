package com.morse.master.coach

class CommandParser(
    private val wakePhraseRequired: Boolean = true,
    private val wakePhrase: String = DEFAULT_WAKE_PHRASE
) {
    fun parse(
        input: String,
        wakePhraseRequiredOverride: Boolean = wakePhraseRequired
    ): CoachVoiceCommand? {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return null

        val phrase = when {
            wakePhraseRequiredOverride -> {
                val prefix = "$wakePhrase "
                if (!normalized.startsWith(prefix)) return null
                normalized.removePrefix(prefix)
            }
            normalized.startsWith("$wakePhrase ") -> normalized.removePrefix("$wakePhrase ")
            else -> normalized
        }.trim()

        return commandMap[phrase]
    }

    private fun normalize(raw: String): String {
        return raw
            .lowercase()
            .replace(Regex("[^a-z ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val DEFAULT_WAKE_PHRASE = "coach"

        private val commandMap = mapOf(
            "start session" to CoachVoiceCommand.START_SESSION,
            "pause" to CoachVoiceCommand.PAUSE,
            "resume" to CoachVoiceCommand.RESUME,
            "repeat" to CoachVoiceCommand.REPEAT,
            "slower" to CoachVoiceCommand.SLOWER,
            "faster" to CoachVoiceCommand.FASTER,
            "stop" to CoachVoiceCommand.STOP,
            "continue" to CoachVoiceCommand.CONTINUE,
            "continue session" to CoachVoiceCommand.CONTINUE
        )
    }
}
