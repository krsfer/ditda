package com.morse.master.ui

import com.morse.master.ai.CommandType
import com.morse.master.ai.CoachDecisionEngine
import com.morse.master.ai.CurriculumCommand
import com.morse.master.ai.CurriculumDecisionEngine
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
import com.morse.master.session.TrainingSetPerformanceSnapshot
import com.morse.master.session.TrainingSetPerformanceTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppTab {
    PRACTICE,
    TEXT,
    SETTINGS
}

enum class PlaybackMode {
    IDLE,
    SINGLE_CHAR,
    TRAINING_SET,
    TEXT
}

const val TRAINING_SET_REPEAT_ENDLESS = -1

data class DitDaSettings(
    val characterWpm: Int = 30,
    val effectiveWpm: Int = 8,
    val toneHz: Int = 600,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val highlightPlaybackEnabled: Boolean = true,
    val trainingSetRepeatCount: Int = 0,
    val randomizeTrainingSetOrder: Boolean = true,
    val darkMode: Boolean = true,
    val handsFreeEnabled: Boolean = false,
    val wakePhraseRequired: Boolean = true,
    val feedbackVerbose: Boolean = false,
    val ultraPhaseEnabled: Boolean = false
)

data class SpeedPreset(
    val characterWpm: Int,
    val effectiveWpm: Int,
    val toneHz: Int
)

val Expert30WpmPreset = SpeedPreset(
    characterWpm = 30,
    effectiveWpm = 8,
    toneHz = 650
)

data class DitDaUiState(
    val activeTab: AppTab = AppTab.PRACTICE,
    val settings: DitDaSettings = DitDaSettings(),
    val currentCharacters: List<Char> = listOf('K', 'M'),
    val nextCharacter: Char? = 'U',
    val playbackMode: PlaybackMode = PlaybackMode.IDLE,
    val isPlaying: Boolean = false,
    val currentIteration: Int = 0,
    val highlightedCharacter: Char? = null,
    val problemCharacters: Set<Char> = emptySet(),
    val easyCharacters: Set<Char> = emptySet(),
    val adaptationDebugLine: String = "Adapt: stable=0 unstable=0 last=KEEP_LIST",
    val stopPlaybackRequested: Boolean = false,
    val coachState: CoachState = CoachState.IDLE,
    val roundIndex: Int = 0,
    val sessionElapsedMs: Long = 0L,
    val lastCoachMessage: String? = null,
    val voiceControlArmed: Boolean = false,
    val textPlaybackInput: String = "",
    val textPlaybackActive: Boolean = false,
    val textPlaybackPaused: Boolean = false,
    val textPlaybackProgress: Int = 0,
    val textPlaybackLoopEnabled: Boolean = false,
    val textPlaybackCurrentIndex: Int? = null
)

data class TrainingSetTapResult(
    val expected: Char?,
    val actual: Char,
    val isCorrect: Boolean,
    val correctionCharacter: Char?,
    val latencyMs: Int
)

class DitDaViewModel(
    private val stateStore: DitDaStateStore = InMemoryDitDaStateStore(),
    private val shuffler: (List<Char>) -> List<Char> = { chars -> chars.shuffled() },
    private val curriculumDecisionEngine: CurriculumDecisionEngine = CurriculumDecisionEngine(),
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
        private const val MIN_CHARACTER_WPM = 10
        private const val MAX_CHARACTER_WPM = 60
        private const val MIN_EFFECTIVE_WPM = 5
    }

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<DitDaUiState> = _state.asStateFlow()
    private var lastRandomizedTrainingSet: List<Char>? = null
    private val trainingSetPerformanceTracker = TrainingSetPerformanceTracker()
    private var expectedTrainingCharacter: Char? = null
    private var expectedTrainingCharacterStartedAtMs: Long? = null
    private var expectedTrainingCharacterSatisfied: Boolean = false
    private var pendingCorrectionCharacter: Char? = null
    private var stableIterations: Int = 0
    private var unstableIterations: Int = 0
    private var lastTrainingSetCommand: CommandType = CommandType.KEEP_LIST

    init {
        coachCoordinator.setCurrentCharacters(_state.value.currentCharacters)
        coachCoordinator.setWakePhraseRequired(_state.value.settings.wakePhraseRequired)
        coachCoordinator.setFeedbackVerbose(_state.value.settings.feedbackVerbose)
        coachCoordinator.setUltraPhaseEnabled(_state.value.settings.ultraPhaseEnabled)
        coachCoordinator.setSpeedProfile(
            characterWpm = _state.value.settings.characterWpm,
            effectiveWpm = _state.value.settings.effectiveWpm
        )
        syncCoachState(persist = false)
    }

    fun selectTab(tab: AppTab) {
        updateState { it.copy(activeTab = tab) }
    }

    fun updateCharacterWpm(value: Int) {
        updateAndPersist {
            val clampedCharacter = value.coerceIn(MIN_CHARACTER_WPM, MAX_CHARACTER_WPM)
            val clampedEffective = it.settings.effectiveWpm.coerceAtMost(clampedCharacter)
            val updatedSettings = it.settings.copy(
                characterWpm = clampedCharacter,
                effectiveWpm = clampedEffective
            )
            syncCoachSettings(updatedSettings)
            it.copy(settings = updatedSettings)
        }
    }

    fun updateEffectiveWpm(value: Int) {
        updateAndPersist {
            val clampedEffective = value.coerceIn(
                MIN_EFFECTIVE_WPM,
                it.settings.characterWpm
            )
            val updatedSettings = it.settings.copy(effectiveWpm = clampedEffective)
            syncCoachSettings(updatedSettings)
            it.copy(settings = updatedSettings)
        }
    }

    fun updateToneHz(value: Int) {
        updateAndPersist { it.copy(settings = it.settings.copy(toneHz = value)) }
    }

    fun applyThirtyWpmBeginnerPreset() {
        updateAndPersist {
            val updatedSettings = it.settings.copy(
                characterWpm = Expert30WpmPreset.characterWpm,
                effectiveWpm = Expert30WpmPreset.effectiveWpm,
                toneHz = Expert30WpmPreset.toneHz,
                ultraPhaseEnabled = false
            )
            syncCoachSettings(updatedSettings)
            it.copy(
                settings = updatedSettings
            )
        }
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

    fun updateRandomizeTrainingSetOrder(enabled: Boolean) {
        updateAndPersist { it.copy(settings = it.settings.copy(randomizeTrainingSetOrder = enabled)) }
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

    fun updateUltraPhaseEnabled(enabled: Boolean) {
        updateAndPersist {
            val updatedSettings = it.settings.copy(ultraPhaseEnabled = enabled)
            syncCoachSettings(updatedSettings)
            it.copy(settings = updatedSettings)
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        updateState {
            it.copy(
                playbackMode = mode,
                isPlaying = mode != PlaybackMode.IDLE
            )
        }
    }

    fun setPlaying(value: Boolean) {
        if (!value) {
            setPlaybackMode(PlaybackMode.IDLE)
            return
        }
        val currentMode = _state.value.playbackMode
        val mode = if (currentMode == PlaybackMode.IDLE) {
            PlaybackMode.SINGLE_CHAR
        } else {
            currentMode
        }
        setPlaybackMode(mode)
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

    fun beginExpectedTrainingCharacter(character: Char, startedAtMs: Long) {
        expectedTrainingCharacter = character.uppercaseChar()
        expectedTrainingCharacterStartedAtMs = startedAtMs
        expectedTrainingCharacterSatisfied = false
    }

    fun clearExpectedTrainingCharacter() {
        expectedTrainingCharacter = null
        expectedTrainingCharacterStartedAtMs = null
        expectedTrainingCharacterSatisfied = false
    }

    fun onTrainingSetTap(actual: Char, nowMs: Long): TrainingSetTapResult {
        val expected = expectedTrainingCharacter
        val normalizedActual = actual.uppercaseChar()
        if (expected == null) {
            return TrainingSetTapResult(
                expected = null,
                actual = normalizedActual,
                isCorrect = false,
                correctionCharacter = null,
                latencyMs = 0
            )
        }

        val startedAt = expectedTrainingCharacterStartedAtMs ?: nowMs
        val latencyMs = (nowMs - startedAt).coerceAtLeast(0L).toInt()
        trainingSetPerformanceTracker.record(
            expected = expected,
            actual = normalizedActual,
            latencyMs = latencyMs
        )
        val snapshot = trainingSetPerformanceTracker.snapshot()
        updateState {
            it.copy(
                problemCharacters = snapshot.problemCharacters,
                easyCharacters = snapshot.easyCharacters
            )
        }

        val correct = expected == normalizedActual
        if (correct) {
            expectedTrainingCharacterSatisfied = true
        }
        if (!correct) {
            pendingCorrectionCharacter = expected
        }
        return TrainingSetTapResult(
            expected = expected,
            actual = normalizedActual,
            isCorrect = correct,
            correctionCharacter = if (correct) null else expected,
            latencyMs = latencyMs
        )
    }

    fun consumePendingCorrectionCharacter(): Char? {
        val correction = pendingCorrectionCharacter
        pendingCorrectionCharacter = null
        return correction
    }

    fun shouldAdvanceTrainingSetCharacter(nowMs: Long, timeoutMs: Long): Boolean {
        val expected = expectedTrainingCharacter ?: return true
        if (expectedTrainingCharacterSatisfied) {
            return true
        }
        val startedAt = expectedTrainingCharacterStartedAtMs ?: return true
        val elapsed = nowMs - startedAt
        if (elapsed >= timeoutMs.coerceAtLeast(0L)) {
            return true
        }
        return expected !in _state.value.currentCharacters
    }

    fun shouldHighlightTrainingCharacter(character: Char): Boolean {
        if (!_state.value.settings.highlightPlaybackEnabled) return false

        val normalized = character.uppercaseChar()
        val snapshot = trainingSetPerformanceTracker.snapshot()
        if (snapshot.totalChars < 3) return false

        if (normalized in snapshot.problemCharacters) return true
        if (unstableIterations > 0 && normalized in snapshot.failedChars) return true
        return false
    }

    fun resetTrainingSetAdaptiveSession() {
        trainingSetPerformanceTracker.clear()
        clearExpectedTrainingCharacter()
        pendingCorrectionCharacter = null
        stableIterations = 0
        unstableIterations = 0
        lastTrainingSetCommand = CommandType.KEEP_LIST
        updateState {
            it.copy(
                problemCharacters = emptySet(),
                easyCharacters = emptySet(),
                adaptationDebugLine = adaptationDebugLine()
            )
        }
    }

    fun currentTrainingSetPerformanceSnapshot(): TrainingSetPerformanceSnapshot {
        return trainingSetPerformanceTracker.snapshot()
    }

    fun evaluateTrainingSetAdaptation(): CurriculumCommand {
        val snapshot = trainingSetPerformanceTracker.snapshot()
        val stable = snapshot.totalChars > 0 &&
            snapshot.accuracyPercent >= 90 &&
            snapshot.medianLatencyMs <= 900 &&
            snapshot.problemCharacters.isEmpty()
        val unstable = snapshot.totalChars > 0 &&
            (snapshot.accuracyPercent < 75 ||
                snapshot.medianLatencyMs > 1_800)

        stableIterations = if (stable) stableIterations + 1 else 0
        unstableIterations = if (unstable) unstableIterations + 1 else 0

        val command = curriculumDecisionEngine.decideTrainingSetAdjustment(
            currentList = _state.value.currentCharacters,
            metrics = snapshot,
            stableIterations = stableIterations,
            unstableIterations = unstableIterations,
            characterWpm = _state.value.settings.characterWpm,
            effectiveWpm = _state.value.settings.effectiveWpm,
            minCharacterWpm = MIN_CHARACTER_WPM,
            minEffectiveWpm = MIN_EFFECTIVE_WPM
        )
        applyCurriculumCommand(command)
        if (command.type != CommandType.KEEP_LIST) {
            stableIterations = 0
            unstableIterations = 0
        }
        updateState {
            it.copy(
                adaptationDebugLine = adaptationDebugLine()
            )
        }
        return command
    }

    fun applyCurriculumCommand(command: CurriculumCommand) {
        lastTrainingSetCommand = command.type
        when (command.type) {
            CommandType.KEEP_LIST -> Unit
            CommandType.EXPAND_LIST -> {
                val commandCharacter = command.newCharacter
                if (commandCharacter != null && commandCharacter !in _state.value.currentCharacters) {
                    val updated = _state.value.currentCharacters + commandCharacter
                    coachCoordinator.setCurrentCharacters(updated)
                    updateAndPersist {
                        it.copy(
                            currentCharacters = updated,
                            nextCharacter = KochSequence.full().firstOrNull { char -> char !in updated }
                        )
                    }
                } else {
                    advanceToNextCharacter()
                }
            }
            CommandType.REMOVE_LATEST -> removeLatestCharacter()
            CommandType.SPEED_UP -> increaseSpeedsBalanced()
            CommandType.SPEED_DOWN -> decreaseSpeedsBalanced()
        }
        updateState {
            it.copy(
                adaptationDebugLine = adaptationDebugLine()
            )
        }
    }

    fun updateTextPlaybackInput(value: String) {
        updateAndPersist { it.copy(textPlaybackInput = value) }
    }

    fun updateTextPlaybackLoopEnabled(enabled: Boolean) {
        updateAndPersist { it.copy(textPlaybackLoopEnabled = enabled) }
    }

    fun setTextPlaybackActive(active: Boolean) {
        updateState { it.copy(textPlaybackActive = active) }
    }

    fun setTextPlaybackPaused(paused: Boolean) {
        updateState { it.copy(textPlaybackPaused = paused) }
    }

    fun setTextPlaybackProgress(progress: Int) {
        updateState { it.copy(textPlaybackProgress = progress.coerceAtLeast(0)) }
    }

    fun resetTextPlaybackProgress() {
        setTextPlaybackProgress(0)
    }

    fun setTextPlaybackCurrentIndex(index: Int?) {
        updateState { it.copy(textPlaybackCurrentIndex = index) }
    }

    fun clearTextPlaybackCurrentIndex() {
        setTextPlaybackCurrentIndex(null)
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
        val previous = lastRandomizedTrainingSet
        if (previous != null && randomized == previous) {
            randomized = current.drop(1) + current.first()
            if (randomized == previous && current.size > 2) {
                randomized = current.drop(2) + current.take(2)
            }
        }
        lastRandomizedTrainingSet = randomized
        return randomized
    }

    fun nextTrainingSetForPlayback(): List<Char> {
        return if (_state.value.settings.randomizeTrainingSetOrder) {
            nextRandomizedTrainingSet()
        } else {
            _state.value.currentCharacters
        }
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

    private fun increaseSpeedsBalanced() {
        updateAndPersist {
            val settings = it.settings
            val updatedSettings = if (settings.effectiveWpm < settings.characterWpm) {
                settings.copy(effectiveWpm = settings.effectiveWpm + 1)
            } else if (settings.characterWpm < MAX_CHARACTER_WPM) {
                val raisedCharacter = settings.characterWpm + 1
                settings.copy(
                    characterWpm = raisedCharacter,
                    effectiveWpm = (settings.effectiveWpm + 1).coerceAtMost(raisedCharacter)
                )
            } else {
                settings
            }
            syncCoachSettings(updatedSettings)
            it.copy(settings = updatedSettings)
        }
    }

    private fun decreaseSpeedsBalanced() {
        updateAndPersist {
            val settings = it.settings
            val updatedSettings = if (settings.effectiveWpm > MIN_EFFECTIVE_WPM) {
                settings.copy(effectiveWpm = settings.effectiveWpm - 1)
            } else if (settings.characterWpm > MIN_CHARACTER_WPM) {
                val loweredCharacter = settings.characterWpm - 1
                settings.copy(
                    characterWpm = loweredCharacter,
                    effectiveWpm = settings.effectiveWpm.coerceAtMost(loweredCharacter)
                )
            } else {
                settings
            }
            syncCoachSettings(updatedSettings)
            it.copy(settings = updatedSettings)
        }
    }

    private fun loadInitialState(): DitDaUiState {
        val loaded = stateStore.load()
        val normalizedCharacters = normalizeCharacters(loaded.currentCharacters)
        val normalizedSettings = normalizeSettings(loaded.settings)
        return DitDaUiState(
            settings = normalizedSettings,
            currentCharacters = normalizedCharacters,
            nextCharacter = KochSequence.full().firstOrNull { it !in normalizedCharacters },
            textPlaybackInput = loaded.textPlaybackInput,
            textPlaybackLoopEnabled = loaded.textPlaybackLoopEnabled
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
        val normalizedCharacterWpm = settings.characterWpm.coerceIn(MIN_CHARACTER_WPM, MAX_CHARACTER_WPM)
        val normalizedEffectiveWpm = settings.effectiveWpm.coerceIn(
            MIN_EFFECTIVE_WPM,
            normalizedCharacterWpm
        )
        return settings.copy(
            characterWpm = normalizedCharacterWpm,
            effectiveWpm = normalizedEffectiveWpm,
            trainingSetRepeatCount = normalizedRepeatCount
        )
    }

    private fun syncCoachState(persist: Boolean) {
        val coachState = coachCoordinator.state.value
        val updated = _state.value.copy(
            settings = _state.value.settings.copy(
                characterWpm = coachState.characterWpm,
                effectiveWpm = coachState.effectiveWpm
            ),
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

    private fun syncCoachSettings(settings: DitDaSettings) {
        coachCoordinator.setUltraPhaseEnabled(settings.ultraPhaseEnabled)
        coachCoordinator.setSpeedProfile(
            characterWpm = settings.characterWpm,
            effectiveWpm = settings.effectiveWpm
        )
    }

    private fun adaptationDebugLine(): String {
        return "Adapt: stable=$stableIterations unstable=$unstableIterations last=$lastTrainingSetCommand"
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
                currentCharacters = state.currentCharacters,
                textPlaybackInput = state.textPlaybackInput,
                textPlaybackLoopEnabled = state.textPlaybackLoopEnabled
            )
        )
    }
}
