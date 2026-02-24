package com.morse.master.ai

class GeminiNanoGateway(
    private val fakeStatus: String? = null,
    private val failPrompt: Boolean = false
) {
    fun checkAvailability(): NanoAvailability = when (fakeStatus) {
        "AVAILABLE" -> NanoAvailability.AVAILABLE
        "DOWNLOADING" -> NanoAvailability.DOWNLOADING
        "BUSY" -> NanoAvailability.RETRYABLE_UNAVAILABLE
        else -> NanoAvailability.UNAVAILABLE
    }

    fun runPromptOrNull(inputJson: String): String? {
        if (failPrompt) {
            return null
        }
        return """{"command":"EXPAND_LIST","new_character":"U"}"""
    }
}
