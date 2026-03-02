package com.morse.master.coach

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.morse.master.ai.CoachDecisionEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoiceCoachCoordinatorTest {
    @Test
    fun `transitions from idle to active round to break prompt after session duration`() = runTest {
        val coordinator = buildCoordinator(
            settings = VoiceCoachSettings(sessionDurationMs = 1_000L)
        )

        coordinator.state.test {
            assertThat(awaitItem().coachState).isEqualTo(CoachState.IDLE)

            coordinator.handleCommand(CoachVoiceCommand.START_SESSION, nowMs = 0L)
            assertThat(awaitItem().coachState).isEqualTo(CoachState.ROUND_ACTIVE)

            coordinator.onRoundCompleted(stableAttempts(), nowMs = 1_001L)
            assertThat(awaitItem().coachState).isEqualTo(CoachState.BREAK_PROMPT)
        }
    }

    @Test
    fun `escalates assist level when two answer windows time out`() = runTest {
        val speech = QueueSpeechRecognizerGateway(
            responses = listOf(
                null,
                null,
                SpeechRecognitionResult(token = "kilo", confidence = 0.91, latencyMs = 330)
            )
        )
        val coordinator = buildCoordinator(speechGateway = speech)

        val attempt = coordinator.captureAttempt(
            expectedChar = 'K',
            unlockedCharacters = listOf('K', 'M')
        ) {}

        assertThat(attempt.assistLevel).isEqualTo(2)
        assertThat(attempt.isCorrect).isTrue()
        assertThat(attempt.resolvedChar).isEqualTo('K')
    }

    @Test
    fun `capture attempt handles spoken coach command during round`() = runTest {
        val speech = QueueSpeechRecognizerGateway(
            responses = listOf(
                SpeechRecognitionResult(
                    token = "Coach stop",
                    confidence = 0.92,
                    latencyMs = 180
                )
            )
        )
        val coordinator = buildCoordinator(speechGateway = speech)
        coordinator.handleCommand(CoachVoiceCommand.START_SESSION, nowMs = 0L)

        val attempt = coordinator.captureAttempt(
            expectedChar = 'K',
            unlockedCharacters = listOf('K', 'M')
        ) {}

        assertThat(attempt.isCorrect).isFalse()
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.STOPPED)
    }

    @Test
    fun `capture attempt waits for narration to finish before opening microphone`() = runTest {
        val speech = QueueSpeechRecognizerGateway(
            responses = listOf(
                SpeechRecognitionResult(
                    token = "kilo",
                    confidence = 0.95,
                    latencyMs = 210
                )
            )
        )
        val narration = RecordingNarrationGateway(speaking = true)
        val coordinator = buildCoordinator(speechGateway = speech, narrationGateway = narration)

        val capture = async {
            coordinator.captureAttempt(
                expectedChar = 'K',
                unlockedCharacters = listOf('K', 'M')
            ) {}
        }

        kotlinx.coroutines.delay(250L)
        assertThat(speech.listenCalls).isEqualTo(0)

        narration.speaking = false
        val attempt = capture.await()
        assertThat(speech.listenCalls).isEqualTo(1)
        assertThat(attempt.isCorrect).isTrue()
    }

    @Test
    fun `caps expansions to one per session`() {
        val coordinator = buildCoordinator(
            settings = VoiceCoachSettings(
                sessionDurationMs = 100_000L,
                maxSessionExpansions = 1
            )
        )

        coordinator.handleCommand(CoachVoiceCommand.START_SESSION, nowMs = 0L)

        coordinator.onRoundCompleted(stableAttempts(), nowMs = 100L)
        coordinator.onRoundCompleted(stableAttempts(), nowMs = 200L)
        assertThat(coordinator.state.value.currentCharacters).containsExactly('K', 'M', 'U').inOrder()

        coordinator.onRoundCompleted(stableAttempts(), nowMs = 300L)
        coordinator.onRoundCompleted(stableAttempts(), nowMs = 400L)
        assertThat(coordinator.state.value.currentCharacters).containsExactly('K', 'M', 'U').inOrder()
    }

    @Test
    fun `handles pause resume and stop commands`() {
        val coordinator = buildCoordinator()

        coordinator.handleCommand(CoachVoiceCommand.START_SESSION, nowMs = 0L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.ROUND_ACTIVE)

        coordinator.handleCommand(CoachVoiceCommand.PAUSE, nowMs = 100L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.PAUSED)

        coordinator.handleCommand(CoachVoiceCommand.RESUME, nowMs = 200L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.ROUND_ACTIVE)

        coordinator.handleCommand(CoachVoiceCommand.STOP, nowMs = 300L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.STOPPED)
        assertThat(coordinator.state.value.voiceControlArmed).isFalse()
    }

    @Test
    fun `reinforces new character on unstable post expansion rounds and freezes after four unstable rounds`() {
        val coordinator = buildCoordinator(
            settings = VoiceCoachSettings(sessionDurationMs = 100_000L)
        )

        coordinator.handleCommand(CoachVoiceCommand.START_SESSION, nowMs = 0L)
        coordinator.onRoundCompleted(stableAttempts(), nowMs = 100L)
        coordinator.onRoundCompleted(stableAttempts(), nowMs = 200L)
        assertThat(coordinator.state.value.currentCharacters).containsExactly('K', 'M', 'U').inOrder()

        coordinator.onRoundCompleted(unstableAttempts(topMiss = 'U'), nowMs = 300L)
        assertThat(coordinator.state.value.reinforceRoundsRemaining).isEqualTo(2)

        coordinator.onRoundCompleted(unstableAttempts(topMiss = 'M'), nowMs = 400L)
        coordinator.onRoundCompleted(unstableAttempts(topMiss = 'M'), nowMs = 500L)
        coordinator.onRoundCompleted(unstableAttempts(topMiss = 'M'), nowMs = 600L)

        assertThat(coordinator.state.value.progressionFrozen).isTrue()
    }

    @Test
    fun `wake phrase requirement can be toggled at runtime`() {
        val coordinator = buildCoordinator()

        coordinator.handleTranscript("start session", nowMs = 0L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.IDLE)

        coordinator.setWakePhraseRequired(false)
        coordinator.handleTranscript("start session", nowMs = 0L)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.ROUND_ACTIVE)
    }

    @Test
    fun `poll voice command can start session from microphone transcript`() = runTest {
        val coordinator = buildCoordinator(
            speechGateway = QueueSpeechRecognizerGateway(
                responses = listOf(
                    SpeechRecognitionResult(
                        token = "Coach start session",
                        confidence = 0.93,
                        latencyMs = 200
                    )
                )
            )
        )

        val command = coordinator.pollVoiceCommand(timeoutMs = 1_000L, nowMs = 0L)

        assertThat(command).isEqualTo(CoachVoiceCommand.START_SESSION)
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.ROUND_ACTIVE)
    }

    @Test
    fun `poll voice command ignores non command transcripts`() = runTest {
        val coordinator = buildCoordinator(
            speechGateway = QueueSpeechRecognizerGateway(
                responses = listOf(
                    SpeechRecognitionResult(
                        token = "random speech",
                        confidence = 0.4,
                        latencyMs = 250
                    )
                )
            )
        )

        val command = coordinator.pollVoiceCommand(timeoutMs = 1_000L, nowMs = 0L)

        assertThat(command).isNull()
        assertThat(coordinator.state.value.coachState).isEqualTo(CoachState.IDLE)
    }

    private fun buildCoordinator(
        settings: VoiceCoachSettings = VoiceCoachSettings(),
        speechGateway: SpeechRecognizerGateway = QueueSpeechRecognizerGateway(emptyList()),
        narrationGateway: RecordingNarrationGateway = RecordingNarrationGateway()
    ): VoiceCoachCoordinator {
        val transcriptFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
        return VoiceCoachCoordinator(
            commandParser = CommandParser(wakePhraseRequired = true),
            vocabulary = PhoneticVocabulary(),
            speechRecognizer = speechGateway,
            narration = narrationGateway,
            wakePhraseGateway = PrefixWakePhraseGateway(),
            settings = settings,
            orchestrator = CoachDecisionEngine(),
            nowProvider = { 0L },
            transcriptFlow = transcriptFlow
        )
    }

    private fun stableAttempts(): List<VoiceAttempt> = List(10) {
        VoiceAttempt(
            expectedChar = if (it % 2 == 0) 'K' else 'M',
            spokenToken = if (it % 2 == 0) "kilo" else "mike",
            resolvedChar = if (it % 2 == 0) 'K' else 'M',
            latencyMs = 350,
            asrConfidence = 0.9,
            isCorrect = true,
            assistLevel = 0
        )
    }

    private fun unstableAttempts(topMiss: Char): List<VoiceAttempt> {
        val attempts = mutableListOf<VoiceAttempt>()
        repeat(6) {
            attempts += VoiceAttempt(
                expectedChar = topMiss,
                spokenToken = "wrong",
                resolvedChar = null,
                latencyMs = 700,
                asrConfidence = 0.5,
                isCorrect = false,
                assistLevel = 2
            )
        }
        repeat(4) {
            attempts += VoiceAttempt(
                expectedChar = 'K',
                spokenToken = "kilo",
                resolvedChar = 'K',
                latencyMs = 700,
                asrConfidence = 0.5,
                isCorrect = true,
                assistLevel = 0
            )
        }
        return attempts
    }
}

private class QueueSpeechRecognizerGateway(
    responses: List<SpeechRecognitionResult?>
) : SpeechRecognizerGateway {
    private val queue = ArrayDeque<SpeechRecognitionResult?>(responses)
    var listenCalls: Int = 0

    override suspend fun listenForAnswer(timeoutMs: Long): SpeechRecognitionResult? {
        listenCalls += 1
        return if (queue.isEmpty()) null else queue.removeFirst()
    }
}

private class RecordingNarrationGateway : CoachNarrationGateway {
    @Volatile
    var speaking: Boolean = false
    val messages = mutableListOf<String>()

    constructor()
    constructor(speaking: Boolean) {
        this.speaking = speaking
    }

    override fun speak(message: String) {
        messages += message
    }

    override fun isSpeaking(): Boolean = speaking
}
