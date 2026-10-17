package com.illyaua.winamp.desktop.skin

data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Where each piece lives inside the classic Winamp 2.x skin bitmaps.
 * Sheet names are the bitmap file names without extension.
 */
object Sheets {
    const val MAIN = "main"
    const val CBUTTONS = "cbuttons"
    const val TITLEBAR = "titlebar"
    const val NUMBERS = "numbers"
    const val PLAYPAUS = "playpaus"
    const val MONOSTEREO = "monostereo"
    const val VOLUME = "volume"
    const val BALANCE = "balance"
    const val POSBAR = "posbar"
    const val SHUFREP = "shufrep"
    const val TEXT = "text"
    const val EQMAIN = "eqmain"
    const val PLEDIT = "pledit"

    val ALL = listOf(MAIN, CBUTTONS, TITLEBAR, NUMBERS, PLAYPAUS, MONOSTEREO, VOLUME, BALANCE, POSBAR, SHUFREP, TEXT, EQMAIN, PLEDIT)
}

object Spr {
    // ---- window sizes
    const val WIN_W = 275
    const val MAIN_H = 116
    const val EQ_H = 116
    const val TITLE_H = 14

    // ---- main window: positions on screen
    val PREV_AT = Rect(16, 88, 23, 18)
    val PLAY_AT = Rect(39, 88, 23, 18)
    val PAUSE_AT = Rect(62, 88, 23, 18)
    val STOP_AT = Rect(85, 88, 23, 18)
    val NEXT_AT = Rect(108, 88, 22, 18)
    val EJECT_AT = Rect(136, 89, 22, 16)
    val SHUFFLE_AT = Rect(164, 89, 47, 15)
    val REPEAT_AT = Rect(211, 89, 28, 15)
    val EQ_BTN_AT = Rect(219, 58, 23, 12)
    val PL_BTN_AT = Rect(242, 58, 23, 12)
    val VOLUME_AT = Rect(107, 57, 68, 13)
    val BALANCE_AT = Rect(177, 57, 38, 13)
    val SEEK_AT = Rect(16, 72, 248, 10)
    val CLOCK_AT = Rect(36, 26, 63, 13)
    val SPECTRUM_AT = Rect(24, 43, 76, 16)
    val TITLE_TEXT_AT = Rect(111, 27, 153, 6)
    val BITRATE_AT = Rect(111, 43, 15, 6)
    val KHZ_AT = Rect(156, 43, 10, 6)
    val PLAYSTATE_AT = Rect(26, 28, 9, 9)
    val MONO_AT = Rect(212, 41, 27, 12)
    val STEREO_AT = Rect(239, 41, 29, 12)
    val MENU_BTN_AT = Rect(6, 3, 9, 9)
    val MINIMIZE_AT = Rect(244, 3, 9, 9)
    val CLOSE_AT = Rect(264, 3, 9, 9)
    val TITLE_DRAG = Rect(0, 0, 275, 14)

    // ---- cbuttons.bmp (second row = pressed)
    val PREV = Rect(0, 0, 23, 18)
    val PLAY = Rect(23, 0, 23, 18)
    val PAUSE = Rect(46, 0, 23, 18)
    val STOP = Rect(69, 0, 23, 18)
    val NEXT = Rect(92, 0, 22, 18)
    val EJECT = Rect(114, 0, 22, 16)
    const val CBUTTON_PRESSED_DY = 18
    const val EJECT_PRESSED_DY = 16

    // ---- titlebar.bmp
    val TITLE_ACTIVE = Rect(27, 0, 275, 14)
    val TITLE_INACTIVE = Rect(27, 15, 275, 14)
    val TB_MENU = Rect(0, 0, 9, 9)
    val TB_MINIMIZE = Rect(9, 0, 9, 9)
    val TB_CLOSE = Rect(18, 0, 9, 9)
    const val TB_PRESSED_DY = 9

    // ---- numbers.bmp: digit i at x = i * 9; the minus sign is a 5x1 strip taken from the middle of the "2"
    const val DIGIT_W = 9
    const val DIGIT_H = 13
    val MINUS = Rect(20, 6, 5, 1)
    val CLOCK_MINUS_AT = Rect(38, 32, 5, 1)
    val CLOCK_DIGIT_X = intArrayOf(48, 60, 78, 90)
    const val CLOCK_DIGIT_Y = 26

    // ---- playpaus.bmp
    val PS_PLAY = Rect(0, 0, 9, 9)
    val PS_PAUSE = Rect(9, 0, 9, 9)
    val PS_STOP = Rect(18, 0, 9, 9)

    // ---- monostereo.bmp
    val STEREO_ON = Rect(0, 0, 29, 12)
    val STEREO_OFF = Rect(0, 12, 29, 12)
    val MONO_ON = Rect(29, 0, 27, 12)
    val MONO_OFF = Rect(29, 12, 27, 12)

    // ---- volume.bmp and balance.bmp: 28 background states 15px apart, thumbs at the bottom
    const val SLIDER_STATES = 28
    const val SLIDER_STATE_DY = 15
    const val VOLUME_BG_X = 0
    const val BALANCE_BG_X = 9
    const val SLIDER_BG_H = 13
    val SLIDER_THUMB = Rect(15, 422, 14, 11)
    val SLIDER_THUMB_PRESSED = Rect(0, 422, 14, 11)
    const val SLIDER_THUMB_W = 14

    // ---- posbar.bmp
    val POS_THUMB = Rect(248, 0, 29, 10)
    val POS_THUMB_PRESSED = Rect(278, 0, 29, 10)

    // ---- shufrep.bmp: normal, pressed, selected, selected+pressed stacked 15px (or 12px) apart
    val REPEAT = Rect(0, 0, 28, 15)
    val SHUFFLE = Rect(28, 0, 47, 15)
    const val SR_PRESSED_DY = 15
    const val SR_SELECTED_DY = 30
    const val SR_SELECTED_PRESSED_DY = 45
    val EQ_BTN = Rect(0, 61, 23, 12)
    val PL_BTN = Rect(23, 61, 23, 12)
    const val TOGGLE_PRESSED_DX = 46
    const val TOGGLE_SELECTED_DY = 12

    // ---- text.bmp: 5x6 cells
    const val CHAR_W = 5
    const val CHAR_H = 6

    // ---- eqmain.bmp
    val EQ_BG = Rect(0, 0, 275, 116)
    val EQ_TITLE_ACTIVE = Rect(0, 134, 275, 14)
    val EQ_TITLE_INACTIVE = Rect(0, 149, 275, 14)
    val EQ_THUMB = Rect(0, 164, 11, 11)
    val EQ_THUMB_PRESSED = Rect(0, 176, 11, 11)
    const val EQ_SLIDER_BG_X = 13
    const val EQ_SLIDER_BG_Y = 164
    const val EQ_SLIDER_W = 14
    const val EQ_SLIDER_H = 63
    const val EQ_SLIDER_COL_STEP = 15
    const val EQ_SLIDER_ROW_STEP = 65
    val EQ_ON = Rect(10, 119, 26, 12)
    val EQ_AUTO = Rect(36, 119, 32, 12)
    const val EQ_BTN_SELECTED_DX = 59   // selected state sits 59px right of normal
    const val EQ_BTN_PRESSED_DX = 118   // pressed state sits 118px right of normal
    val EQ_PRESETS = Rect(224, 164, 44, 12)
    const val EQ_PRESETS_PRESSED_DY = 12
    val EQ_GRAPH_COLORS = Rect(115, 294, 1, 19)
    val EQ_PREAMP_LINE = Rect(0, 314, 113, 1)
    val EQ_ON_AT = Rect(14, 18, 26, 12)
    val EQ_AUTO_AT = Rect(39, 18, 32, 12)
    val EQ_PRESETS_AT = Rect(217, 18, 44, 12)
    val EQ_GRAPH_AT = Rect(86, 17, 113, 19)
    const val EQ_PREAMP_X = 21
    const val EQ_SLIDER_Y = 38
    const val EQ_BAND_X0 = 78
    const val EQ_BAND_STEP = 18
    val EQ_THUMB_H = 11

    // ---- pledit.bmp (playlist frame pieces)
    val PL_TL_ACTIVE = Rect(0, 21, 25, 20)
    val PL_TL_INACTIVE = Rect(0, 0, 25, 20)
    val PL_TITLE_ACTIVE = Rect(26, 21, 100, 20)
    val PL_TITLE_INACTIVE = Rect(26, 0, 100, 20)
    val PL_TOP_ACTIVE = Rect(127, 21, 25, 20)
    val PL_TOP_INACTIVE = Rect(127, 0, 25, 20)
    val PL_TR_ACTIVE = Rect(153, 21, 25, 20)
    val PL_TR_INACTIVE = Rect(153, 0, 25, 20)
    val PL_LEFT = Rect(0, 42, 12, 29)
    val PL_RIGHT = Rect(31, 42, 20, 29)
    val PL_BL = Rect(0, 72, 125, 38)
    val PL_BOTTOM = Rect(179, 0, 25, 38)
    val PL_BR = Rect(126, 72, 150, 38)
    val PL_SCROLL = Rect(52, 53, 8, 18)
    val PL_SCROLL_PRESSED = Rect(61, 53, 8, 18)
    const val PL_TOP_H = 20
    const val PL_BOTTOM_H = 38
    const val PL_LEFT_W = 12
    const val PL_RIGHT_W = 20
}
