package com.morse.master.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeminiNanoGatewayTest {
    @Test
    fun `maps busy status to retryable unavailable`() {
        val gateway = GeminiNanoGateway(fakeStatus = "BUSY")
        assertThat(gateway.checkAvailability()).isEqualTo(NanoAvailability.RETRYABLE_UNAVAILABLE)
    }
}
