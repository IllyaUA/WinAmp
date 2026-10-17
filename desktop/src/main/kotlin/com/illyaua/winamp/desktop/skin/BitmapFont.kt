package com.illyaua.winamp.desktop.skin

import java.awt.Graphics2D

/** Draws text with the 5x6 glyph cells in a skin's text.bmp. Letters are drawn as capitals, as in the original. */
object BitmapFont {
    private const val ROW0 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@"
    private const val ROW1 = "0123456789.:()-'!_+\\/[]^&%,=$#"
    private const val ROW2 = "ÅÖÄ?* "

    /** (column, row) of a character in text.bmp, or null for a blank. */
    fun cell(ch: Char): Pair<Int, Int>? {
        val c = ch.uppercaseChar()
        ROW0.indexOf(c).let { if (it >= 0) return it to 0 }
        ROW1.indexOf(c).let { if (it >= 0) return it to 1 }
        ROW2.indexOf(c).let { if (it >= 0 && c != ' ') return it to 2 }
        return when (c) {
            '|', ';' -> ':'.let { cell(it) }
            '<', '{' -> cell('(')
            '>', '}' -> cell(')')
            '~' -> cell('-')
            '\t' -> null
            else -> if (c == ' ') null else cell('?')
        }
    }

    fun width(text: String) = text.length * Spr.CHAR_W

    /** Draws [text] with its top-left at ([x], [y]) and returns the width used. */
    fun draw(g: Graphics2D, skin: Skin, text: String, x: Int, y: Int): Int {
        var dx = x
        for (ch in text) {
            val c = cell(ch)
            if (c != null) skin.blit(g, Sheets.TEXT, c.first * Spr.CHAR_W, c.second * Spr.CHAR_H, Spr.CHAR_W, Spr.CHAR_H, dx, y)
            dx += Spr.CHAR_W
        }
        return dx - x
    }

    /** Like [draw] but clipped to [area]; [scroll] shifts the text left by that many pixels. */
    fun drawClipped(g: Graphics2D, skin: Skin, text: String, area: Rect, scroll: Int) {
        val old = g.clip
        g.clipRect(area.x, area.y, area.w, area.h)
        draw(g, skin, text, area.x - scroll, area.y)
        g.clip = old
    }
}
