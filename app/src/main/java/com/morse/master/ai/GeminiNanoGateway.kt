package com.morse.master.ai

class GeminiNanoGateway(
    private val fakeStatus: String? = null
) {
    fun checkAvailability(): NanoAvailability = when (fakeStatus) {
        "AVAILABLE" -> NanoAvailability.AVAILABLE
        "DOWNLOADING" -> NanoAvailability.DOWNLOADING
        "BUSY" -> NanoAvailability.RETRYABLE_UNAVAILABLE
        else -> NanoAvailability.UNAVAILABLE
    }
}
