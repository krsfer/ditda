package com.morse.master.domain

object KochSequence {
    private val order = listOf(
        'K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'I', 'O', 'G', 'Z', 'H', 'D', 'C', 'Y',
        'F', 'X', 'Q', 'J', 'B', 'V', 'W',
        '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
        '.', ',', '?', '/', '=', '+', '-', '@'
    )

    fun full(): List<Char> = order
}
