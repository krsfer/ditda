package com.morse.master.coach

class PhoneticVocabulary(
    private val tokenMap: Map<Char, Set<String>> = defaultTokenMap()
) {
    fun resolve(token: String, unlockedCharacters: List<Char>): Char? {
        val normalizedToken = token.trim().lowercase()
        if (normalizedToken.isBlank()) return null

        val unlocked = unlockedCharacters.map { it.uppercaseChar() }.toSet()
        if (unlocked.isEmpty()) return null

        val directLetter = normalizedToken.singleOrNull()?.uppercaseChar()
        if (directLetter != null && directLetter in unlocked) {
            return directLetter
        }

        return unlocked.firstOrNull { char ->
            val accepted = tokenMap[char] ?: emptySet()
            normalizedToken in accepted
        }
    }

    fun getWordFor(char: Char): String? {
        return tokenMap[char.uppercaseChar()]?.firstOrNull()
    }

    companion object {
        private fun defaultTokenMap(): Map<Char, Set<String>> = mapOf(
            'A' to setOf("alpha", "ay"),
            'B' to setOf("bravo", "bee", "be"),
            'C' to setOf("charlie", "see", "sea"),
            'D' to setOf("delta", "dee"),
            'E' to setOf("echo", "ee"),
            'F' to setOf("foxtrot", "ef", "eff"),
            'G' to setOf("golf", "jee", "gee"),
            'H' to setOf("hotel", "aitch", "haitch"),
            'I' to setOf("india", "eye"),
            'J' to setOf("juliett", "juliet", "jay"),
            'K' to setOf("kilo", "kay", "okay", "ok", "hey", "gay", "cay"),
            'L' to setOf("lima", "el"),
            'M' to setOf("mike", "em"),
            'N' to setOf("november", "en"),
            'O' to setOf("oscar", "oh"),
            'P' to setOf("papa", "pee", "pea"),
            'Q' to setOf("quebec", "cue", "queue"),
            'R' to setOf("romeo", "ar", "are"),
            'S' to setOf("sierra", "es", "ess"),
            'T' to setOf("tango", "tee", "tea"),
            'U' to setOf("uniform", "you"),
            'V' to setOf("victor", "vee"),
            'W' to setOf("whiskey", "whisky", "double u", "double you"),
            'X' to setOf("xray", "x-ray", "ex"),
            'Y' to setOf("yankee", "why"),
            'Z' to setOf("zulu", "zee", "zed"),
            '1' to setOf("one", "1"),
            '2' to setOf("two", "2", "to", "too"),
            '3' to setOf("three", "3"),
            '4' to setOf("four", "4", "for"),
            '5' to setOf("five", "5"),
            '6' to setOf("six", "6"),
            '7' to setOf("seven", "7"),
            '8' to setOf("eight", "8", "ate"),
            '9' to setOf("nine", "9"),
            '0' to setOf("zero", "0", "oh"),
            '.' to setOf("period", "full stop", "dot"),
            ',' to setOf("comma"),
            '?' to setOf("question mark"),
            '/' to setOf("slash", "forward slash"),
            '=' to setOf("equals", "equal sign"),
            '+' to setOf("plus"),
            '-' to setOf("minus", "dash", "hyphen"),
            '@' to setOf("at", "at sign")
        )
    }
}
