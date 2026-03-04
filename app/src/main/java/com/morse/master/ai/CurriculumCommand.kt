package com.morse.master.ai

enum class CommandType {
    EXPAND_LIST,
    KEEP_LIST,
    REMOVE_LATEST,
    SPEED_UP,
    SPEED_DOWN
}

data class CurriculumCommand(
    val type: CommandType,
    val newCharacter: Char? = null,
    val characterWpmDelta: Int = 0,
    val effectiveWpmDelta: Int = 0
)
