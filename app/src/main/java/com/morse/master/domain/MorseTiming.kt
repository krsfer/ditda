package com.morse.master.domain

data class MorseTiming(
    val characterWpm: Int,
    val effectiveWpm: Int
) {
    val dotMs: Int = 1200 / characterWpm
    val dashMs: Int = dotMs * 3
    val intraSymbolGapMs: Int = dotMs
    val interCharGapMs: Int = (1200 / effectiveWpm) * 3
}
