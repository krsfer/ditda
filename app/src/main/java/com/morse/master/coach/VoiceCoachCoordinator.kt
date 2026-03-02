package com.morse.master.coach

import com.morse.master.ai.CoachDecisionEngine
import com.morse.master.domain.KochSequence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceCoachCoordinator(
    private val commandParser: CommandParser,
    private val vocabulary: PhoneticVocabulary,
    private val speechRecognizer: SpeechRecognizerGateway,
    private val narration: CoachNarrationGateway,
    private val wakePhraseGateway: WakePhraseGateway,
    private val settings: VoiceCoachSettings,
    private val orchestrator: CoachDecisionEngine,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    @Suppress("UNUSED_PARAMETER")
    private val transcriptFlow: Flow<String>? = null
) {
    private companion object {
        const val NARRATION_WAIT_STEP_MS = 50L
        const val DEFAULT_NARRATION_WAIT_TIMEOUT_MS = 2_500L
    }

    private val _state = MutableStateFlow(
        VoiceCoachSessionState(
            currentCharacters = listOf('K', 'M'),
            effectiveWpm = settings.effectiveWpm
        )
    )
    val state: StateFlow<VoiceCoachSessionState> = _state.asStateFlow()

    private var sessionStartMs: Long? = null
    private var wakePhraseRequired: Boolean = settings.wakePhraseRequired
    private var feedbackVerbose: Boolean = settings.feedbackVerbose

    fun setCurrentCharacters(characters: List<Char>) {
        val normalized = characters
            .map { it.uppercaseChar() }
            .distinct()
        if (normalized.isEmpty()) return

        _state.value = _state.value.copy(
            currentCharacters = normalized,
            newCharacterInSession = _state.value.newCharacterInSession?.takeIf { it in normalized }
        )
    }

    fun setWakePhraseRequired(required: Boolean) {
        wakePhraseRequired = required
    }

    fun setFeedbackVerbose(verbose: Boolean) {
        feedbackVerbose = verbose
    }

    fun handleTranscript(transcript: String, nowMs: Long = nowProvider()) {
        if (wakePhraseRequired && !wakePhraseGateway.isWakePhraseDetected(transcript)) {
            return
        }
        val command = commandParser.parse(
            input = transcript,
            wakePhraseRequiredOverride = wakePhraseRequired
        ) ?: return
        handleCommand(command, nowMs)
    }

    suspend fun pollVoiceCommand(
        timeoutMs: Long,
        nowMs: Long = nowProvider()
    ): CoachVoiceCommand? {
        awaitNarrationIdle()
        val response = speechRecognizer.listenForAnswer(timeoutMs) ?: return null
        val command = commandParser.parse(
            input = response.token,
            wakePhraseRequiredOverride = wakePhraseRequired
        ) ?: return null
        handleCommand(command, nowMs)
        return command
    }

    fun handleCommand(command: CoachVoiceCommand, nowMs: Long = nowProvider()) {
        when (command) {
            CoachVoiceCommand.START_SESSION -> startSession(nowMs)
            CoachVoiceCommand.PAUSE -> setPaused()
            CoachVoiceCommand.RESUME -> resumeSession()
            CoachVoiceCommand.REPEAT -> narration.speak("Repeating prompt")
            CoachVoiceCommand.SLOWER -> lowerSpeed()
            CoachVoiceCommand.FASTER -> raiseSpeed()
            CoachVoiceCommand.STOP -> stopSession(nowMs)
            CoachVoiceCommand.CONTINUE -> continueSession(nowMs)
        }
    }

    suspend fun captureAttempt(
        expectedChar: Char, 
        unlockedCharacters: List<Char>,
        playPrompt: suspend (assistLevel: Int) -> Unit
    ): VoiceAttempt {
        var assistLevel = 0
        while (assistLevel <= 2) {
            awaitNarrationIdle()
            playPrompt(assistLevel)
            delay(150L) // Wait for playPrompt (AudioTrack) to fully release audio focus
            val response = speechRecognizer.listenForAnswer(settings.answerTimeoutMs)
            if (response != null) {
                val command = commandParser.parse(
                    input = response.token,
                    wakePhraseRequiredOverride = wakePhraseRequired
                )
                if (command != null) {
                    handleCommand(command)
                    return VoiceAttempt(
                        expectedChar = expectedChar,
                        spokenToken = response.token,
                        resolvedChar = null,
                        latencyMs = response.latencyMs,
                        asrConfidence = response.confidence,
                        isCorrect = false,
                        assistLevel = assistLevel
                    )
                }

                val resolved = vocabulary.resolve(response.token, unlockedCharacters)
                val isCorrect = resolved == expectedChar
                
                if (feedbackVerbose) {
                    if (isCorrect) {
                        narration.speak("Correct")
                    } else {
                        val word = vocabulary.getWordFor(expectedChar) ?: expectedChar.toString()
                        narration.speak("Incorrect, expected $word")
                    }
                }

                return VoiceAttempt(
                    expectedChar = expectedChar,
                    spokenToken = response.token,
                    resolvedChar = resolved,
                    latencyMs = response.latencyMs,
                    asrConfidence = response.confidence,
                    isCorrect = isCorrect,
                    assistLevel = assistLevel
                )
            }

            if (assistLevel == 0) {
                if (feedbackVerbose) {
                    narration.speak("No answer, replaying")
                }
            }
            if (assistLevel == 1) {
                if (feedbackVerbose) {
                    narration.speak("No answer, slowing down")
                }
            }
            assistLevel += 1
        }

        return VoiceAttempt(
            expectedChar = expectedChar,
            spokenToken = null,
            resolvedChar = null,
            latencyMs = (settings.answerTimeoutMs * 3L).toInt(),
            asrConfidence = 0.0,
            isCorrect = false,
            assistLevel = 2
        )
    }

    suspend fun awaitNarrationIdle(maxWaitMs: Long = DEFAULT_NARRATION_WAIT_TIMEOUT_MS) {
        if (maxWaitMs <= 0L) return
        var remainingMs = maxWaitMs
        while (narration.isSpeaking() && remainingMs > 0L) {
            val delayMs = remainingMs.coerceAtMost(NARRATION_WAIT_STEP_MS)
            delay(delayMs)
            remainingMs -= delayMs
        }
    }

    fun onRoundCompleted(attempts: List<VoiceAttempt>, nowMs: Long = nowProvider()) {
        if (_state.value.coachState == CoachState.STOPPED) return

        val roundMetrics = VoiceRoundMetrics.fromAttempts(attempts)
        val previous = _state.value
        val stable = roundMetrics.isStable()
        val stableRounds = if (stable) previous.consecutiveStableRounds + 1 else 0
        val unstableRounds = if (stable) 0 else previous.unstableRounds + 1

        val decision = if (previous.progressionFrozen) {
            CoachDecision.KEEP_LIST
        } else {
            orchestrator.decide(
                currentList = previous.currentCharacters,
                roundMetrics = roundMetrics,
                consecutiveStableRounds = stableRounds,
                unstableRounds = unstableRounds,
                newCharacter = previous.newCharacterInSession,
                sessionExpansions = previous.sessionExpansions,
                maxSessionExpansions = settings.maxSessionExpansions,
                stableRoundsRequired = settings.stableRoundsRequired
            )
        }

        var updatedCharacters = previous.currentCharacters
        var sessionExpansions = previous.sessionExpansions
        var reinforceRoundsRemaining = previous.reinforceRoundsRemaining
        var progressionFrozen = previous.progressionFrozen
        var newCharacterInSession = previous.newCharacterInSession
        var effectiveWpm = previous.effectiveWpm

        when (decision) {
            CoachDecision.EXPAND_LIST -> {
                val next = KochSequence.full().firstOrNull { it !in updatedCharacters }
                if (next != null && sessionExpansions < settings.maxSessionExpansions) {
                    updatedCharacters = updatedCharacters + next
                    newCharacterInSession = next
                    sessionExpansions += 1
                    reinforceRoundsRemaining = 0
                    narration.speak("Adding $next")
                }
            }

            CoachDecision.REINFORCE_NEW_CHAR -> {
                reinforceRoundsRemaining = 2
                narration.speak("Reinforcing $newCharacterInSession")
            }

            CoachDecision.REDUCE_SPEED -> {
                effectiveWpm = (effectiveWpm - 1).coerceAtLeast(settings.minEffectiveWpm)
                narration.speak("Reducing speed")
            }

            CoachDecision.FREEZE_PROGRESS -> {
                progressionFrozen = true
                narration.speak("Progression frozen for this session")
            }

            CoachDecision.KEEP_LIST -> Unit
        }

        val elapsed = (sessionStartMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: previous.sessionElapsedMs)
        val nextState = if (elapsed >= settings.sessionDurationMs) {
            CoachState.BREAK_PROMPT
        } else {
            CoachState.ROUND_ACTIVE
        }

        _state.value = previous.copy(
            coachState = nextState,
            roundIndex = previous.roundIndex + 1,
            sessionElapsedMs = elapsed,
            currentCharacters = updatedCharacters,
            consecutiveStableRounds = stableRounds,
            unstableRounds = unstableRounds,
            sessionExpansions = sessionExpansions,
            progressionFrozen = progressionFrozen,
            reinforceRoundsRemaining = reinforceRoundsRemaining,
            newCharacterInSession = newCharacterInSession,
            effectiveWpm = effectiveWpm,
            lastRoundMetrics = roundMetrics
        )
    }

    private fun startSession(nowMs: Long) {
        sessionStartMs = nowMs
        _state.value = _state.value.copy(
            coachState = CoachState.ROUND_ACTIVE,
            roundIndex = 0,
            sessionElapsedMs = 0L,
            lastCoachMessage = "Session started",
            voiceControlArmed = true,
            consecutiveStableRounds = 0,
            unstableRounds = 0,
            sessionExpansions = 0,
            progressionFrozen = false,
            reinforceRoundsRemaining = 0,
            newCharacterInSession = null,
            effectiveWpm = settings.effectiveWpm
        )
        narration.speak("Session started")
    }

    private fun setPaused() {
        if (_state.value.coachState == CoachState.ROUND_ACTIVE ||
            _state.value.coachState == CoachState.BREAK_PROMPT
        ) {
            _state.value = _state.value.copy(coachState = CoachState.PAUSED)
            narration.speak("Paused")
        }
    }

    private fun resumeSession() {
        if (_state.value.coachState == CoachState.PAUSED) {
            _state.value = _state.value.copy(coachState = CoachState.ROUND_ACTIVE)
            narration.speak("Resumed")
        }
    }

    private fun stopSession(nowMs: Long) {
        val elapsed = sessionStartMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: _state.value.sessionElapsedMs
        _state.value = _state.value.copy(
            coachState = CoachState.STOPPED,
            voiceControlArmed = false,
            sessionElapsedMs = elapsed,
            lastCoachMessage = "Stopped"
        )
        narration.speak("Stopped")
    }

    private fun continueSession(nowMs: Long) {
        if (_state.value.coachState == CoachState.BREAK_PROMPT) {
            sessionStartMs = nowMs
            _state.value = _state.value.copy(
                coachState = CoachState.ROUND_ACTIVE,
                sessionElapsedMs = 0L,
                unstableRounds = 0,
                consecutiveStableRounds = 0,
                sessionExpansions = 0,
                progressionFrozen = false,
                reinforceRoundsRemaining = 0,
                newCharacterInSession = null
            )
            narration.speak("Continuing")
        }
    }

    private fun lowerSpeed() {
        _state.value = _state.value.copy(
            effectiveWpm = (_state.value.effectiveWpm - 1).coerceAtLeast(settings.minEffectiveWpm)
        )
        narration.speak("Slower")
    }

    private fun raiseSpeed() {
        _state.value = _state.value.copy(
            effectiveWpm = (_state.value.effectiveWpm + 1).coerceAtMost(settings.characterWpm)
        )
        narration.speak("Faster")
    }
}
