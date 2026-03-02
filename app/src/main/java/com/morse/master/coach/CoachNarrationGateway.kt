package com.morse.master.coach

interface CoachNarrationGateway {
    fun speak(message: String)
    fun isSpeaking(): Boolean = false
}

class NoopCoachNarrationGateway : CoachNarrationGateway {
    override fun speak(message: String) = Unit
}
