package com.morse.master.coach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneticVocabularyTest {
    private val vocabulary = PhoneticVocabulary()

    @Test
    fun `resolves known phonetic token for unlocked character`() {
        assertThat(vocabulary.resolve("Kilo", unlockedCharacters = listOf('K', 'M'))).isEqualTo('K')
        assertThat(vocabulary.resolve("mike", unlockedCharacters = listOf('K', 'M'))).isEqualTo('M')
    }

    @Test
    fun `rejects tokens for locked characters`() {
        assertThat(vocabulary.resolve("romeo", unlockedCharacters = listOf('K', 'M'))).isNull()
    }

    @Test
    fun `normalizes whitespace before lookup`() {
        assertThat(vocabulary.resolve("   kilo   ", unlockedCharacters = listOf('K', 'M'))).isEqualTo('K')
    }
}
