package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import com.morse.master.coach.CoachState
import org.junit.Test

class SessionScreenStateTest {
    @Test
    fun `disables character buttons while manual playback is active and keeps highlight state`() {
        val playingHighlighted = characterButtonVisualState(
            isPlaying = true,
            character = 'K',
            highlightedCharacter = 'K'
        )
        val playingNotHighlighted = characterButtonVisualState(
            isPlaying = true,
            character = 'M',
            highlightedCharacter = 'K'
        )

        assertThat(playingHighlighted.enabled).isFalse()
        assertThat(playingNotHighlighted.enabled).isFalse()
        assertThat(playingHighlighted.isHighlighted).isTrue()
        assertThat(playingNotHighlighted.isHighlighted).isFalse()
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
                isPlaying = false,
                currentIteration = 0,
                repeatCount = 0
            )
        ).isEqualTo("Training Sets: 0 / 1 (0%)")

        assertThat(
            playbackIterationCounterText(
                isPlaying = true,
                currentIteration = 3,
                repeatCount = 2
            )
        ).isEqualTo("Training Sets: 3 / 3 (100%)")

        assertThat(
            playbackIterationCounterText(
                isPlaying = true,
                currentIteration = 7,
                repeatCount = TRAINING_SET_REPEAT_ENDLESS
            )
        ).isEqualTo("Training Sets: 7 / Endless (7%)")
    }

    @Test
    fun `builds progress bar state for finite and endless playback`() {
        val finite = trainingSetProgressBarState(
            isPlaying = true,
            currentIteration = 2,
            repeatCount = 2
        )
        assertThat(finite.progress).isWithin(0.0001f).of(2f / 3f)
        assertThat(finite.percentage).isEqualTo(67)

        val endless = trainingSetProgressBarState(
            isPlaying = true,
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
                isPlaying = false,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isFalse()
        assertThat(
            isPlayTrainingSetEnabled(
                isPlaying = false,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isFalse()
    }

    @Test
    fun `keeps play training set enabled only to stop active manual playback`() {
        assertThat(
            isPlayTrainingSetEnabled(
                isPlaying = true,
                coachState = CoachState.ROUND_ACTIVE
            )
        ).isTrue()
    }

    @Test
    fun `disables coach start while manual playback is running`() {
        assertThat(
            isCoachStartEnabled(
                coachState = CoachState.IDLE,
                isPlaying = true
            )
        ).isFalse()
    }
}
