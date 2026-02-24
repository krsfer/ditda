package com.morse.master.ai

enum class CommandType { EXPAND_LIST, KEEP_LIST }

data class CurriculumCommand(
    val type: CommandType,
    val newCharacter: Char? = null
)
