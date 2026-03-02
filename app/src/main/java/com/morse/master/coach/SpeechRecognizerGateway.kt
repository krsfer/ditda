package com.morse.master.coach

data class SpeechRecognitionResult(
    val token: String,
    val confidence: Double,
    val latencyMs: Int
)

interface SpeechRecognizerGateway {
    suspend fun listenForAnswer(timeoutMs: Long): SpeechRecognitionResult?
}

class NoopSpeechRecognizerGateway : SpeechRecognizerGateway {
    override suspend fun listenForAnswer(timeoutMs: Long): SpeechRecognitionResult? = null
}
