package com.morse.master.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KochSequenceTest {
    @Test
    fun `returns expected LCWO order`() {
        assertThat(KochSequence.full()).containsExactly(
            'K','M','U','R','E','S','N','A','P','T','L','I','O','G','Z','H','D','C','Y','F','X','Q','J','B','V'
        ).inOrder()
    }
}
