package com.illyaua.winamp.desktop.ui

import com.illyaua.winamp.core.TimeFormat
import com.illyaua.winamp.core.TrackKind
import com.illyaua.winamp.desktop.audio.Player
import com.illyaua.winamp.desktop.audio.Status
import com.illyaua.winamp.desktop.skin.BitmapFont
import com.illyaua.winamp.desktop.skin.Rect
import com.illyaua.winamp.desktop.skin.Sheets
import com.illyaua.winamp.desktop.skin.Spr
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/** The main player window, 275x116 in skin pixels. */
class MainPanel(app: App) : SkinnedPanel(app, Spr.WIN_W, Spr.MAIN_H) {
    private val player: Player get() = app.player

    private class Button(val at: Rect, val action: () -> Unit, val draw: (Graphics2D, Boolean) -> Unit)

    private val buttons: List<Button>
    private var pressed: Button? = null
    private var pressedInside = false
    private var dragging: Drag? = null
    private var seekPreview = -1f
    private var showRemaining = false
    private var marqueeOffset = 0
    private var marqueeTick = 0
    private val windowDrag = WindowDrag(app) { javax.swing.SwingUtilities.getWindowAncestor(this) }

    private enum class Drag { VOLUME, BALANCE, SEEK, TITLE }

    init {
        fun cbutton(at: Rect, src: Rect, pressedDy: Int, action: () -> Unit) =
            Button(at, action) { g, down ->
                app.skin.blit(g, Sheets.CBUTTONS, src.x, src.y + if (down) pressedDy else 0, src.w, src.h, at.x, at.y)
            }

        fun srToggle(at: Rect, src: Rect, selected: () -> Boolean, action: () -> Unit) =
            Button(at, action) { g, down ->
                val dy = (if (selected()) Spr.SR_SELECTED_DY else 0) + if (down) Spr.SR_PRESSED_DY else 0
                app.skin.blit(g, Sheets.SHUFREP, src.x, src.y + dy, src.w, src.h, at.x, at.y)
            }

        fun smallToggle(at: Rect, src: Rect, selected: () -> Boolean, action: () -> Unit) =
            Button(at, action) { g, down ->
                val sx = src.x + if (down) Spr.TOGGLE_PRESSED_DX else 0
                val sy = src.y + if (selected()) Spr.TOGGLE_SELECTED_DY else 0
                app.skin.blit(g, Sheets.SHUFREP, sx, sy, src.w, src.h, at.x, at.y)
            }

        fun titleButton(at: Rect, src: Rect, action: () -> Unit) =
            Button(at, action) { g, down ->
                app.skin.blit(g, Sheets.TITLEBAR, src.x, src.y + if (down) Spr.TB_PRESSED_DY else 0, src.w, src.h, at.x, at.y)
            }

        buttons = listOf(
            cbutton(Spr.PREV_AT, Spr.PREV, Spr.CBUTTON_PRESSED_DY) { player.previous() },
            cbutton(Spr.PLAY_AT, Spr.PLAY, Spr.CBUTTON_PRESSED_DY) { player.play() },
            cbutton(Spr.PAUSE_AT, Spr.PAUSE, Spr.CBUTTON_PRESSED_DY) { player.togglePause() },
            cbutton(Spr.STOP_AT, Spr.STOP, Spr.CBUTTON_PRESSED_DY) { player.stop() },
            cbutton(Spr.NEXT_AT, Spr.NEXT, Spr.CBUTTON_PRESSED_DY) { player.next() },
            cbutton(Spr.EJECT_AT, Spr.EJECT, Spr.EJECT_PRESSED_DY) { app.openFiles(play = true) },
            srToggle(Spr.SHUFFLE_AT, Spr.SHUFFLE, { player.shuffle }) { player.setShuffle(!player.shuffle) },
            srToggle(Spr.REPEAT_AT, Spr.REPEAT, { player.repeat }) { player.setRepeat(!player.repeat) },
            smallToggle(Spr.EQ_BTN_AT, Spr.EQ_BTN, { app.eqVisible }) { app.setEqVisible(!app.eqVisible) },
            smallToggle(Spr.PL_BTN_AT, Spr.PL_BTN, { app.playlistVisible }) { app.setPlaylistVisible(!app.playlistVisible) },
            titleButton(Spr.MENU_BTN_AT, Spr.TB_MENU) { app.showMainMenu(this, Spr.MENU_BTN_AT.x * app.scale, (Spr.MENU_BTN_AT.y + 9) * app.scale) },
            titleButton(Spr.MINIMIZE_AT, Spr.TB_MINIMIZE) { app.minimize() },
            titleButton(Spr.CLOSE_AT, Spr.TB_CLOSE) { app.quit() },
        )

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    app.showMainMenu(this@MainPanel, e.x, e.y)
                    return
                }
                val x = bx(e)
                val y = by(e)
                buttons.firstOrNull { it.at.contains(x, y) }?.let {
                    pressed = it
                    pressedInside = true
                    repaint()
                    return
                }
                when {
                    Spr.VOLUME_AT.contains(x, y) -> { dragging = Drag.VOLUME; setVolumeFrom(x) }
                    Spr.BALANCE_AT.contains(x, y) -> { dragging = Drag.BALANCE; setBalanceFrom(x) }
                    Spr.SEEK_AT.contains(x, y) && player.status != Status.STOPPED -> { dragging = Drag.SEEK; seekPreview = seekFraction(x) }
                    Spr.CLOCK_AT.contains(x, y) -> showRemaining = !showRemaining
                    Spr.TITLE_DRAG.contains(x, y) -> { dragging = Drag.TITLE; windowDrag.press(e) }
                }
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                val x = bx(e)
                val y = by(e)
                pressed?.let { pressedInside = it.at.contains(x, y); repaint(); return }
                when (dragging) {
                    Drag.VOLUME -> setVolumeFrom(x)
                    Drag.BALANCE -> setBalanceFrom(x)
                    Drag.SEEK -> seekPreview = seekFraction(x)
                    Drag.TITLE -> windowDrag.drag(e)
                    null -> {}
                }
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                pressed?.let { if (pressedInside) it.action() }
                pressed = null
                when (dragging) {
                    Drag.SEEK -> {
                        val duration = player.currentTrack?.durationMs ?: 0
                        if (duration > 0 && seekPreview >= 0) player.seek((seekPreview * duration).toLong())
                        seekPreview = -1f
                    }
                    Drag.TITLE -> windowDrag.release()
                    else -> {}
                }
                dragging = null
                repaint()
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                player.setVolume(player.volume - e.wheelRotation * 0.05f)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)
    }

    private fun setVolumeFrom(x: Int) {
        val f = (x - Spr.VOLUME_AT.x - Spr.SLIDER_THUMB_W / 2f) / (Spr.VOLUME_AT.w - Spr.SLIDER_THUMB_W)
        player.setVolume(f.coerceIn(0f, 1f))
    }

    private fun setBalanceFrom(x: Int) {
        val f = (x - Spr.BALANCE_AT.x - Spr.SLIDER_THUMB_W / 2f) / (Spr.BALANCE_AT.w - Spr.SLIDER_THUMB_W)
        player.setBalance(f.coerceIn(0f, 1f) * 2f - 1f)
    }

    private fun seekFraction(x: Int): Float {
        val thumb = Spr.POS_THUMB.w
        return ((x - Spr.SEEK_AT.x - thumb / 2f) / (Spr.SEEK_AT.w - thumb)).coerceIn(0f, 1f)
    }

    /** Called from the UI timer. */
    fun tick() {
        marqueeTick++
        if (marqueeTick % 3 == 0) marqueeOffset++
        repaint()
    }

    override fun paintBase(g: Graphics2D) {
        val skin = app.skin
        skin.blit(g, Sheets.MAIN, 0, 0, Spr.WIN_W, Spr.MAIN_H, 0, 0)
        val t = if (app.mainActive) Spr.TITLE_ACTIVE else Spr.TITLE_INACTIVE
        skin.blit(g, Sheets.TITLEBAR, t, 0, 0)

        val track = player.currentTrack
        val status = player.status
        val duration = track?.durationMs ?: 0
        val position = player.positionMs()

        // play state
        val ps = when (status) {
            Status.PLAYING -> Spr.PS_PLAY
            Status.PAUSED -> Spr.PS_PAUSE
            Status.STOPPED -> Spr.PS_STOP
        }
        skin.blit(g, Sheets.PLAYPAUS, ps, Spr.PLAYSTATE_AT.x, Spr.PLAYSTATE_AT.y)

        // clock
        val shown = when {
            status == Status.STOPPED -> 0L
            seekPreview >= 0 -> (seekPreview * duration).toLong()
            showRemaining && duration > 0 -> (duration - position).coerceAtLeast(0)
            else -> position
        }
        val digits = TimeFormat.lcd(shown)
        for (i in 0 until 4) {
            skin.blit(g, Sheets.NUMBERS, (digits[i] - '0') * Spr.DIGIT_W, 0, Spr.DIGIT_W, Spr.DIGIT_H, Spr.CLOCK_DIGIT_X[i], Spr.CLOCK_DIGIT_Y)
        }
        if (showRemaining && status != Status.STOPPED) skin.blit(g, Sheets.NUMBERS, Spr.MINUS, Spr.CLOCK_MINUS_AT.x, Spr.CLOCK_MINUS_AT.y)

        paintSpectrum(g)

        // song title
        val text = titleText()
        val area = Spr.TITLE_TEXT_AT
        val width = BitmapFont.width(text)
        if (width <= area.w) {
            BitmapFont.drawClipped(g, skin, text, area, 0)
        } else {
            val span = width
            val off = marqueeOffset % span
            val old = g.clip
            g.clipRect(area.x, area.y, area.w, area.h)
            BitmapFont.draw(g, skin, text, area.x - off, area.y)
            BitmapFont.draw(g, skin, text, area.x - off + span, area.y)
            g.clip = old
        }

        // bitrate, sample rate, channels
        if (track != null && status != Status.STOPPED) {
            if (track.kind != TrackKind.MIDI && track.bitrateKbps > 0) {
                val k = track.bitrateKbps
                // three characters wide, so lossless rates like 1411 become "1k4"
                val shown = if (k >= 1000) "${k / 1000}k${(k % 1000) / 100}" else k.toString().padStart(3)
                BitmapFont.draw(g, skin, shown, Spr.BITRATE_AT.x, Spr.BITRATE_AT.y)
            }
            if (track.sampleRateHz > 0) {
                BitmapFont.draw(g, skin, (track.sampleRateHz / 1000).toString().padStart(2).takeLast(2), Spr.KHZ_AT.x, Spr.KHZ_AT.y)
            }
        }
        val stereo = track != null && status != Status.STOPPED && track.channels >= 2
        val mono = track != null && status != Status.STOPPED && track.channels == 1
        skin.blit(g, Sheets.MONOSTEREO, if (stereo) Spr.STEREO_ON else Spr.STEREO_OFF, Spr.STEREO_AT.x, Spr.STEREO_AT.y)
        skin.blit(g, Sheets.MONOSTEREO, if (mono) Spr.MONO_ON else Spr.MONO_OFF, Spr.MONO_AT.x, Spr.MONO_AT.y)

        // volume and balance
        val vol = player.volume
        val volState = (vol * (Spr.SLIDER_STATES - 1)).roundToInt()
        skin.blit(g, Sheets.VOLUME, Spr.VOLUME_BG_X, volState * Spr.SLIDER_STATE_DY, Spr.VOLUME_AT.w, Spr.SLIDER_BG_H, Spr.VOLUME_AT.x, Spr.VOLUME_AT.y)
        val volThumbX = Spr.VOLUME_AT.x + (vol * (Spr.VOLUME_AT.w - Spr.SLIDER_THUMB_W)).roundToInt()
        val thumbSrc = if (dragging == Drag.VOLUME) Spr.SLIDER_THUMB_PRESSED else Spr.SLIDER_THUMB
        skin.blit(g, Sheets.VOLUME, thumbSrc, volThumbX, Spr.VOLUME_AT.y + 1)

        val bal = player.balance
        val balState = (abs(bal) * (Spr.SLIDER_STATES - 1)).roundToInt()
        val balBgX = if ((skin.sheet(Sheets.BALANCE)?.width ?: 0) >= Spr.BALANCE_BG_X + Spr.BALANCE_AT.w) Spr.BALANCE_BG_X else 0
        skin.blit(g, Sheets.BALANCE, balBgX, balState * Spr.SLIDER_STATE_DY, Spr.BALANCE_AT.w, Spr.SLIDER_BG_H, Spr.BALANCE_AT.x, Spr.BALANCE_AT.y)
        val balThumbX = Spr.BALANCE_AT.x + ((bal + 1f) / 2f * (Spr.BALANCE_AT.w - Spr.SLIDER_THUMB_W)).roundToInt()
        val balThumb = if (dragging == Drag.BALANCE) Spr.SLIDER_THUMB_PRESSED else Spr.SLIDER_THUMB
        skin.blit(g, Sheets.BALANCE, balThumb, balThumbX, Spr.BALANCE_AT.y + 1)

        // seek bar thumb (hidden while stopped)
        if (status != Status.STOPPED && duration > 0) {
            val f = if (seekPreview >= 0) seekPreview else (position.toFloat() / duration).coerceIn(0f, 1f)
            val thumbX = Spr.SEEK_AT.x + (f * (Spr.SEEK_AT.w - Spr.POS_THUMB.w)).roundToInt()
            skin.blit(g, Sheets.POSBAR, if (dragging == Drag.SEEK) Spr.POS_THUMB_PRESSED else Spr.POS_THUMB, thumbX, Spr.SEEK_AT.y)
        }

        for (b in buttons) b.draw(g, b === pressed && pressedInside)
    }

    private fun titleText(): String {
        player.lastError?.let { return "$it   ***   " }
        val track = player.currentTrack ?: return "WINAMP CLASSIC   ***   "
        val length = if (track.durationMs > 0) " (${TimeFormat.mmss(track.durationMs)})" else ""
        return "${player.currentIndex + 1}. ${track.displayName}$length   ***   "
    }

    private fun paintSpectrum(g: Graphics2D) {
        val frame = app.spectrum
        val area = Spr.SPECTRUM_AT
        val colors = app.skin.visColors
        val rows = area.h
        for (i in frame.bars.indices) {
            val x = area.x + i * 4
            if (x + 3 > area.x + area.w + 1) break
            val h = (frame.bars[i] * rows).roundToInt().coerceIn(0, rows)
            for (r in 0 until h) {
                g.color = colors[2 + (rows - 1 - r).coerceIn(0, 15)]
                g.fillRect(x, area.y + rows - 1 - r, 3, 1)
            }
            val ph = (frame.peaks[i] * rows).roundToInt().coerceIn(0, rows)
            if (ph > 0) {
                g.color = colors[23]
                g.fillRect(x, area.y + rows - ph, 3, 1)
            }
        }
    }
}
