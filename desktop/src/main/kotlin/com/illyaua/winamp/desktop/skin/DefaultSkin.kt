package com.illyaua.winamp.desktop.skin

import com.illyaua.winamp.core.SevenSegment
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * The built-in skin. It is drawn here in code in the same sprite layout as a classic .wsz skin, so the
 * app has a single rendering path for every skin, and [exportWsz] gives skin authors a ready template.
 */
object DefaultSkin {
    private val BODY_TOP = Color(0x4C5068)
    private val BODY_BOTTOM = Color(0x2B2E3E)
    private val LIGHT = Color(0x9096B6)
    private val DARK = Color(0x11131B)
    private val WELL = Color(0x000000) // same black as the digit and text sprites, so they blend in
    private val GREEN = Color(0x00E000)
    private val GREEN_DIM = Color(0x0B4A0B)
    private val LABEL = Color(0x8C92B0)
    private val BTN_TOP = Color(0x666B87)
    private val BTN_BOTTOM = Color(0x434860)
    private val BTN_DOWN_TOP = Color(0x2E3144)
    private val BTN_DOWN_BOTTOM = Color(0x3C4058)
    private val ICON = Color(0xE6E8F4)
    private val AMBER = Color(0xE8D41E)
    private val RED = Color(0xE82A1E)

    val visColors: Array<Color> = Array(24) { i ->
        when (i) {
            0 -> Color(0, 0, 0)
            1 -> Color(0x20, 0x28, 0x20)
            in 2..17 -> {
                // row 2 is the top of the analyzer, row 17 the bottom: red, then amber, then green
                val t = (i - 2) / 15f
                if (t < 0.5f) mix(RED, AMBER, t * 2f) else mix(AMBER, Color(0x1E, 0xE8, 0x1E), (t - 0.5f) * 2f)
            }
            23 -> Color(0xD0, 0xD0, 0xD0)
            else -> Color(0xE0, 0xE0, 0xE0)
        }
    }

    val playlistColors = PlaylistColors(
        normal = Color(0x00E000),
        current = Color(0xFFFFFF),
        normalBg = Color(0x000000),
        selectedBg = Color(0x1A2A8C),
        fontName = "SansSerif",
    )

    val skin: Skin by lazy { Skin("Built-in", sheets(), playlistColors, visColors) }

    private fun mix(a: Color, b: Color, t: Float): Color {
        val u = t.coerceIn(0f, 1f)
        return Color(
            (a.red + (b.red - a.red) * u).toInt(),
            (a.green + (b.green - a.green) * u).toInt(),
            (a.blue + (b.blue - a.blue) * u).toInt(),
        )
    }

    // ---------------------------------------------------------------------------------------------------------

    private class Art(w: Int, h: Int) {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        private val g = img.createGraphics()

        init {
            g.color = Color.BLACK
            g.fillRect(0, 0, w, h)
        }

        fun fill(c: Color, x: Int, y: Int, w: Int, h: Int) {
            g.color = c
            g.fillRect(x, y, w, h)
        }

        fun px(c: Color, x: Int, y: Int) = fill(c, x, y, 1, 1)

        fun vgrad(x: Int, y: Int, w: Int, h: Int, top: Color, bottom: Color) {
            for (row in 0 until h) fill(mix(top, bottom, if (h > 1) row / (h - 1f) else 0f), x, y + row, w, 1)
        }

        fun bevel(x: Int, y: Int, w: Int, h: Int, raised: Boolean) {
            val hi = if (raised) LIGHT else DARK
            val lo = if (raised) DARK else LIGHT
            fill(hi, x, y, w, 1); fill(hi, x, y, 1, h)
            fill(lo, x, y + h - 1, w, 1); fill(lo, x + w - 1, y, 1, h)
        }

        fun body(x: Int, y: Int, w: Int, h: Int) {
            vgrad(x, y, w, h, BODY_TOP, BODY_BOTTOM)
            bevel(x, y, w, h, true)
        }

        fun well(x: Int, y: Int, w: Int, h: Int) {
            fill(WELL, x, y, w, h)
            bevel(x, y, w, h, false)
        }

        fun button(x: Int, y: Int, w: Int, h: Int, down: Boolean) {
            if (down) vgrad(x, y, w, h, BTN_DOWN_TOP, BTN_DOWN_BOTTOM) else vgrad(x, y, w, h, BTN_TOP, BTN_BOTTOM)
            bevel(x, y, w, h, !down)
        }

        /** Draws text with the 4x5 glyphs, 5px apart. Returns the width used. */
        fun text(s: String, x: Int, y: Int, c: Color, advance: Int = 5): Int {
            var dx = x
            for (ch in s.uppercase()) {
                DefaultFont.GLYPHS[ch]?.forEachIndexed { row, bits ->
                    bits.forEachIndexed { col, b -> if (b == '#') px(c, dx + col, y + row) }
                }
                dx += advance
            }
            return dx - x - (advance - 4)
        }

        fun centeredText(s: String, x: Int, y: Int, w: Int, c: Color) {
            val tw = s.length * 5 - 1
            text(s, x + (w - tw) / 2, y, c)
        }

        fun poly(c: Color, vararg pts: Int) {
            g.color = c
            val n = pts.size / 2
            g.fillPolygon(IntArray(n) { pts[it * 2] }, IntArray(n) { pts[it * 2 + 1] }, n)
        }

        fun done(): BufferedImage {
            g.dispose()
            return img
        }
    }

    private fun sheets(): Map<String, BufferedImage> = mapOf(
        Sheets.MAIN to main(),
        Sheets.CBUTTONS to cbuttons(),
        Sheets.TITLEBAR to titlebar(),
        Sheets.NUMBERS to numbers(),
        Sheets.PLAYPAUS to playpaus(),
        Sheets.MONOSTEREO to monostereo(),
        Sheets.VOLUME to volume(),
        Sheets.BALANCE to balance(),
        Sheets.POSBAR to posbar(),
        Sheets.SHUFREP to shufrep(),
        Sheets.TEXT to textSheet(),
        Sheets.EQMAIN to eqmain(),
        Sheets.PLEDIT to pledit(),
    )

    private fun main(): BufferedImage = Art(275, 116).apply {
        body(0, 0, 275, 116)
        // title bar area is replaced at runtime by titlebar.bmp
        well(9, 21, 100, 43)                    // clock and analyzer
        fill(GREEN, 73, 29, 2, 2); fill(GREEN, 73, 35, 2, 2)
        well(109, 24, 159, 12)                  // song title
        well(109, 40, 22, 11); text("KBPS", 133, 43, LABEL)
        well(154, 40, 15, 11); text("KHZ", 171, 43, LABEL)
        well(16, 72, 248, 10)                   // seek groove
        fill(Color(0x15171F), 16, 76, 248, 2)
        fill(DARK, 12, 85, 234, 1)
        fill(LIGHT, 12, 108, 234, 1)
        text("WINAMP CLASSIC", 178, 109, Color(0x6A6F8C))
    }.done()

    private fun cbuttons(): BufferedImage {
        val a = Art(136, 36)
        val buttons = listOf(Spr.PREV, Spr.PLAY, Spr.PAUSE, Spr.STOP, Spr.NEXT, Spr.EJECT)
        for ((i, r) in buttons.withIndex()) {
            for (down in listOf(false, true)) {
                val dy = if (down) (if (i == 5) Spr.EJECT_PRESSED_DY else Spr.CBUTTON_PRESSED_DY) else 0
                a.button(r.x, r.y + dy, r.w, r.h, down)
                val cx = r.x + r.w / 2 + if (down) 1 else 0
                val cy = r.y + dy + r.h / 2 + if (down) 1 else 0
                when (i) {
                    0 -> { a.fill(ICON, cx - 5, cy - 4, 2, 8); a.poly(ICON, cx + 4, cy - 4, cx + 4, cy + 4, cx - 3, cy) }
                    1 -> a.poly(ICON, cx - 3, cy - 5, cx - 3, cy + 5, cx + 5, cy)
                    2 -> { a.fill(ICON, cx - 4, cy - 4, 3, 8); a.fill(ICON, cx + 1, cy - 4, 3, 8) }
                    3 -> a.fill(ICON, cx - 4, cy - 4, 8, 8)
                    4 -> { a.poly(ICON, cx - 4, cy - 4, cx - 4, cy + 4, cx + 3, cy); a.fill(ICON, cx + 3, cy - 4, 2, 8) }
                    5 -> { a.poly(ICON, cx - 5, cy + 1, cx + 5, cy + 1, cx, cy - 4); a.fill(ICON, cx - 5, cy + 3, 10, 2) }
                }
            }
        }
        return a.done()
    }

    private fun titlebar(): BufferedImage {
        val a = Art(344, 87)
        for ((i, r) in listOf(Spr.TITLE_ACTIVE, Spr.TITLE_INACTIVE).withIndex()) {
            val top = if (i == 0) Color(0x6A6F8F) else Color(0x4A4D63)
            val bottom = if (i == 0) Color(0x2A2C3A) else Color(0x2F3140)
            a.vgrad(r.x, r.y, r.w, r.h, top, bottom)
            a.bevel(r.x, r.y, r.w, r.h, true)
            var y = r.y + 3
            while (y < r.y + r.h - 3) {
                a.fill(if (i == 0) Color(0x9DA2C2) else Color(0x6A6E88), r.x + 20, y, 230 - 10, 1)
                y += 2
            }
            val label = "WINAMP"
            val tw = label.length * 5
            a.fill(bottom, r.x + (r.w - tw) / 2 - 4, r.y + 2, tw + 8, 10)
            a.text(label, r.x + (r.w - tw) / 2, r.y + 4, if (i == 0) Color(0xE0E4F8) else Color(0x8A8EA8))
        }
        val buttons = listOf(Spr.TB_MENU to 'm', Spr.TB_MINIMIZE to 'n', Spr.TB_CLOSE to 'x', Rect(0, 18, 9, 9) to 's')
        for ((r, kind) in buttons) {
            for (down in listOf(false, true)) {
                val y = r.y + if (down && kind != 's') Spr.TB_PRESSED_DY else 0
                val x = r.x + if (kind == 's' && down) 9 else 0
                a.button(x, y, 9, 9, down)
                when (kind) {
                    'm' -> { a.fill(ICON, x + 2, y + 2, 5, 1); a.fill(ICON, x + 2, y + 4, 5, 1); a.fill(ICON, x + 2, y + 6, 5, 1) }
                    'n' -> a.fill(ICON, x + 2, y + 6, 5, 2)
                    'x' -> for (k in 0 until 5) { a.px(ICON, x + 2 + k, y + 2 + k); a.px(ICON, x + 6 - k, y + 2 + k) }
                    's' -> a.fill(ICON, x + 2, y + 3, 5, 3)
                }
            }
        }
        return a.done()
    }

    private fun numbers(): BufferedImage {
        val a = Art(99, 13)
        for (d in 0..9) {
            val x = d * 9
            val m = SevenSegment.mask('0' + d)
            if (m and SevenSegment.A != 0) a.fill(GREEN, x + 1, 0, 7, 2)
            if (m and SevenSegment.B != 0) a.fill(GREEN, x + 7, 1, 2, 6)
            if (m and SevenSegment.C != 0) a.fill(GREEN, x + 7, 6, 2, 6)
            if (m and SevenSegment.D != 0) a.fill(GREEN, x + 1, 11, 7, 2)
            if (m and SevenSegment.E != 0) a.fill(GREEN, x, 6, 2, 6)
            if (m and SevenSegment.F != 0) a.fill(GREEN, x, 1, 2, 6)
            if (m and SevenSegment.G != 0) a.fill(GREEN, x + 1, 5, 7, 2)
        }
        return a.done()
    }

    private fun playpaus(): BufferedImage {
        val a = Art(42, 9)
        a.poly(GREEN, 1, 0, 1, 8, 7, 4)                                  // play
        a.fill(GREEN, 11, 0, 2, 9); a.fill(GREEN, 15, 0, 2, 9)          // pause
        a.fill(GREEN, 20, 1, 7, 7)                                       // stop
        return a.done()
    }

    private fun monostereo(): BufferedImage {
        val a = Art(56, 24)
        // narrow 4x5 glyphs so STEREO fits the 29px sprite
        for ((r, text) in listOf(Spr.STEREO_ON to "STEREO", Spr.STEREO_OFF to "STEREO", Spr.MONO_ON to "MONO", Spr.MONO_OFF to "MONO")) {
            val on = r == Spr.STEREO_ON || r == Spr.MONO_ON
            a.text(text, r.x, r.y + 3, if (on) GREEN else GREEN_DIM)
        }
        return a.done()
    }

    private fun sliderThumb(a: Art, r: Rect, down: Boolean) {
        a.button(r.x, r.y, r.w, r.h, down)
        for (k in 0 until 3) a.fill(if (down) Color(0x6A6F8C) else Color(0xC0C4DA), r.x + 4 + k * 3, r.y + 2, 1, r.h - 4)
    }

    private fun volume(): BufferedImage {
        val a = Art(68, 433)
        for (n in 0 until Spr.SLIDER_STATES) {
            val y = n * Spr.SLIDER_STATE_DY
            a.well(0, y, 68, 13)
            val fillW = Math.round(n / 27f * 64f)
            for (x in 0 until fillW) {
                val t = x / 63f
                val c = if (t < 0.6f) mix(Color(0x10C810), AMBER, t / 0.6f) else mix(AMBER, RED, (t - 0.6f) / 0.4f)
                a.fill(c, 2 + x, y + 2, 1, 9)
            }
        }
        sliderThumb(a, Spr.SLIDER_THUMB, false)
        sliderThumb(a, Spr.SLIDER_THUMB_PRESSED, true)
        return a.done()
    }

    private fun balance(): BufferedImage {
        val a = Art(47, 433)
        for (n in 0 until Spr.SLIDER_STATES) {
            val y = n * Spr.SLIDER_STATE_DY
            a.well(Spr.BALANCE_BG_X, y, 38, 13)
            val half = Math.round(n / 27f * 17f)
            val cx = Spr.BALANCE_BG_X + 19
            for (d in 0 until half) {
                val c = mix(Color(0x10C810), RED, d / 17f)
                a.fill(c, cx + d, y + 2, 1, 9)
                a.fill(c, cx - 1 - d, y + 2, 1, 9)
            }
        }
        sliderThumb(a, Spr.SLIDER_THUMB, false)
        sliderThumb(a, Spr.SLIDER_THUMB_PRESSED, true)
        return a.done()
    }

    private fun posbar(): BufferedImage {
        val a = Art(307, 10)
        sliderThumb(a, Spr.POS_THUMB, false)
        sliderThumb(a, Spr.POS_THUMB_PRESSED, true)
        return a.done()
    }

    private fun toggleButton(a: Art, r: Rect, label: String, x: Int, y: Int) {
        for (state in 0..3) {
            val down = state == 1 || state == 3
            val selected = state >= 2
            val bx = x
            val by = y + state * r.h
            a.button(bx, by, r.w, r.h, down)
            a.centeredText(label, bx, by + (r.h - 5) / 2, r.w, if (selected) GREEN else LABEL)
            if (selected) a.fill(GREEN, bx + 3, by + r.h - 3, r.w - 6, 1)
        }
    }

    private fun shufrep(): BufferedImage {
        val a = Art(92, 85)
        toggleButton(a, Spr.REPEAT, "REP", Spr.REPEAT.x, 0)
        toggleButton(a, Spr.SHUFFLE, "SHUFFLE", Spr.SHUFFLE.x, 0)
        // EQ and PL: normal and selected stacked 12px apart, pressed variants 46px to the right
        for ((r, label) in listOf(Spr.EQ_BTN to "EQ", Spr.PL_BTN to "PL")) {
            for (selected in listOf(false, true)) for (down in listOf(false, true)) {
                val x = r.x + if (down) Spr.TOGGLE_PRESSED_DX else 0
                val y = r.y + if (selected) Spr.TOGGLE_SELECTED_DY else 0
                a.button(x, y, r.w, r.h, down)
                a.centeredText(label, x, y + 3, r.w, if (selected) GREEN else LABEL)
            }
        }
        return a.done()
    }

    private fun textSheet(): BufferedImage {
        val a = Art(155, 18)
        val rows = listOf(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@",
            "0123456789.:()-'!_+\\/[]^&%,=$#",
            "ÅÖÄ?* ",
        )
        for ((row, chars) in rows.withIndex()) {
            for ((col, ch) in chars.withIndex()) {
                val glyph = DefaultFont.GLYPHS[ch] ?: continue
                glyph.forEachIndexed { gy, bits ->
                    bits.forEachIndexed { gx, b -> if (b == '#') a.px(GREEN, col * 5 + gx, row * 6 + gy) }
                }
            }
        }
        return a.done()
    }

    private fun eqmain(): BufferedImage {
        val a = Art(275, 315)
        // window background
        a.body(0, 0, 275, 116)
        a.well(86, 17, 113, 19)
        a.fill(GREEN_DIM, 87, 26, 111, 1)
        a.text("+12DB", 44, 40, LABEL); a.text("0DB", 44, 68, LABEL); a.text("-12DB", 44, 96, LABEL)
        a.text("PREAMP", 10, 104, LABEL)
        val names = listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K")
        for ((i, n) in names.withIndex()) a.text(n, Spr.EQ_BAND_X0 + i * Spr.EQ_BAND_STEP + 7 - (n.length * 5 - 1) / 2, 104, LABEL)
        a.fill(DARK, 8, 14, 259, 1); a.fill(LIGHT, 8, 15, 259, 1)
        // title bars
        for ((r, active) in listOf(Spr.EQ_TITLE_ACTIVE to true, Spr.EQ_TITLE_INACTIVE to false)) {
            a.vgrad(r.x, r.y, r.w, r.h, if (active) Color(0x6A6F8F) else Color(0x4A4D63), if (active) Color(0x2A2C3A) else Color(0x2F3140))
            a.bevel(r.x, r.y, r.w, r.h, true)
            var y = r.y + 3
            while (y < r.y + r.h - 3) { a.fill(if (active) Color(0x9DA2C2) else Color(0x6A6E88), r.x + 20, y, 235, 1); y += 2 }
            val label = "WINAMP EQUALIZER"
            val tw = label.length * 5
            a.fill(if (active) Color(0x2A2C3A) else Color(0x2F3140), r.x + (r.w - tw) / 2 - 4, r.y + 2, tw + 8, 10)
            a.text(label, r.x + (r.w - tw) / 2, r.y + 4, if (active) Color(0xE0E4F8) else Color(0x8A8EA8))
        }
        // slider backgrounds: 28 states, 14 per row; state 0 is the bottom (-12 dB), state 27 the top (+12 dB)
        for (i in 0 until 28) {
            val x = Spr.EQ_SLIDER_BG_X + (i % 14) * Spr.EQ_SLIDER_COL_STEP
            val y = Spr.EQ_SLIDER_BG_Y + (i / 14) * Spr.EQ_SLIDER_ROW_STEP
            a.fill(WELL, x + 5, y, 4, Spr.EQ_SLIDER_H)
            a.bevel(x + 5, y, 4, Spr.EQ_SLIDER_H, false)
            val thumbCenter = y + 5 + Math.round((1f - i / 27f) * 52f)
            val mid = y + Spr.EQ_SLIDER_H / 2
            val (from, to) = if (thumbCenter < mid) thumbCenter to mid else mid to thumbCenter
            for (row in from..to) a.fill(if (thumbCenter < mid) mix(GREEN, AMBER, (mid - row) / 31f) else mix(GREEN, RED, (row - mid) / 31f), x + 6, row, 2, 1)
            a.fill(LABEL, x + 3, mid, 8, 1)
        }
        sliderThumbEq(a, Spr.EQ_THUMB, false)
        sliderThumbEq(a, Spr.EQ_THUMB_PRESSED, true)
        // ON / AUTO: normal, selected, pressed, selected+pressed
        for ((r, label) in listOf(Spr.EQ_ON to "ON", Spr.EQ_AUTO to "AUTO")) {
            for (state in 0..3) {
                val selected = state == 1 || state == 3
                val down = state >= 2
                val x = r.x + (if (selected) Spr.EQ_BTN_SELECTED_DX else 0) + (if (down) Spr.EQ_BTN_PRESSED_DX else 0)
                a.button(x, r.y, r.w, r.h, down)
                a.centeredText(label, x, r.y + 3, r.w, if (selected) GREEN else LABEL)
            }
        }
        for (down in listOf(false, true)) {
            val y = Spr.EQ_PRESETS.y + if (down) Spr.EQ_PRESETS_PRESSED_DY else 0
            a.button(Spr.EQ_PRESETS.x, y, Spr.EQ_PRESETS.w, Spr.EQ_PRESETS.h, down)
            a.centeredText("PRESETS", Spr.EQ_PRESETS.x, y + 3, Spr.EQ_PRESETS.w, LABEL)
        }
        // graph line colours (top = +12 dB) and the flat-preamp reference line
        for (row in 0 until 19) a.px(if (row < 9) mix(AMBER, GREEN, row / 9f) else mix(GREEN, RED, (row - 9) / 9f), Spr.EQ_GRAPH_COLORS.x, Spr.EQ_GRAPH_COLORS.y + row)
        a.fill(Color(0x3C6A3C), Spr.EQ_PREAMP_LINE.x, Spr.EQ_PREAMP_LINE.y, Spr.EQ_PREAMP_LINE.w, 1)
        return a.done()
    }

    private fun sliderThumbEq(a: Art, r: Rect, down: Boolean) {
        a.button(r.x, r.y, r.w, r.h, down)
        a.fill(if (down) Color(0x6A6F8C) else Color(0xC0C4DA), r.x + 3, r.y + r.h / 2, r.w - 6, 1)
    }

    private fun pledit(): BufferedImage {
        val a = Art(280, 186)
        fun titleBarPiece(r: Rect, active: Boolean) {
            a.vgrad(r.x, r.y, r.w, r.h, if (active) Color(0x6A6F8F) else Color(0x4A4D63), if (active) Color(0x2A2C3A) else Color(0x2F3140))
            a.fill(if (active) LIGHT else Color(0x6A6E88), r.x, r.y, r.w, 1)
            var y = r.y + 4
            while (y < r.y + 11) { a.fill(if (active) Color(0x9DA2C2) else Color(0x6A6E88), r.x, y, r.w, 1); y += 2 }
        }
        for (active in listOf(true, false)) {
            val tl = if (active) Spr.PL_TL_ACTIVE else Spr.PL_TL_INACTIVE
            val top = if (active) Spr.PL_TOP_ACTIVE else Spr.PL_TOP_INACTIVE
            val tr = if (active) Spr.PL_TR_ACTIVE else Spr.PL_TR_INACTIVE
            val title = if (active) Spr.PL_TITLE_ACTIVE else Spr.PL_TITLE_INACTIVE
            for (r in listOf(tl, top, tr, title)) titleBarPiece(r, active)
            a.fill(if (active) LIGHT else Color(0x6A6E88), tl.x, tl.y, 1, tl.h)
            a.fill(if (active) LIGHT else Color(0x6A6E88), tr.x + tr.w - 1, tr.y, 1, tr.h)
            val label = "WINAMP PLAYLIST"
            val tw = label.length * 5
            a.fill(if (active) Color(0x2A2C3A) else Color(0x2F3140), title.x + (title.w - tw) / 2 - 4, title.y + 3, tw + 8, 9)
            a.text(label, title.x + (title.w - tw) / 2, title.y + 5, if (active) Color(0xE0E4F8) else Color(0x8A8EA8))
        }
        // side tiles repeat vertically, so they are flat with edge lines to avoid visible seams
        val side = mix(BODY_TOP, BODY_BOTTOM, 0.5f)
        val l = Spr.PL_LEFT
        a.fill(side, l.x, l.y, l.w, l.h); a.fill(LIGHT, l.x, l.y, 1, l.h); a.fill(DARK, l.x + l.w - 1, l.y, 1, l.h)
        val r = Spr.PL_RIGHT
        a.fill(side, r.x, r.y, r.w, r.h); a.fill(DARK, r.x, r.y, 1, r.h); a.fill(LIGHT, r.x + r.w - 1, r.y, 1, r.h)
        a.vgrad(Spr.PL_BOTTOM.x, Spr.PL_BOTTOM.y, Spr.PL_BOTTOM.w, Spr.PL_BOTTOM.h, BODY_TOP, BODY_BOTTOM)
        a.fill(DARK, Spr.PL_BOTTOM.x, Spr.PL_BOTTOM.y + Spr.PL_BOTTOM.h - 1, Spr.PL_BOTTOM.w, 1)
        // bottom left: ADD REM SEL MISC buttons
        val bl = Spr.PL_BL
        a.body(bl.x, bl.y, bl.w, bl.h)
        for ((i, label) in listOf("ADD", "REM", "SEL", "MISC").withIndex()) {
            a.button(bl.x + 11 + i * 29, bl.y + 10, 25, 18, false)
            a.centeredText(label, bl.x + 11 + i * 29, bl.y + 17, 25, LABEL)
        }
        // bottom right: running time and LIST
        val br = Spr.PL_BR
        a.body(br.x, br.y, br.w, br.h)
        a.well(br.x + 8, br.y + 8, 96, 14)
        a.button(br.x + 110, br.y + 10, 34, 18, false)
        a.centeredText("LIST", br.x + 110, br.y + 17, 34, LABEL)
        // scroll handle
        a.button(Spr.PL_SCROLL.x, Spr.PL_SCROLL.y, Spr.PL_SCROLL.w, Spr.PL_SCROLL.h, false)
        a.button(Spr.PL_SCROLL_PRESSED.x, Spr.PL_SCROLL_PRESSED.y, Spr.PL_SCROLL_PRESSED.w, Spr.PL_SCROLL_PRESSED.h, true)
        return a.done()
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Writes the built-in skin as a .wsz so it can be edited as a starting point for a new skin. */
    fun exportWsz(file: File) {
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, img) in sheets()) {
                val rgb = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB)
                val g = rgb.createGraphics(); g.drawImage(img, 0, 0, null); g.dispose()
                zip.putNextEntry(ZipEntry("$name.bmp"))
                ImageIO.write(rgb, "bmp", zip)
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("viscolor.txt"))
            zip.write(visColors.joinToString("\n") { "${it.red},${it.green},${it.blue}" }.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("pledit.txt"))
            val p = playlistColors
            fun hex(c: Color) = "#%02X%02X%02X".format(c.red, c.green, c.blue)
            zip.write("[Text]\nNormal=${hex(p.normal)}\nCurrent=${hex(p.current)}\nNormalBG=${hex(p.normalBg)}\nSelectedBG=${hex(p.selectedBg)}\nFont=${p.fontName}\n".toByteArray())
            zip.closeEntry()
        }
    }
}
