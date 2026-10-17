package com.illyaua.winamp.ui

import androidx.compose.ui.graphics.Color

/** Colours and window sizes. Sizes are in classic Winamp pixels; the root composable scales them to the screen. */
object Skin {
    const val WIDTH = 275f
    const val TITLE_H = 14f
    const val MAIN_H = 116f
    const val EQ_H = 116f
    const val PLAYLIST_MIN_H = 90f
    const val BROWSER_MIN_H = 170f

    val BodyTop = Color(0xFF4F4F68)
    val BodyBottom = Color(0xFF2E2E40)
    val BevelLight = Color(0xFF9A9AB8)
    val BevelDark = Color(0xFF111119)

    val TitleTop = Color(0xFF5E5E7C)
    val TitleBottom = Color(0xFF25252F)
    val TitleStripe = Color(0xFF8E8EAA)
    val TitleText = Color(0xFFD8D8E8)

    val ButtonFace = Color(0xFF55556F)
    val ButtonDown = Color(0xFF38384A)
    val Icon = Color(0xFFE4E4F0)
    val Label = Color(0xFFC8C8DC)

    val LcdBackground = Color(0xFF05060A)
    val LcdGreen = Color(0xFF00E600)
    val LcdDim = Color(0xFF0B5A0B)
    val LcdLabel = Color(0xFF7B7B96)

    val SpectrumTop = Color(0xFFE82A1E)
    val SpectrumMid = Color(0xFFE8D41E)
    val SpectrumBottom = Color(0xFF1EE81E)
    val SpectrumPeak = Color(0xFFD0D0D0)

    val ListBackground = Color(0xFF000000)
    val ListText = Color(0xFF00E600)
    val ListCurrent = Color(0xFFFFFFFF)
    val ListSelected = Color(0xFF0000C6)
    val ListError = Color(0xFFE85050)

    val GrooveTop = Color(0xFF0C0C12)
    val ThumbFace = Color(0xFF8A8AA8)
}
