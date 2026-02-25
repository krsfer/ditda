package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import com.morse.master.domain.KochSequence
import com.morse.master.domain.MorseTiming
import org.junit.Test

class TrainingSetPlaybackPolicyTest {
    @Test
    fun `converts repeat count into total iterations`() {
        assertThat(totalTrainingSetIterations(0)).isEqualTo(1)
        assertThat(totalTrainingSetIterations(10)).isEqualTo(11)
        assertThat(totalTrainingSetIterations(TRAINING_SET_REPEAT_ENDLESS)).isNull()
    }

    @Test
    fun `uses double inter-character gap between iterations`() {
        val timing = MorseTiming(characterWpm = 25, effectiveWpm = 8)

        assertThat(interIterationPauseMs(timing)).isEqualTo(timing.interCharGapMs.toLong() * 2L)
    }

    @Test
    fun `maps repeat count to slider value and back including endless`() {
        assertThat(trainingSetRepeatSliderValue(0)).isEqualTo(0f)
        assertThat(trainingSetRepeatSliderValue(TRAINING_SET_REPEAT_ENDLESS)).isEqualTo(11f)

        assertThat(sliderValueToTrainingSetRepeatCount(0)).isEqualTo(0)
        assertThat(sliderValueToTrainingSetRepeatCount(11)).isEqualTo(TRAINING_SET_REPEAT_ENDLESS)
    }

    @Test
    fun `uses koch sequence size for maximum training levels`() {
        assertThat(maxTrainingLevels()).isEqualTo(KochSequence.full().size)
    }
}
