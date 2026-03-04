package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import com.morse.master.coach.CoachState
import org.junit.Test

class SessionScreenStateTest {
    @Test
    fun `keeps characters in a single row when count is at most six`() {
        val rows = characterGridRows(listOf('K', 'M', 'U', 'R', 'E', 'S'))

        assertThat(rows).containsExactly(
            listOf('K', 'M', 'U', 'R', 'E', 'S')
        )
    }

    @Test
    fun `wraps characters to a second row after six columns`() {
        val rows = characterGridRows(listOf('K', 'M', 'U', 'R', 'E', 'S', 'N'))

        assertThat(rows).containsExactly(
            listOf('K', 'M', 'U', 'R', 'E', 'S'),
            listOf('N')
        ).inOrder()
    }

    @Test
    fun `preserves character order while wrapping rows of six`() {
        val rows = characterGridRows(listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I'))

        assertThat(rows).containsExactly(
            listOf('K', 'M', 'U', 'R', 'E', 'S'),
            listOf('N', 'A', 'P', 'T', 'L', 'W'),
            listOf('I')
        ).inOrder()
    }

    @Test
    fun `disables character buttons while single character playback is active and keeps highlight state`() {
        val playingHighlighted = characterButtonVisualState(
            playbackMode = PlaybackMode.SINGLE_CHAR,
            character = 'K',
            highlightedCharacter = 'K'
        )
        val playingNotHighlighted = characterButtonVisualState(
            playbackMode = PlaybackMode.SINGLE_CHAR,
            character = 'M',
            highlightedCharacter = 'K'
        )

        assertThat(playingHighlighted.enabled).isFalse()
        assertThat(playingNotHighlighted.enabled).isFalse()
        assertThat(playingHighlighted.isHighlighted).isTrue()
        assertThat(playingNotHighlighted.isHighlighted).isFalse()
    }

    @Test
    fun `keeps character buttons enabled while training set playback is active`() {
        val trainingSetButton = characterButtonVisualState(
            playbackMode = PlaybackMode.TRAINING_SET,
            character = 'K',
            highlightedCharacter = null
        )

        assertThat(trainingSetButton.enabled).isTrue()
    }

    @Test
    fun `returns latest removable character only when set has more than base size`() {
        assertThat(latestRemovableCharacter(listOf('K', 'M'))).isNull()
        assertThat(latestRemovableCharacter(listOf('K', 'M', 'U'))).isEqualTo('U')
    }

    @Test
    fun `builds playback iteration counter text for finite and endless modes`() {
        assertThat(
            playbackIterationCounterText(
                playbackMode = PlaybackMode.IDLE,
                currentIteration = 0,
                repeatCount = 0
            )
        ).isEqualTo("Training Sets: 0 / 1 (0%)")

        assertThat(
            playbackIterationCounterText(
                playbackMode = PlaybackMode.TRAINING_SET,
                currentIteration = 3,
                repeatCount = 2
            )
        ).isEqualTo("Training Sets: 3 / 3 (100%)")

        assertThat(
            playbackIterationCounterText(
                playbackMode = PlaybackMode.TRAINING_SET,
                currentIteration = 7,
                repeatCount = TRAINING_SET_REPEAT_ENDLESS
            )
        ).isEqualTo("Training Sets: 7 / Endless (7%)")
    }

    @Test
    fun `builds progress bar state for finite and endless playback`() {
        val finite = trainingSetProgressBarState(
            playbackMode = PlaybackMode.TRAINING_SET,
            currentIteration = 2,
            repeatCount = 2
        )
        assertThat(finite.progress).isWithin(0.0001f).of(2f / 3f)
        assertThat(finite.percentage).isEqualTo(67)

        val endless = trainingSetProgressBarState(
            playbackMode = PlaybackMode.TRAINING_SET,
            currentIteration = 7,
            repeatCount = TRAINING_SET_REPEAT_ENDLESS
        )
        assertThat(endless.progress).isWithin(0.0001f).of(0.07f)
        assertThat(endless.percentage).isEqualTo(7)
    }

    @Test
    fun `builds level-based progress state and text`() {
        val baseLevel = trainingLevelProgressBarState(
            currentTrainingLevels = 2,
            maxTrainingLevels = 25
        )
        assertThat(baseLevel.progress).isWithin(0.0001f).of(2f / 25f)
        assertThat(baseLevel.percentage).isEqualTo(8)

        val advancedLevel = trainingLevelProgressBarState(
            currentTrainingLevels = 3,
            maxTrainingLevels = 25
        )
        assertThat(advancedLevel.progress).isWithin(0.0001f).of(3f / 25f)
        assertThat(advancedLevel.percentage).isEqualTo(12)

        assertThat(
            trainingLevelProgressText(
                currentTrainingLevels = 3,
                maxTrainingLevels = 25
            )
        ).isEqualTo("Training Level: 3 / 25 (12%)")
    }

    @Test
    fun `disables manual tone controls while coach session is active`() {
        assertThat(isCoachSessionInProgress(CoachState.ROUND_ACTIVE)).isTrue()
        assertThat(
            isManualCharacterInputEnabled(
                playbackMode = PlaybackMode.IDLE,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isFalse()
        assertThat(
            isPlayTrainingSetEnabled(
                playbackMode = PlaybackMode.IDLE,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isFalse()
    }

    @Test
    fun `keeps play training set enabled only to stop active manual playback`() {
        assertThat(
            isPlayTrainingSetEnabled(
                playbackMode = PlaybackMode.TRAINING_SET,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isTrue()
    }

    @Test
    fun `disables coach start while manual playback is running`() {
        assertThat(
            isCoachStartEnabled(
                coachState = CoachState.IDLE,
                playbackMode = PlaybackMode.TRAINING_SET
            )
        ).isFalse()
    }

    @Test
    fun `disables manual character input during text playback`() {
        assertThat(
            isManualCharacterInputEnabled(
                playbackMode = PlaybackMode.TEXT,
                coachState = CoachState.IDLE
            )
        ).isFalse()
    }

    @Test
    fun `disables manual character input during single character playback`() {
        assertThat(
            isManualCharacterInputEnabled(
                playbackMode = PlaybackMode.SINGLE_CHAR,
                coachState = CoachState.IDLE
            )
        ).isFalse()
    }

    @Test
    fun `prioritizes highlighted over problem and easy visual states`() {
        val highlighted = characterButtonVisualState(
            playbackMode = PlaybackMode.TRAINING_SET,
            character = 'K',
            highlightedCharacter = 'K',
            problemCharacters = setOf('K'),
            easyCharacters = setOf('K')
        )
        val problem = characterButtonVisualState(
            playbackMode = PlaybackMode.TRAINING_SET,
            character = 'M',
            highlightedCharacter = null,
            problemCharacters = setOf('M'),
            easyCharacters = emptySet()
        )
        val easy = characterButtonVisualState(
            playbackMode = PlaybackMode.TRAINING_SET,
            character = 'U',
            highlightedCharacter = null,
            problemCharacters = emptySet(),
            easyCharacters = setOf('U')
        )

        assertThat(highlighted.isHighlighted).isTrue()
        assertThat(highlighted.isProblem).isFalse()
        assertThat(highlighted.isEasy).isFalse()
        assertThat(problem.isProblem).isTrue()
        assertThat(problem.isEasy).isFalse()
        assertThat(easy.isEasy).isTrue()
        assertThat(easy.isProblem).isFalse()
    }
}
