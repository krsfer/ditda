package com.morse.master.ui

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DitDaViewModelTest {
    @Test
    fun `updates settings and tab state`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.activeTab).isEqualTo(AppTab.PRACTICE)
            assertThat(initial.settings.darkMode).isTrue()
            assertThat(initial.settings.soundEnabled).isTrue()
            assertThat(initial.settings.vibrationEnabled).isTrue()
            assertThat(initial.settings.highlightPlaybackEnabled).isTrue()
            assertThat(initial.settings.trainingSetRepeatCount).isEqualTo(0)
            assertThat(initial.settings.randomizeTrainingSetOrder).isTrue()
            assertThat(initial.settings.handsFreeEnabled).isFalse()
            assertThat(initial.settings.wakePhraseRequired).isTrue()
            assertThat(initial.settings.feedbackVerbose).isFalse()
            assertThat(initial.textPlaybackInput).isEmpty()
            assertThat(initial.textPlaybackActive).isFalse()
            assertThat(initial.textPlaybackPaused).isFalse()
            assertThat(initial.textPlaybackProgress).isEqualTo(0)
            assertThat(initial.textPlaybackLoopEnabled).isFalse()
            assertThat(initial.textPlaybackCurrentIndex).isNull()

            vm.selectTab(AppTab.SETTINGS)
            assertThat(awaitItem().activeTab).isEqualTo(AppTab.SETTINGS)

            vm.updateCharacterWpm(32)
            assertThat(awaitItem().settings.characterWpm).isEqualTo(32)

            vm.updateEffectiveWpm(12)
            assertThat(awaitItem().settings.effectiveWpm).isEqualTo(12)

            vm.updateToneHz(750)
            assertThat(awaitItem().settings.toneHz).isEqualTo(750)

            vm.updateDarkMode(false)
            assertThat(awaitItem().settings.darkMode).isFalse()

            vm.updateSoundEnabled(false)
            assertThat(awaitItem().settings.soundEnabled).isFalse()

            vm.updateVibrationEnabled(false)
            assertThat(awaitItem().settings.vibrationEnabled).isFalse()

            vm.updateHighlightPlaybackEnabled(false)
            assertThat(awaitItem().settings.highlightPlaybackEnabled).isFalse()

            vm.updateTrainingSetRepeatCount(4)
            assertThat(awaitItem().settings.trainingSetRepeatCount).isEqualTo(4)

            vm.updateTrainingSetRepeatCount(9)
            assertThat(awaitItem().settings.trainingSetRepeatCount).isEqualTo(9)

            vm.updateTrainingSetRepeatCount(42)
            assertThat(awaitItem().settings.trainingSetRepeatCount).isEqualTo(10)

            vm.updateTrainingSetRepeatCount(-8)
            assertThat(awaitItem().settings.trainingSetRepeatCount).isEqualTo(0)

            vm.updateTrainingSetRepeatCount(TRAINING_SET_REPEAT_ENDLESS)
            assertThat(awaitItem().settings.trainingSetRepeatCount).isEqualTo(TRAINING_SET_REPEAT_ENDLESS)

            vm.updateRandomizeTrainingSetOrder(false)
            assertThat(awaitItem().settings.randomizeTrainingSetOrder).isFalse()

            vm.updateHandsFreeEnabled(true)
            assertThat(awaitItem().settings.handsFreeEnabled).isTrue()

            vm.updateWakePhraseRequired(false)
            assertThat(awaitItem().settings.wakePhraseRequired).isFalse()

            vm.updateFeedbackVerbose(true)
            assertThat(awaitItem().settings.feedbackVerbose).isTrue()
        }
    }

    @Test
    fun `applies 30 WPM expert preset and persists it`() {
        val store = FakeDitDaStateStore(loadedState = DitDaPersistedState())
        val vm = DitDaViewModel(stateStore = store)

        vm.updateCharacterWpm(18)
        vm.updateEffectiveWpm(6)
        vm.updateToneHz(500)

        vm.applyThirtyWpmBeginnerPreset()

        val settings = vm.state.value.settings
        assertThat(settings.characterWpm).isEqualTo(30)
        assertThat(settings.effectiveWpm).isEqualTo(8)
        assertThat(settings.toneHz).isEqualTo(650)

        val saved = store.lastSaved
        assertThat(saved).isNotNull()
        assertThat(saved?.settings?.characterWpm).isEqualTo(30)
        assertThat(saved?.settings?.effectiveWpm).isEqualTo(8)
        assertThat(saved?.settings?.toneHz).isEqualTo(650)
    }

    @Test
    fun `tracks text playback input state and pause controls`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            assertThat(awaitItem().textPlaybackInput).isEmpty()

            vm.updateTextPlaybackInput("CQ CQ DE F4ABC")
            assertThat(awaitItem().textPlaybackInput).isEqualTo("CQ CQ DE F4ABC")

            vm.setTextPlaybackActive(true)
            assertThat(awaitItem().textPlaybackActive).isTrue()

            vm.setTextPlaybackPaused(true)
            assertThat(awaitItem().textPlaybackPaused).isTrue()

            vm.setTextPlaybackProgress(5)
            assertThat(awaitItem().textPlaybackProgress).isEqualTo(5)

            vm.updateTextPlaybackLoopEnabled(true)
            assertThat(awaitItem().textPlaybackLoopEnabled).isTrue()

            vm.setTextPlaybackCurrentIndex(3)
            assertThat(awaitItem().textPlaybackCurrentIndex).isEqualTo(3)

            vm.resetTextPlaybackProgress()
            assertThat(awaitItem().textPlaybackProgress).isEqualTo(0)

            vm.clearTextPlaybackCurrentIndex()
            assertThat(awaitItem().textPlaybackCurrentIndex).isNull()

            vm.setTextPlaybackActive(false)
            assertThat(awaitItem().textPlaybackActive).isFalse()
        }
    }

    @Test
    fun `advances curriculum by adding next Koch character`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            val initial = awaitItem()
            assertThat(initial.currentCharacters).containsExactly('K', 'M').inOrder()
            assertThat(initial.nextCharacter).isEqualTo('U')

            vm.advanceToNextCharacter()
            val advanced = awaitItem()
            assertThat(advanced.currentCharacters).containsExactly('K', 'M', 'U').inOrder()
            assertThat(advanced.nextCharacter).isEqualTo('R')
        }
    }

    @Test
    fun `removes latest curriculum character and restores it as next`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            assertThat(awaitItem().currentCharacters).containsExactly('K', 'M').inOrder()

            vm.advanceToNextCharacter()
            assertThat(awaitItem().currentCharacters).containsExactly('K', 'M', 'U').inOrder()

            vm.removeLatestCharacter()
            val removed = awaitItem()
            assertThat(removed.currentCharacters).containsExactly('K', 'M').inOrder()
            assertThat(removed.nextCharacter).isEqualTo('U')
        }
    }

    @Test
    fun `does not remove below base curriculum`() {
        val vm = DitDaViewModel()

        vm.removeLatestCharacter()

        assertThat(vm.state.value.currentCharacters).containsExactly('K', 'M').inOrder()
        assertThat(vm.state.value.nextCharacter).isEqualTo('U')
    }

    @Test
    fun `does not duplicate character when advancing repeatedly at end of sequence`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            var state = awaitItem()
            repeat(com.morse.master.domain.KochSequence.full().size - 2) {
                vm.advanceToNextCharacter()
                state = awaitItem()
            }
            val distinctCount = state.currentCharacters.distinct().size
            assertThat(state.currentCharacters.size).isEqualTo(distinctCount)
            assertThat(state.nextCharacter).isNull()
        }
    }

    @Test
    fun `returns randomized play sequence based on current set`() {
        val vm = DitDaViewModel(shuffler = { it.reversed() })

        val sequence = vm.nextRandomizedTrainingSet()
        assertThat(sequence).containsExactly('M', 'K').inOrder()

        vm.advanceToNextCharacter()
        val expandedSequence = vm.nextRandomizedTrainingSet()
        assertThat(expandedSequence).containsExactly('U', 'M', 'K').inOrder()
    }

    @Test
    fun `returns in-order play sequence when randomization setting is disabled`() {
        val vm = DitDaViewModel(shuffler = { it.reversed() })

        vm.updateRandomizeTrainingSetOrder(false)

        val sequence = vm.nextTrainingSetForPlayback()
        assertThat(sequence).containsExactly('K', 'M').inOrder()
    }

    @Test
    fun `randomized playback varies across consecutive training-set requests`() {
        val vm = DitDaViewModel(shuffler = { it })

        val first = vm.nextTrainingSetForPlayback()
        val second = vm.nextTrainingSetForPlayback()

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `avoids consecutive duplicate order when shuffler returns original`() {
        val vm = DitDaViewModel(shuffler = { it })

        val first = vm.nextRandomizedTrainingSet()
        val second = vm.nextRandomizedTrainingSet()
        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `tracks currently highlighted character`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            assertThat(awaitItem().highlightedCharacter).isNull()
            vm.setHighlightedCharacter('K')
            assertThat(awaitItem().highlightedCharacter).isEqualTo('K')
            vm.setHighlightedCharacter(null)
            assertThat(awaitItem().highlightedCharacter).isNull()
        }
    }

    @Test
    fun `tracks current playback iteration`() = runTest {
        val vm = DitDaViewModel()

        vm.state.test {
            assertThat(awaitItem().currentIteration).isEqualTo(0)
            vm.setCurrentIteration(2)
            assertThat(awaitItem().currentIteration).isEqualTo(2)
            vm.resetCurrentIteration()
            assertThat(awaitItem().currentIteration).isEqualTo(0)
        }
    }

    @Test
    fun `stop playback request can be raised and cleared`() {
        val vm = DitDaViewModel()

        assertThat(vm.isPlaybackStopRequested()).isFalse()
        vm.requestPlaybackStop()
        assertThat(vm.isPlaybackStopRequested()).isTrue()
        vm.clearPlaybackStopRequest()
        assertThat(vm.isPlaybackStopRequested()).isFalse()
    }

    @Test
    fun `coach commands synchronize coach state fields`() {
        val vm = DitDaViewModel()

        assertThat(vm.state.value.coachState).isEqualTo(com.morse.master.coach.CoachState.IDLE)
        assertThat(vm.state.value.voiceControlArmed).isFalse()

        vm.handleCoachCommand(com.morse.master.coach.CoachVoiceCommand.START_SESSION, nowMs = 0L)
        assertThat(vm.state.value.coachState).isEqualTo(com.morse.master.coach.CoachState.ROUND_ACTIVE)
        assertThat(vm.state.value.voiceControlArmed).isTrue()

        vm.handleCoachCommand(com.morse.master.coach.CoachVoiceCommand.PAUSE, nowMs = 100L)
        assertThat(vm.state.value.coachState).isEqualTo(com.morse.master.coach.CoachState.PAUSED)

        vm.handleCoachCommand(com.morse.master.coach.CoachVoiceCommand.RESUME, nowMs = 200L)
        assertThat(vm.state.value.coachState).isEqualTo(com.morse.master.coach.CoachState.ROUND_ACTIVE)

        vm.handleCoachCommand(com.morse.master.coach.CoachVoiceCommand.STOP, nowMs = 300L)
        assertThat(vm.state.value.coachState).isEqualTo(com.morse.master.coach.CoachState.STOPPED)
        assertThat(vm.state.value.voiceControlArmed).isFalse()
    }

    @Test
    fun `loads and saves current level and settings with state store`() {
        val store = FakeDitDaStateStore(
            loadedState = DitDaPersistedState(
                settings = DitDaSettings(
                    characterWpm = 20,
                    effectiveWpm = 10,
                    toneHz = 700,
                    soundEnabled = false,
                    vibrationEnabled = true,
                    highlightPlaybackEnabled = false,
                    trainingSetRepeatCount = TRAINING_SET_REPEAT_ENDLESS,
                    randomizeTrainingSetOrder = false,
                    darkMode = false,
                    handsFreeEnabled = true,
                    wakePhraseRequired = false,
                    feedbackVerbose = true
                ),
                currentCharacters = listOf('K', 'M', 'U'),
                textPlaybackInput = "CQ CQ DE F4ABC",
                textPlaybackLoopEnabled = true
            )
        )

        val vm = DitDaViewModel(stateStore = store, shuffler = { it })
        val loaded = vm.state.value
        assertThat(loaded.currentCharacters).containsExactly('K', 'M', 'U').inOrder()
        assertThat(loaded.nextCharacter).isEqualTo('R')
        assertThat(loaded.settings.toneHz).isEqualTo(700)
        assertThat(loaded.settings.soundEnabled).isFalse()
        assertThat(loaded.settings.highlightPlaybackEnabled).isFalse()
        assertThat(loaded.settings.trainingSetRepeatCount).isEqualTo(TRAINING_SET_REPEAT_ENDLESS)
        assertThat(loaded.settings.randomizeTrainingSetOrder).isFalse()
        assertThat(loaded.settings.handsFreeEnabled).isTrue()
        assertThat(loaded.settings.wakePhraseRequired).isFalse()
        assertThat(loaded.settings.feedbackVerbose).isTrue()
        assertThat(loaded.textPlaybackInput).isEqualTo("CQ CQ DE F4ABC")
        assertThat(loaded.textPlaybackLoopEnabled).isTrue()

        vm.updateCharacterWpm(25)
        vm.updateTextPlaybackInput("VVV DE TEST")
        vm.updateTextPlaybackLoopEnabled(false)
        vm.advanceToNextCharacter()
        vm.removeLatestCharacter()

        val saved = store.lastSaved
        assertThat(saved).isNotNull()
        assertThat(saved?.settings?.characterWpm).isEqualTo(25)
        assertThat(saved?.currentCharacters).containsExactly('K', 'M', 'U').inOrder()
        assertThat(saved?.textPlaybackInput).isEqualTo("VVV DE TEST")
        assertThat(saved?.textPlaybackLoopEnabled).isFalse()
    }
}

private class FakeDitDaStateStore(
    private val loadedState: DitDaPersistedState
) : DitDaStateStore {
    var lastSaved: DitDaPersistedState? = null

    override fun load(): DitDaPersistedState = loadedState

    override fun save(state: DitDaPersistedState) {
        lastSaved = state
    }
}
