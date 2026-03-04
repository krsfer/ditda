package com.morse.master.session

data class TrainingSetTapEvent(
    val expected: Char,
    val actual: Char,
    val latencyMs: Int
) {
    val isCorrect: Boolean
        get() = expected == actual
}
