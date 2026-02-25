package com.morse.master.ui

import android.content.Context

data class DitDaPersistedState(
    val settings: DitDaSettings = DitDaSettings(),
    val currentCharacters: List<Char> = listOf('K', 'M')
)

interface DitDaStateStore {
    fun load(): DitDaPersistedState
    fun save(state: DitDaPersistedState)
}

class InMemoryDitDaStateStore(
    private var state: DitDaPersistedState = DitDaPersistedState()
) : DitDaStateStore {
    override fun load(): DitDaPersistedState = state

    override fun save(state: DitDaPersistedState) {
        this.state = state
    }
}

class SharedPrefsDitDaStateStore(
    context: Context,
    prefsName: String = PREFS_NAME
) : DitDaStateStore {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun load(): DitDaPersistedState {
        val settings = DitDaSettings(
            characterWpm = prefs.getInt(KEY_CHARACTER_WPM, DEFAULT_SETTINGS.characterWpm),
            effectiveWpm = prefs.getInt(KEY_EFFECTIVE_WPM, DEFAULT_SETTINGS.effectiveWpm),
            toneHz = prefs.getInt(KEY_TONE_HZ, DEFAULT_SETTINGS.toneHz),
            soundEnabled = prefs.getBoolean(KEY_SOUND_ENABLED, DEFAULT_SETTINGS.soundEnabled),
            vibrationEnabled = prefs.getBoolean(KEY_VIBRATION_ENABLED, DEFAULT_SETTINGS.vibrationEnabled),
            highlightPlaybackEnabled = prefs.getBoolean(
                KEY_HIGHLIGHT_PLAYBACK_ENABLED,
                DEFAULT_SETTINGS.highlightPlaybackEnabled
            ),
            trainingSetRepeatCount = prefs.getInt(
                KEY_TRAINING_SET_REPEAT_COUNT,
                DEFAULT_SETTINGS.trainingSetRepeatCount
            ),
            darkMode = prefs.getBoolean(KEY_DARK_MODE, DEFAULT_SETTINGS.darkMode)
        )
        return DitDaPersistedState(
            settings = settings,
            currentCharacters = parseCharacters(
                prefs.getString(KEY_CURRENT_CHARACTERS, null)
            ) ?: DEFAULT_CHARACTERS
        )
    }

    override fun save(state: DitDaPersistedState) {
        prefs.edit()
            .putInt(KEY_CHARACTER_WPM, state.settings.characterWpm)
            .putInt(KEY_EFFECTIVE_WPM, state.settings.effectiveWpm)
            .putInt(KEY_TONE_HZ, state.settings.toneHz)
            .putBoolean(KEY_SOUND_ENABLED, state.settings.soundEnabled)
            .putBoolean(KEY_VIBRATION_ENABLED, state.settings.vibrationEnabled)
            .putBoolean(KEY_HIGHLIGHT_PLAYBACK_ENABLED, state.settings.highlightPlaybackEnabled)
            .putInt(KEY_TRAINING_SET_REPEAT_COUNT, state.settings.trainingSetRepeatCount)
            .putBoolean(KEY_DARK_MODE, state.settings.darkMode)
            .putString(KEY_CURRENT_CHARACTERS, state.currentCharacters.joinToString(separator = ","))
            .apply()
    }

    private fun parseCharacters(serialized: String?): List<Char>? {
        if (serialized.isNullOrBlank()) return null
        val parsed = serialized
            .split(',')
            .mapNotNull { entry ->
                val trimmed = entry.trim()
                if (trimmed.length == 1) trimmed[0].uppercaseChar() else null
            }
            .distinct()
        return parsed.ifEmpty { null }
    }

    private companion object {
        private const val PREFS_NAME = "ditda_state"
        private const val KEY_CHARACTER_WPM = "character_wpm"
        private const val KEY_EFFECTIVE_WPM = "effective_wpm"
        private const val KEY_TONE_HZ = "tone_hz"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_HIGHLIGHT_PLAYBACK_ENABLED = "highlight_playback_enabled"
        private const val KEY_TRAINING_SET_REPEAT_COUNT = "training_set_repeat_count"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_CURRENT_CHARACTERS = "current_characters"

        private val DEFAULT_SETTINGS = DitDaSettings()
        private val DEFAULT_CHARACTERS = listOf('K', 'M')
    }
}
