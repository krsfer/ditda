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

            vm.selectTab(AppTab.SETTINGS)
            assertThat(awaitItem().activeTab).isEqualTo(AppTab.SETTINGS)

            vm.updateCharacterWpm(30)
            assertThat(awaitItem().settings.characterWpm).isEqualTo(30)

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
            repeat(23) {
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
    fun `forces different order when shuffler returns original`() {
        val vm = DitDaViewModel(shuffler = { it })

        val sequence = vm.nextRandomizedTrainingSet()
        assertThat(sequence).containsExactly('M', 'K').inOrder()
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
    fun `stop playback request can be raised and cleared`() {
        val vm = DitDaViewModel()

        assertThat(vm.isPlaybackStopRequested()).isFalse()
        vm.requestPlaybackStop()
        assertThat(vm.isPlaybackStopRequested()).isTrue()
        vm.clearPlaybackStopRequest()
        assertThat(vm.isPlaybackStopRequested()).isFalse()
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
                    darkMode = false
                ),
                currentCharacters = listOf('K', 'M', 'U')
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

        vm.updateCharacterWpm(25)
        vm.advanceToNextCharacter()
        vm.removeLatestCharacter()

        val saved = store.lastSaved
        assertThat(saved).isNotNull()
        assertThat(saved?.settings?.characterWpm).isEqualTo(25)
        assertThat(saved?.currentCharacters).containsExactly('K', 'M', 'U').inOrder()
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
