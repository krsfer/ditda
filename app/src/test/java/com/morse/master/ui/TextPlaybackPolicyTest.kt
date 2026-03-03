package com.morse.master.ui

import com.google.common.truth.Truth.assertThat
import androidx.compose.ui.graphics.Color
import org.junit.Test

class TextPlaybackPolicyTest {
    @Test
    fun `normalizes pasted text to playable morse characters and single spaces`() {
        val normalized = normalizeTextPlaybackInput("  cq\n\nde f4abc?!   ###   @  ")

        assertThat(normalized).isEqualTo("CQ DE F4ABC? @")
    }

    @Test
    fun `accepts punctuation and digits supported by current morse planner`() {
        val normalized = normalizeTextPlaybackInput("1/2 = 0.5 + @")

        assertThat(normalized).isEqualTo("1/2 = 0.5 + @")
    }

    @Test
    fun `enables start only with playable text and when playback is idle`() {
        assertThat(
            isTextPlaybackStartEnabled(
                input = "CQ",
                isPlaying = false,
                textPlaybackActive = false
            )
        ).isTrue()

        assertThat(
            isTextPlaybackStartEnabled(
                input = "   ",
                isPlaying = false,
                textPlaybackActive = false
            )
        ).isFalse()

        assertThat(
            isTextPlaybackStartEnabled(
                input = "CQ",
                isPlaying = true,
                textPlaybackActive = false
            )
        ).isFalse()
    }

    @Test
    fun `exposes highlighted index only for active non-space character`() {
        assertThat(
            textPlaybackHighlightedIndex(
                normalizedInput = "CQ TEST",
                currentIndex = 1
            )
        ).isEqualTo(1)

        assertThat(
            textPlaybackHighlightedIndex(
                normalizedInput = "CQ TEST",
                currentIndex = 2
            )
        ).isNull()

        assertThat(
            textPlaybackHighlightedIndex(
                normalizedInput = "CQ TEST",
                currentIndex = 99
            )
        ).isNull()
    }

    @Test
    fun `builds preview with temporary background highlight style for active character`() {
        val preview = highlightedTextPlaybackPreview(
            normalizedInput = "CQ",
            highlightedIndex = 1,
            highlightColor = Color.Red
        )

        assertThat(preview.text).isEqualTo("CQ")
        assertThat(preview.spanStyles).hasSize(1)
        val span = preview.spanStyles.single()
        assertThat(span.start).isEqualTo(1)
        assertThat(span.end).isEqualTo(2)
        assertThat(span.item.background).isEqualTo(Color.Red)
    }
}
