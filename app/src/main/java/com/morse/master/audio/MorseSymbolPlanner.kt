package com.morse.master.audio

import com.morse.master.domain.MorseTiming
import com.morse.master.ui.DitDaSettings

sealed interface MorseSegment {
    data class Tone(val durationMs: Int) : MorseSegment
    data class Gap(val durationMs: Int) : MorseSegment
}

class MorseSymbolPlanner {
    private val patterns = mapOf(
        'A' to ".-",
        'B' to "-...",
        'C' to "-.-.",
        'D' to "-..",
        'E' to ".",
        'F' to "..-.",
        'G' to "--.",
        'H' to "....",
        'I' to "..",
        'J' to ".---",
        'K' to "-.-",
        'L' to ".-..",
        'M' to "--",
        'N' to "-.",
        'O' to "---",
        'P' to ".--.",
        'Q' to "--.-",
        'R' to ".-.",
        'S' to "...",
        'T' to "-",
        'U' to "..-",
        'V' to "...-",
        'W' to ".--",
        'X' to "-..-",
        'Y' to "-.--",
        'Z' to "--..",
        '1' to ".----",
        '2' to "..---",
        '3' to "...--",
        '4' to "....-",
        '5' to ".....",
        '6' to "-....",
        '7' to "--...",
        '8' to "---..",
        '9' to "----.",
        '0' to "-----",
        '.' to ".-.-.-",
        ',' to "--..--",
        '?' to "..--..",
        '/' to "-..-.",
        '=' to "-...-",
        '+' to ".-.-.",
        '-' to "-....-",
        '@' to ".--.-."
    )

    fun planFor(character: Char, settings: DitDaSettings): List<MorseSegment> {
        val pattern = patterns[character.uppercaseChar()] ?: return emptyList()
        val timing = MorseTiming(
            characterWpm = settings.characterWpm,
            effectiveWpm = settings.effectiveWpm
        )

        return buildList {
            pattern.forEachIndexed { index, symbol ->
                add(
                    if (symbol == '.') {
                        MorseSegment.Tone(timing.dotMs)
                    } else {
                        MorseSegment.Tone(timing.dashMs)
                    }
                )

                if (index < pattern.lastIndex) {
                    add(MorseSegment.Gap(timing.intraSymbolGapMs))
                }
            }
        }
    }
}
