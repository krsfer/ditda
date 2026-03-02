package com.morse.master.coach

internal class CoachNarrationBusyState {
    private var queuedMessages: Int = 0
    private var inFlightUtterances: Int = 0

    @Synchronized
    fun onMessageQueued() {
        queuedMessages += 1
    }

    @Synchronized
    fun onMessageDequeuedForPlayback() {
        queuedMessages = (queuedMessages - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun onUtteranceSubmitted() {
        inFlightUtterances += 1
    }

    @Synchronized
    fun onUtteranceFinished() {
        inFlightUtterances = (inFlightUtterances - 1).coerceAtLeast(0)
    }

    @Synchronized
    fun clear() {
        queuedMessages = 0
        inFlightUtterances = 0
    }

    @Synchronized
    fun isBusy(): Boolean {
        return queuedMessages > 0 || inFlightUtterances > 0
    }
}
