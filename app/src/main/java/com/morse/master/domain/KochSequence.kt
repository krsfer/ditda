package com.morse.master.domain

object KochSequence {
    private val order = listOf(
        'K','M','U','R','E','S','N','A','P','T','L','I','O','G','Z','H','D','C','Y','F','X','Q','J','B','V'
    )

    fun full(): List<Char> = order
}
