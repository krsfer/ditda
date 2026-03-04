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
    fun `builds preview with highlight on active character only when no chars yet played`() {
        val preview = highlightedTextPlaybackPreview(
            normalizedInput = "CQ",
            highlightedIndex = 1,
            playedUpTo = 0,
            highlightColor = Color.Red,
            playedColor = Color.Blue
        )

        assertThat(preview.text).isEqualTo("CQ")
        assertThat(preview.spanStyles).hasSize(1)
        val span = preview.spanStyles.single()
        assertThat(span.start).isEqualTo(1)
        assertThat(span.end).isEqualTo(2)
        assertThat(span.item.background).isEqualTo(Color.Red)
    }

    @Test
    fun `builds preview with played chars shaded before active character`() {
        val preview = highlightedTextPlaybackPreview(
            normalizedInput = "CQD",
            highlightedIndex = 2,
            playedUpTo = 3,
            highlightColor = Color.Red,
            playedColor = Color.Blue
        )

        assertThat(preview.text).isEqualTo("CQD")
        val spans = preview.spanStyles
        assertThat(spans).hasSize(3) // C played, Q played, D active
        val playedC = spans.first { it.start == 0 }
        assertThat(playedC.end).isEqualTo(1)
        assertThat(playedC.item.background).isEqualTo(Color.Blue)
        val playedQ = spans.first { it.start == 1 }
        assertThat(playedQ.end).isEqualTo(2)
        assertThat(playedQ.item.background).isEqualTo(Color.Blue)
        val active = spans.first { it.start == 2 }
        assertThat(active.end).isEqualTo(3)
        assertThat(active.item.background).isEqualTo(Color.Red)
    }

    @Test
    fun `builds preview with played chars shaded and skips space characters`() {
        val preview = highlightedTextPlaybackPreview(
            normalizedInput = "A B",
            highlightedIndex = 2,
            playedUpTo = 3,
            highlightColor = Color.Red,
            playedColor = Color.Blue
        )

        assertThat(preview.text).isEqualTo("A B")
        val spans = preview.spanStyles
        // 'A' played, ' ' skipped, 'B' active
        assertThat(spans).hasSize(2)
        val playedA = spans.first { it.start == 0 }
        assertThat(playedA.item.background).isEqualTo(Color.Blue)
        val activeB = spans.first { it.start == 2 }
        assertThat(activeB.item.background).isEqualTo(Color.Red)
    }

    @Test
    fun `builds preview with all chars shaded when playback complete and no current index`() {
        val preview = highlightedTextPlaybackPreview(
            normalizedInput = "CQ",
            highlightedIndex = null,
            playedUpTo = 2,
            highlightColor = Color.Red,
            playedColor = Color.Blue
        )

        assertThat(preview.text).isEqualTo("CQ")
        assertThat(preview.spanStyles).hasSize(2)
        assertThat(preview.spanStyles.all { it.item.background == Color.Blue }).isTrue()
    }
}
