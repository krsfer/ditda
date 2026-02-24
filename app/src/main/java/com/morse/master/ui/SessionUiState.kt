package com.morse.master.ui

data class SessionUiState(
    val showNudge: Boolean = false,
    val lastPlaybackFinishedAtMs: Long? = null
)
