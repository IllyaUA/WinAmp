package com.illyaua.winamp.desktop.ui

import com.illyaua.winamp.core.Equalizer10
import com.illyaua.winamp.desktop.skin.Rect
import com.illyaua.winamp.desktop.skin.Sheets
import com.illyaua.winamp.desktop.skin.Spr
import java.awt.Color
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.roundToInt

/** The equalizer window: preamp plus ten bands, an on switch, presets and a response curve. */
class EqPanel(app: App) : SkinnedPanel(app, Spr.WIN_W, Spr.EQ_H) {
    private val windowDrag = WindowDrag(app) { SwingUtilities.getWindowAncestor(this) }
    private var dragSlider = -2          // -1 = preamp, 0..9 = band, -2 = none
    private var dragTitle = false
    private var pressedButton = 0        // 1 = on, 2 = auto, 3 = presets
    private var pressedInside = false

    private fun sliderX(index: Int) = if (index < 0) Spr.EQ_PREAMP_X else Spr.EQ_BAND_X0 + index * Spr.EQ_BAND_STEP

    private fun sliderAt(x: Int, y: Int): Int? {
        if (y < Spr.EQ_SLIDER_Y || y >= Spr.EQ_SLIDER_Y + Spr.EQ_SLIDER_H) return null
        if (x in Spr.EQ_PREAMP_X until Spr.EQ_PREAMP_X + Spr.EQ_SLIDER_W) return -1
        for (i in 0 until Equalizer10.BANDS) {
            val sx = sliderX(i)
            if (x in sx until sx + Spr.EQ_SLIDER_W) return i
        }
        return null
    }

    private fun db(index: Int) = if (index < 0) app.player.eqPreampDb else app.player.eqBand(index)

    private fun setFromY(index: Int, y: Int) {
        val travel = Spr.EQ_SLIDER_H - Spr.EQ_THUMB_H
        val f = 1f - ((y - Spr.EQ_SLIDER_Y - Spr.EQ_THUMB_H / 2f) / travel).coerceIn(0f, 1f)
        var value = f * 24f - 12f
        if (abs(value) < 0.6f) value = 0f
        if (index < 0) app.player.setEqPreamp(value) else app.player.setEqBand(index, value)
    }

    init {
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                val x = bx(e)
                val y = by(e)
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    app.showMainMenu(this@EqPanel, e.x, e.y)
                    return
                }
                when {
                    Spr.EQ_ON_AT.contains(x, y) -> { pressedButton = 1; pressedInside = true }
                    Spr.EQ_AUTO_AT.contains(x, y) -> { pressedButton = 2; pressedInside = true }
                    Spr.EQ_PRESETS_AT.contains(x, y) -> { pressedButton = 3; pressedInside = true }
                    sliderAt(x, y) != null -> {
                        dragSlider = sliderAt(x, y)!!
                        setFromY(dragSlider, y)
                    }
                    y < Spr.TITLE_H -> {
                        if (x in 264..272 && y in 3..11) app.setEqVisible(false)
                        else { dragTitle = true; windowDrag.press(e) }
                    }
                }
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                val x = bx(e)
                val y = by(e)
                when {
                    pressedButton != 0 -> pressedInside = buttonRect(pressedButton).contains(x, y)
                    dragSlider != -2 -> setFromY(dragSlider, y)
                    dragTitle -> windowDrag.drag(e)
                }
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (pressedButton != 0 && pressedInside) {
                    when (pressedButton) {
                        1 -> app.player.setEqEnabled(!app.player.eqEnabled)
                        3 -> showPresets(e)
                    }
                }
                pressedButton = 0
                if (dragTitle) windowDrag.release()
                if (dragSlider != -2) app.saveConfig()
                dragSlider = -2
                dragTitle = false
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    private fun buttonRect(id: Int): Rect = when (id) {
        1 -> Spr.EQ_ON_AT
        2 -> Spr.EQ_AUTO_AT
        else -> Spr.EQ_PRESETS_AT
    }

    private fun showPresets(e: MouseEvent) {
        val menu = JPopupMenu()
        for (preset in EqPresets.ALL) {
            menu.add(JMenuItem(preset.name).apply {
                addActionListener {
                    app.player.loadEqPreset(preset.bands, preset.preamp)
                    app.player.setEqEnabled(true)
                    app.saveConfig()
                }
            })
        }
        menu.show(this, Spr.EQ_PRESETS_AT.x * app.scale, (Spr.EQ_PRESETS_AT.y + Spr.EQ_PRESETS_AT.h) * app.scale)
    }

    override fun paintBase(g: Graphics2D) {
        val skin = app.skin
        skin.blit(g, Sheets.EQMAIN, Spr.EQ_BG, 0, 0)
        skin.blit(g, Sheets.EQMAIN, if (app.eqActive) Spr.EQ_TITLE_ACTIVE else Spr.EQ_TITLE_INACTIVE, 0, 0)

        // buttons: normal, selected (+59), pressed (+118)
        fun stateButton(src: Rect, at: Rect, selected: Boolean, down: Boolean) {
            val dx = (if (selected) Spr.EQ_BTN_SELECTED_DX else 0) + if (down) Spr.EQ_BTN_PRESSED_DX else 0
            skin.blit(g, Sheets.EQMAIN, src.x + dx, src.y, src.w, src.h, at.x, at.y)
        }
        stateButton(Spr.EQ_ON, Spr.EQ_ON_AT, app.player.eqEnabled, pressedButton == 1 && pressedInside)
        stateButton(Spr.EQ_AUTO, Spr.EQ_AUTO_AT, false, pressedButton == 2 && pressedInside)
        val pdy = if (pressedButton == 3 && pressedInside) Spr.EQ_PRESETS_PRESSED_DY else 0
        skin.blit(g, Sheets.EQMAIN, Spr.EQ_PRESETS.x, Spr.EQ_PRESETS.y + pdy, Spr.EQ_PRESETS.w, Spr.EQ_PRESETS.h, Spr.EQ_PRESETS_AT.x, Spr.EQ_PRESETS_AT.y)

        paintGraph(g)

        // sliders
        for (index in -1 until Equalizer10.BANDS) {
            val x = sliderX(index)
            val f = ((db(index) + 12f) / 24f).coerceIn(0f, 1f)
            val state = (f * 27f).roundToInt()
            val bgX = Spr.EQ_SLIDER_BG_X + (state % 14) * Spr.EQ_SLIDER_COL_STEP
            val bgY = Spr.EQ_SLIDER_BG_Y + (state / 14) * Spr.EQ_SLIDER_ROW_STEP
            skin.blit(g, Sheets.EQMAIN, bgX, bgY, Spr.EQ_SLIDER_W, Spr.EQ_SLIDER_H, x, Spr.EQ_SLIDER_Y)
            val thumbY = Spr.EQ_SLIDER_Y + ((1f - f) * (Spr.EQ_SLIDER_H - Spr.EQ_THUMB_H)).roundToInt()
            val thumb = if (dragSlider == index) Spr.EQ_THUMB_PRESSED else Spr.EQ_THUMB
            skin.blit(g, Sheets.EQMAIN, thumb, x + 1, thumbY)
        }
    }

    /** The curve through the ten band gains, coloured from the skin's 19-pixel gradient strip. */
    private fun paintGraph(g: Graphics2D) {
        val skin = app.skin
        val area = Spr.EQ_GRAPH_AT
        val sheet = skin.sheet(Sheets.EQMAIN)
        fun rowColor(row: Int): Color {
            val src = Spr.EQ_GRAPH_COLORS
            if (sheet == null || src.y + row >= sheet.height || src.x >= sheet.width) return Color.GREEN
            return Color(sheet.getRGB(src.x, src.y + row), true)
        }
        val preampRow = ((1f - (app.player.eqPreampDb + 12f) / 24f) * (area.h - 1)).roundToInt().coerceIn(0, area.h - 1)
        skin.blit(g, Sheets.EQMAIN, Spr.EQ_PREAMP_LINE.x, Spr.EQ_PREAMP_LINE.y, area.w, 1, area.x, area.y + preampRow)

        var prevY = -1
        for (px in 0 until area.w) {
            val pos = px * (Equalizer10.BANDS - 1f) / (area.w - 1)
            val i = pos.toInt().coerceAtMost(Equalizer10.BANDS - 2)
            val t = pos - i
            val value = app.player.eqBand(i) * (1 - t) + app.player.eqBand(i + 1) * t
            val row = ((1f - (value + 12f) / 24f) * (area.h - 1)).roundToInt().coerceIn(0, area.h - 1)
            val lo = if (prevY < 0) row else minOf(prevY, row)
            val hi = if (prevY < 0) row else maxOf(prevY, row)
            for (r in lo..hi) {
                g.color = rowColor(r)
                g.fillRect(area.x + px, area.y + r, 1, 1)
            }
            prevY = row
        }
    }
}
