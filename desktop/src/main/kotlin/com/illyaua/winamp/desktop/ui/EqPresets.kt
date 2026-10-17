package com.illyaua.winamp.desktop.ui

/** Band gains in dB for the ten bands (60 Hz to 16 kHz), plus preamp. */
object EqPresets {
    class Preset(val name: String, val preamp: Float, val bands: FloatArray)

    private fun p(name: String, vararg b: Int) = Preset(name, 0f, FloatArray(10) { b[it].toFloat() })

    val ALL = listOf(
        p("Classical", 0, 0, 0, 0, 0, 0, -7, -7, -7, -9),
        p("Club", 0, 0, 8, 5, 5, 5, 3, 0, 0, 0),
        p("Dance", 9, 7, 2, 0, 0, -5, -7, -7, 0, 0),
        p("Flat", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        p("Full Bass", -8, 9, 9, 5, 1, -4, -8, -10, -11, -11),
        p("Full Bass & Treble", 7, 5, 0, -7, -4, 1, 8, 11, 12, 12),
        p("Full Treble", -9, -9, -9, -4, 2, 11, 12, 12, 12, 12),
        p("Headphones", 4, 11, 5, -3, -2, 1, 4, 9, 12, 12),
        p("Large Hall", 10, 10, 5, 5, 0, -4, -4, -4, 0, 0),
        p("Live", -4, 0, 4, 5, 5, 5, 4, 2, 2, 2),
        p("Party", 7, 7, 0, 0, 0, 0, 0, 0, 7, 7),
        p("Pop", -1, 4, 7, 8, 5, 0, -2, -2, -1, -1),
        p("Reggae", 0, 0, 0, -5, 0, 6, 6, 0, 0, 0),
        p("Rock", 8, 4, -5, -8, -3, 4, 8, 11, 11, 11),
        p("Ska", -2, -4, -4, 0, 4, 5, 8, 9, 11, 9),
        p("Soft", 4, 1, 0, -2, 0, 4, 8, 9, 11, 12),
        p("Soft Rock", 4, 4, 2, 0, -4, -5, -3, 0, 2, 8),
        p("Techno", 8, 5, 0, -5, -4, 0, 8, 9, 9, 8),
    )
}
