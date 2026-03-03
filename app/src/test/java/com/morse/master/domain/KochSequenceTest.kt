package com.morse.master.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KochSequenceTest {
    @Test
    fun `returns expected LCWO order with numbers and punctuation`() {
        assertThat(KochSequence.full()).containsExactly(
            'K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'I', 'O', 'G', 'Z', 'H', 'D', 'C', 'Y',
            'F', 'X', 'Q', 'J', 'B', 'V',
            '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
            '.', ',', '?', '/', '=', '+', '-', '@'
        ).inOrder()
    }
}
