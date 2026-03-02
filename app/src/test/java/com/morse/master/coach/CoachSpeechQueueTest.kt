package com.morse.master.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoachSpeechQueueTest {
    @Test
    fun `drains queued messages in fifo order`() {
        val queue = CoachSpeechQueue(maxSize = 4)
        queue.enqueue("one")
        queue.enqueue("two")

        val spoken = mutableListOf<String>()
        queue.drain(spoken::add)

        assertThat(spoken).containsExactly("one", "two").inOrder()
    }

    @Test
    fun `drops oldest message when max size is exceeded`() {
        val queue = CoachSpeechQueue(maxSize = 2)
        queue.enqueue("one")
        queue.enqueue("two")
        queue.enqueue("three")

        val spoken = mutableListOf<String>()
        queue.drain(spoken::add)

        assertThat(spoken).containsExactly("two", "three").inOrder()
    }
}
