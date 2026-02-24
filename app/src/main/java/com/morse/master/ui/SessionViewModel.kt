package com.morse.master.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionViewModel {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    fun onPlaybackFinished(nowMs: Long) {
        _state.value = _state.value.copy(lastPlaybackFinishedAtMs = nowMs, showNudge = false)
    }

    fun onTick(nowMs: Long) {
        val last = _state.value.lastPlaybackFinishedAtMs ?: return
        if (nowMs - last > 1500) {
            _state.value = _state.value.copy(showNudge = true)
        }
    }
}
