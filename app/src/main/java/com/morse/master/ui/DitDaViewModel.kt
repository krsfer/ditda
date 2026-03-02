package com.morse.master.ui

import com.morse.master.ai.CoachDecisionEngine
import com.morse.master.coach.CoachState
import com.morse.master.coach.CoachVoiceCommand
import com.morse.master.coach.CommandParser
import com.morse.master.coach.NoopCoachNarrationGateway
import com.morse.master.coach.NoopSpeechRecognizerGateway
import com.morse.master.coach.PhoneticVocabulary
import com.morse.master.coach.PrefixWakePhraseGateway
import com.morse.master.coach.VoiceAttempt
import com.morse.master.coach.VoiceCoachCoordinator
import com.morse.master.coach.VoiceCoachSettings
import com.morse.master.domain.KochSequence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTab {
    PRACTICE,
    SETTINGS
}

const val TRAINING_SET_REPEAT_ENDLESS = -1

data class DitDaSettings(
    val characterWpm: Int = 25,
    val effectiveWpm: Int = 8,
    val toneHz: Int = 600,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val highlightPlaybackEnabled: Boolean = true,
    val trainingSetRepeatCount: Int = 0,
    val darkMode: Boolean = true,
    val handsFreeEnabled: Boolean = false,
    val wakePhraseRequired: Boolean = true,
    val feedbackVerbose: Boolean = false
)

data class DitDaUiState(
    val activeTab: AppTab = AppTab.PRACTICE,
    val settings: DitDaSettings = DitDaSettings(),
    val currentCharacters: List<Char> = listOf('K', 'M'),
    val nextCharacter: Char? = 'U',
    val isPlaying: Boolean = false,
    val currentIteration: Int = 0,
    val highlightedCharacter: Char? = null,
    val stopPlaybackRequested: Boolean = false,
    val coachState: CoachState = CoachState.IDLE,
    val roundIndex: Int = 0,
    val sessionElapsedMs: Long = 0L,
    val lastCoachMessage: String? = null,
    val voiceControlArmed: Boolean = false
)

class DitDaViewModel(
    private val stateStore: DitDaStateStore = InMemoryDitDaStateStore(),
    private val shuffler: (List<Char>) -> List<Char> = { chars -> chars.shuffled() },
    private val coachCoordinator: VoiceCoachCoordinator = VoiceCoachCoordinator(
        commandParser = CommandParser(),
        vocabulary = PhoneticVocabulary(),
        speechRecognizer = NoopSpeechRecognizerGateway(),
        narration = NoopCoachNarrationGateway(),
        wakePhraseGateway = PrefixWakePhraseGateway(),
        settings = VoiceCoachSettings(),
        orchestrator = CoachDecisionEngine()
    )
) {
    private companion object {
        private const val BASE_CURRICULUM_SIZE = 2
        private const val MIN_TRAINING_SET_REPEAT_COUNT = 0
        private const val MAX_TRAINING_SET_REPEAT_COUNT = 10
    }

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<DitDaUiState> = _state.asStateFlow()

    init {
        coachCoordinator.setCurrentCharacters(_state.value.currentCharacters)
        coachCoordinator.setWakePhraseRequired(_state.value.settings.wakePhraseRequired)
        coachCoordinator.setFeedbackVerbose(_state.value.settings.feedbackVerbose)
        syncCoachState(persist = false)
    }

    fun selectTab(tab: AppTab) {
        updateState { it.copy(activeTab = tab) }
    }

    fun updateCharacterWpm(value: Int) {
        updateAndPersist { it.copy(settings = it.settings.copy(characterWpm = value)) }
    }

    fun updateEffectiveWpm(value: Int) {
        updateAndPersist { it.copy(settings = it.settings.copy(effectiveWpm = value)) }
    }

    fun updateToneHz(value: Int) {
        updateAndPersist { it.copy(settings = it.settings.copy(toneHz = value)) }
    }

    fun updateDarkMode(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(darkMode = enabled)) }
    }

    fun updateSoundEnabled(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(soundEnabled = enabled)) }
    }

    fun updateVibrationEnabled(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(vibrationEnabled = enabled)) }
    }

    fun updateHighlightPlaybackEnabled(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(highlightPlaybackEnabled = enabled)) }
    }

    fun updateTrainingSetRepeatCount(value: Int) {
        val clamped = if (value == TRAINING_SET_REPEAT_ENDLESS) {
            TRAINING_SET_REPEAT_ENDLESS
        } else {
            value.coerceIn(MIN_TRAINING_SET_REPEAT_COUNT, MAX_TRAINING_SET_REPEAT_COUNT)
        }
        updateAndPersist { it.copy(settings = it.settings.copy(trainingSetRepeatCount = clamped)) }
    }

    fun updateHandsFreeEnabled(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(handsFreeEnabled = enabled)) }
        if (!enabled) {
            handleCoachCommand(CoachVoiceCommand.STOP)
        }
    }

    fun updateWakePhraseRequired(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(wakePhraseRequired = enabled)) }
        coachCoordinator.setWakePhraseRequired(enabled)
    }

    fun updateFeedbackVerbose(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(feedbackVerbose = enabled)) }
        coachCoordinator.setFeedbackVerbose(enabled)
    }

    fun setPlaying(value: Boolean) {
        updateState { it.copy(isPlaying = value) }
    }

    fun setCurrentIteration(iteration: Int) {
        updateState { it.copy(currentIteration = iteration.coerceAtLeast(0)) }
    }

    fun resetCurrentIteration() {
        setCurrentIteration(0)
    }

    fun setHighlightedCharacter(character: Char?) {
        updateState { it.copy(highlightedCharacter = character) }
    }

    fun advanceToNextCharacter() {
        val current = _state.value.currentCharacters
        val next = KochSequence.full().firstOrNull { it !in current } ?: return
        val updated = current + next
        coachCoordinator.setCurrentCharacters(updated)
        updateAndPersist {
            it.copy(
                currentCharacters = updated,
                nextCharacter = KochSequence.full().firstOrNull { char -> char !in updated }
            )
        }
    }

    fun removeLatestCharacter() {
        val current = _state.value.currentCharacters
        if (current.size <= BASE_CURRICULUM_SIZE) return

        val updated = current.dropLast(1)
        coachCoordinator.setCurrentCharacters(updated)
        updateAndPersist {
            it.copy(
                currentCharacters = updated,
                nextCharacter = KochSequence.full().firstOrNull { char -> char !in updated }
            )
        }
    }

    fun requestPlaybackStop() {
        updateState { it.copy(stopPlaybackRequested = true) }
    }

    fun clearPlaybackStopRequest() {
        updateState { it.copy(stopPlaybackRequested = false) }
    }

    fun isPlaybackStopRequested(): Boolean = _state.value.stopPlaybackRequested

    fun nextRandomizedTrainingSet(): List<Char> {
        val current = _state.value.currentCharacters
        if (current.size < 2) return current

        var randomized = shuffler(current)
        if (randomized == current) {
            randomized = current.drop(1) + current.first()
        }
        return randomized
    }

    fun handleCoachCommand(command: CoachVoiceCommand, nowMs: Long = System.currentTimeMillis()) {
        coachCoordinator.handleCommand(command, nowMs)
        syncCoachState(persist = true)
    }

    fun handleCoachTranscript(transcript: String, nowMs: Long = System.currentTimeMillis()) {
        coachCoordinator.handleTranscript(transcript, nowMs)
        syncCoachState(persist = true)
    }

    suspend fun pollCoachVoiceCommand(
        timeoutMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): CoachVoiceCommand? {
        val command = coachCoordinator.pollVoiceCommand(timeoutMs, nowMs)
        syncCoachState(persist = true)
        return command
    }

    suspend fun captureCoachAttempt(
        expectedChar: Char,
        playPrompt: suspend (assistLevel: Int) -> Unit
    ): VoiceAttempt {
        return coachCoordinator.captureAttempt(
            expectedChar = expectedChar,
            unlockedCharacters = _state.value.currentCharacters,
            playPrompt = playPrompt
        )
    }

    suspend fun awaitCoachNarrationIdle(maxWaitMs: Long = 2_500L) {
        coachCoordinator.awaitNarrationIdle(maxWaitMs)
    }

    fun onCoachRoundCompleted(attempts: List<VoiceAttempt>, nowMs: Long = System.currentTimeMillis()) {
        coachCoordinator.onRoundCompleted(attempts, nowMs)
        syncCoachState(persist = true)
    }

    private fun loadInitialState(): DitDaUiState {
        val loaded = stateStore.load()
        val normalizedCharacters = normalizeCharacters(loaded.currentCharacters)
        val normalizedSettings = normalizeSettings(loaded.settings)
        return DitDaUiState(
            settings = normalizedSettings,
            currentCharacters = normalizedCharacters,
            nextCharacter = KochSequence.full().firstOrNull { it !in normalizedCharacters }
        )
    }

    private fun normalizeCharacters(characters: List<Char>): List<Char> {
        val kochCharacters = KochSequence.full().toSet()
        val normalized = characters
            .map { it.uppercaseChar() }
            .filter { it in kochCharacters }
            .distinct()
        return if (normalized.isEmpty()) listOf('K', 'M') else normalized
    }

    private fun normalizeSettings(settings: DitDaSettings): DitDaSettings {
        val normalizedRepeatCount = if (settings.trainingSetRepeatCount == TRAINING_SET_REPEAT_ENDLESS) {
            TRAINING_SET_REPEAT_ENDLESS
        } else {
            settings.trainingSetRepeatCount.coerceIn(
                MIN_TRAINING_SET_REPEAT_COUNT,
                MAX_TRAINING_SET_REPEAT_COUNT
            )
        }
        return settings.copy(
            trainingSetRepeatCount = normalizedRepeatCount
        )
    }

    private fun syncCoachState(persist: Boolean) {
        val coachState = coachCoordinator.state.value
        val updated = _state.value.copy(
            currentCharacters = coachState.currentCharacters,
            nextCharacter = KochSequence.full().firstOrNull { it !in coachState.currentCharacters },
            coachState = coachState.coachState,
            roundIndex = coachState.roundIndex,
            sessionElapsedMs = coachState.sessionElapsedMs,
            lastCoachMessage = coachState.lastCoachMessage,
            voiceControlArmed = coachState.voiceControlArmed
        )
        _state.value = updated
        if (persist) {
            persistState(updated)
        }
    }

    private fun updateState(updater: (DitDaUiState) -> DitDaUiState) {
        _state.value = updater(_state.value)
    }

    private fun updateAndPersist(updater: (DitDaUiState) -> DitDaUiState) {
        val updated = updater(_state.value)
        _state.value = updated
        persistState(updated)
    }

    private fun persistState(state: DitDaUiState) {
        stateStore.save(
            DitDaPersistedState(
                settings = state.settings,
                currentCharacters = state.currentCharacters
            )
        )
    }
}
