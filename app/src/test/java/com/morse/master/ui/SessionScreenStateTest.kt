package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionScreenStateTest {
    @Test
    fun `keeps character buttons enabled while playing and highlights only active character`() {
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

        assertThat(playingHighlighted.enabled).isTrue()
        assertThat(playingNotHighlighted.enabled).isTrue()
        assertThat(playingHighlighted.isHighlighted).isTrue()
        assertThat(playingNotHighlighted.isHighlighted).isFalse()
    }

    @Test
    fun `returns latest removable character only when set has more than base size`() {
        assertThat(latestRemovableCharacter(listOf('K', 'M'))).isNull()
        assertThat(latestRemovableCharacter(listOf('K', 'M', 'U'))).isEqualTo('U')
    }
}
