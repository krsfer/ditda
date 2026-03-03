package com.morse.master.ui

import android.content.Context

data class DitDaPersistedState(
    val settings: DitDaSettings = DitDaSettings(),
    val currentCharacters: List<Char> = listOf('K', 'M'),
    val textPlaybackInput: String = "",
    val textPlaybackLoopEnabled: Boolean = false
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
            randomizeTrainingSetOrder = prefs.getBoolean(
                KEY_RANDOMIZE_TRAINING_SET_ORDER,
                DEFAULT_SETTINGS.randomizeTrainingSetOrder
            ),
            darkMode = prefs.getBoolean(KEY_DARK_MODE, DEFAULT_SETTINGS.darkMode),
            handsFreeEnabled = prefs.getBoolean(
                KEY_HANDS_FREE_ENABLED,
                DEFAULT_SETTINGS.handsFreeEnabled
            ),
            wakePhraseRequired = prefs.getBoolean(
                KEY_WAKE_PHRASE_REQUIRED,
                DEFAULT_SETTINGS.wakePhraseRequired
            ),
            feedbackVerbose = prefs.getBoolean(
                KEY_FEEDBACK_VERBOSE,
                DEFAULT_SETTINGS.feedbackVerbose
            )
        )
        return DitDaPersistedState(
            settings = settings,
            currentCharacters = parseCharacters(
                prefs.getString(KEY_CURRENT_CHARACTERS, null)
            ) ?: DEFAULT_CHARACTERS,
            textPlaybackInput = prefs.getString(
                KEY_TEXT_PLAYBACK_INPUT,
                DEFAULT_TEXT_PLAYBACK_INPUT
            ) ?: DEFAULT_TEXT_PLAYBACK_INPUT,
            textPlaybackLoopEnabled = prefs.getBoolean(
                KEY_TEXT_PLAYBACK_LOOP_ENABLED,
                DEFAULT_TEXT_PLAYBACK_LOOP_ENABLED
            )
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
            .putBoolean(KEY_RANDOMIZE_TRAINING_SET_ORDER, state.settings.randomizeTrainingSetOrder)
            .putBoolean(KEY_DARK_MODE, state.settings.darkMode)
            .putBoolean(KEY_HANDS_FREE_ENABLED, state.settings.handsFreeEnabled)
            .putBoolean(KEY_WAKE_PHRASE_REQUIRED, state.settings.wakePhraseRequired)
            .putBoolean(KEY_FEEDBACK_VERBOSE, state.settings.feedbackVerbose)
            .putString(KEY_CURRENT_CHARACTERS, state.currentCharacters.joinToString(separator = ","))
            .putString(KEY_TEXT_PLAYBACK_INPUT, state.textPlaybackInput)
            .putBoolean(KEY_TEXT_PLAYBACK_LOOP_ENABLED, state.textPlaybackLoopEnabled)
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
        private const val KEY_RANDOMIZE_TRAINING_SET_ORDER = "randomize_training_set_order"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_HANDS_FREE_ENABLED = "hands_free_enabled"
        private const val KEY_WAKE_PHRASE_REQUIRED = "wake_phrase_required"
        private const val KEY_FEEDBACK_VERBOSE = "feedback_verbose"
        private const val KEY_CURRENT_CHARACTERS = "current_characters"
        private const val KEY_TEXT_PLAYBACK_INPUT = "text_playback_input"
        private const val KEY_TEXT_PLAYBACK_LOOP_ENABLED = "text_playback_loop_enabled"

        private val DEFAULT_SETTINGS = DitDaSettings()
        private val DEFAULT_CHARACTERS = listOf('K', 'M')
        private const val DEFAULT_TEXT_PLAYBACK_INPUT = ""
        private const val DEFAULT_TEXT_PLAYBACK_LOOP_ENABLED = false
    }
}
