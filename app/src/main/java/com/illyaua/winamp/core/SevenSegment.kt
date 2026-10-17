package com.illyaua.winamp.core

/** Segment bit masks for the LCD clock: A top, B top right, C bottom right, D bottom, E bottom left, F top left, G middle. */
object SevenSegment {
    const val A = 1
    const val B = 2
    const val C = 4
    const val D = 8
    const val E = 16
    const val F = 32
    const val G = 64

    fun mask(ch: Char): Int = when (ch) {
        '0' -> A or B or C or D or E or F
        '1' -> B or C
        '2' -> A or B or D or E or G
        '3' -> A or B or C or D or G
        '4' -> B or C or F or G
        '5' -> A or C or D or F or G
        '6' -> A or C or D or E or F or G
        '7' -> A or B or C
        '8' -> A or B or C or D or E or F or G
        '9' -> A or B or C or D or F or G
        '-' -> G
        else -> 0
    }
}
