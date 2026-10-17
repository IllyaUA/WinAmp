package com.illyaua.winamp.desktop.ui

import com.illyaua.winamp.core.NaturalOrder
import com.illyaua.winamp.core.TimeFormat
import com.illyaua.winamp.desktop.audio.file
import com.illyaua.winamp.desktop.skin.BitmapFont
import com.illyaua.winamp.desktop.skin.Rect
import com.illyaua.winamp.desktop.skin.Sheets
import com.illyaua.winamp.desktop.skin.Spr
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.random.Random

/** The playlist window. Width is fixed at 275 skin pixels; height can be dragged from the bottom-right corner. */
class PlaylistPanel(app: App, height: Int) : SkinnedPanel(app, Spr.WIN_W, height) {
    private val windowDrag = WindowDrag(app) { SwingUtilities.getWindowAncestor(this) }
    private val selected = LinkedHashSet<Int>()
    private var anchor = -1
    private var scroll = 0
    private var dragMode = Mode.NONE
    private var resizeStartY = 0
    private var resizeStartHeight = 0
    private var scrollGrab = 0

    private enum class Mode { NONE, TITLE, SCROLL, RESIZE }

    private val listArea get() = Rect(Spr.PL_LEFT_W, Spr.PL_TOP_H, baseWidth - Spr.PL_LEFT_W - Spr.PL_RIGHT_W, baseHeight - Spr.PL_TOP_H - Spr.PL_BOTTOM_H)
    private val rows get() = (listArea.h / ROW_H).coerceAtLeast(1)
    private val maxScroll get() = (app.player.tracks.size - rows).coerceAtLeast(0)

    init {
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                val x = bx(e)
                val y = by(e)
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    if (listArea.contains(x, y)) rowAt(y)?.let { if (it !in selected) selectOnly(it) }
                    showPopup(listMenu(), e.x, e.y)
                    return
                }
                when {
                    y < Spr.PL_TOP_H -> { dragMode = Mode.TITLE; windowDrag.press(e) }
                    resizeCorner().contains(x, y) -> {
                        dragMode = Mode.RESIZE
                        resizeStartY = e.yOnScreen
                        resizeStartHeight = baseHeight
                    }
                    scrollTrack().contains(x, y) -> {
                        dragMode = Mode.SCROLL
                        val handle = handleRect()
                        scrollGrab = if (handle.contains(x, y)) y - handle.y else handle.h / 2
                        scrollTo(y)
                    }
                    listArea.contains(x, y) -> clickRow(rowAt(y), e)
                    else -> buttonAt(x, y)?.let { showPopup(it.second(), it.first.x * app.scale, (it.first.y + it.first.h) * app.scale) }
                }
                repaint()
            }

            override fun mouseDragged(e: MouseEvent) {
                when (dragMode) {
                    Mode.TITLE -> windowDrag.drag(e)
                    Mode.SCROLL -> scrollTo(by(e))
                    Mode.RESIZE -> {
                        val dy = (e.yOnScreen - resizeStartY) / app.scale
                        app.resizePlaylist((resizeStartHeight + dy).coerceIn(Spr.MAIN_H, 1200))
                    }
                    Mode.NONE -> {}
                }
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (dragMode == Mode.TITLE) windowDrag.release()
                if (dragMode == Mode.RESIZE) app.saveConfig()
                dragMode = Mode.NONE
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                scroll = (scroll + e.wheelRotation * 3).coerceIn(0, maxScroll)
                repaint()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
        addMouseWheelListener(mouse)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when {
                    e.keyCode == KeyEvent.VK_DELETE -> removeSelected()
                    e.keyCode == KeyEvent.VK_A && e.isControlDown -> selectAll()
                    e.keyCode == KeyEvent.VK_ENTER -> selected.firstOrNull()?.let { app.player.playIndex(it) }
                    e.keyCode == KeyEvent.VK_DOWN -> moveSelection(1, e.isShiftDown)
                    e.keyCode == KeyEvent.VK_UP -> moveSelection(-1, e.isShiftDown)
                    e.keyCode == KeyEvent.VK_PAGE_DOWN -> scroll = (scroll + rows).coerceIn(0, maxScroll)
                    e.keyCode == KeyEvent.VK_PAGE_UP -> scroll = (scroll - rows).coerceIn(0, maxScroll)
                    else -> return
                }
                repaint()
            }
        })
    }

    // ---- geometry -------------------------------------------------------------------------------------------

    private fun rowAt(y: Int): Int? {
        val index = scroll + (y - listArea.y) / ROW_H
        return index.takeIf { it in app.player.tracks.indices }
    }

    private fun scrollTrack() = Rect(baseWidth - 15, Spr.PL_TOP_H + 2, 8, baseHeight - Spr.PL_TOP_H - Spr.PL_BOTTOM_H - 4)

    private fun handleRect(): Rect {
        val track = scrollTrack()
        val travel = (track.h - Spr.PL_SCROLL.h).coerceAtLeast(1)
        val offset = if (maxScroll == 0) 0 else (scroll * travel / maxScroll)
        return Rect(track.x, track.y + offset, Spr.PL_SCROLL.w, Spr.PL_SCROLL.h)
    }

    private fun scrollTo(y: Int) {
        val track = scrollTrack()
        val travel = (track.h - Spr.PL_SCROLL.h).coerceAtLeast(1)
        val offset = (y - scrollGrab - track.y).coerceIn(0, travel)
        scroll = (offset * maxScroll / travel).coerceIn(0, maxScroll)
    }

    private fun resizeCorner() = Rect(baseWidth - 20, baseHeight - 20, 20, 20)

    private fun buttonRect(i: Int) = Rect(11 + i * 29, baseHeight - 28, 25, 18)
    private fun listButtonRect() = Rect(baseWidth - 40, baseHeight - 28, 34, 18)

    private fun buttonAt(x: Int, y: Int): Pair<Rect, () -> JPopupMenu>? {
        for ((i, menu) in listOf(::addMenu, ::remMenu, ::selMenu, ::miscMenu).withIndex()) {
            val r = buttonRect(i)
            if (r.contains(x, y)) return r to menu
        }
        val r = listButtonRect()
        return if (r.contains(x, y)) r to ::listOptsMenu else null
    }

    // ---- selection ------------------------------------------------------------------------------------------

    private fun selectOnly(i: Int) {
        selected.clear()
        selected += i
        anchor = i
    }

    private fun clickRow(row: Int?, e: MouseEvent) {
        if (row == null) {
            if (!e.isControlDown) selected.clear()
            return
        }
        when {
            e.isShiftDown && anchor >= 0 -> {
                selected.clear()
                for (i in minOf(anchor, row)..maxOf(anchor, row)) selected += i
            }
            e.isControlDown -> {
                if (!selected.add(row)) selected.remove(row)
                anchor = row
            }
            else -> selectOnly(row)
        }
        if (e.clickCount == 2 && !e.isShiftDown && !e.isControlDown) app.player.playIndex(row)
    }

    private fun moveSelection(delta: Int, extend: Boolean) {
        val size = app.player.tracks.size
        if (size == 0) return
        val from = selected.lastOrNull() ?: (if (delta > 0) -1 else size)
        val to = (from + delta).coerceIn(0, size - 1)
        if (extend && anchor >= 0) {
            selected.clear()
            for (i in minOf(anchor, to)..maxOf(anchor, to)) selected += i
        } else {
            selectOnly(to)
        }
        ensureVisible(to)
    }

    fun selectAll() {
        selected.clear()
        selected += app.player.tracks.indices
    }

    fun removeSelected() {
        if (selected.isEmpty()) return
        app.player.removeAt(selected.toSet())
        selected.clear()
        anchor = -1
        scroll = scroll.coerceIn(0, maxScroll)
    }

    fun ensureVisible(index: Int) {
        if (index < 0) return
        if (index < scroll) scroll = index else if (index >= scroll + rows) scroll = index - rows + 1
        scroll = scroll.coerceIn(0, maxScroll)
    }

    /** Called when the playlist changes underneath the selection. */
    fun playlistChanged() {
        selected.removeAll { it >= app.player.tracks.size }
        scroll = scroll.coerceIn(0, maxScroll)
    }

    // ---- menus ----------------------------------------------------------------------------------------------

    private fun item(text: String, action: () -> Unit) = JMenuItem(text).apply { addActionListener { action(); repaint() } }

    private fun showPopup(menu: JPopupMenu, x: Int, y: Int) = menu.show(this, x, y)

    private fun addMenu() = JPopupMenu().apply {
        add(item("Add file(s)...") { app.openFiles() })
        add(item("Add folder...") { app.openFolder() })
    }

    private fun remMenu() = JPopupMenu().apply {
        add(item("Remove selected") { removeSelected() })
        add(item("Crop selection") {
            val keep = selected.toSet()
            val drop = app.player.tracks.indices.filter { it !in keep }.toSet()
            app.player.removeAt(drop)
            selected.clear()
        })
        add(item("Remove all") { app.player.clear(); selected.clear() })
    }

    private fun selMenu() = JPopupMenu().apply {
        add(item("Select all") { selectAll() })
        add(item("Select none") { selected.clear() })
        add(item("Invert selection") {
            val inverted = app.player.tracks.indices.filter { it !in selected }
            selected.clear()
            selected += inverted
        })
    }

    private fun miscMenu() = JPopupMenu().apply {
        add(item("Sort by title") { app.player.reorder { l -> l.sortedWith { a, b -> NaturalOrder.compare(a.displayName, b.displayName) } }; selected.clear() })
        add(item("Sort by file name") { app.player.reorder { l -> l.sortedWith { a, b -> NaturalOrder.compare(a.file().name, b.file().name) } }; selected.clear() })
        add(item("Reverse list") { app.player.reorder { it.reversed() }; selected.clear() })
        add(item("Randomize list") { app.player.reorder { it.shuffled(Random.Default) }; selected.clear() })
    }

    private fun listOptsMenu() = JPopupMenu().apply {
        add(item("Open playlist...") { app.openPlaylistFile() })
        add(item("Save playlist...") { app.savePlaylistFile() })
    }

    private fun listMenu() = JPopupMenu().apply {
        add(item("Play") { selected.firstOrNull()?.let { app.player.playIndex(it) } })
        add(item("Remove") { removeSelected() })
        addSeparator()
        add(addMenuItem())
    }

    private fun addMenuItem() = item("Add file(s)...") { app.openFiles() }

    // ---- painting -------------------------------------------------------------------------------------------

    override fun paintBase(g: Graphics2D) {
        val skin = app.skin
        val w = baseWidth
        val h = baseHeight
        val active = app.playlistActive

        // frame: top row, sides, bottom row
        val tl = if (active) Spr.PL_TL_ACTIVE else Spr.PL_TL_INACTIVE
        val top = if (active) Spr.PL_TOP_ACTIVE else Spr.PL_TOP_INACTIVE
        val tr = if (active) Spr.PL_TR_ACTIVE else Spr.PL_TR_INACTIVE
        val title = if (active) Spr.PL_TITLE_ACTIVE else Spr.PL_TITLE_INACTIVE
        val titleX = (w - title.w) / 2
        skin.tileX(g, Sheets.PLEDIT, top, tl.w, 0, w - tl.w - tr.w)
        skin.blit(g, Sheets.PLEDIT, tl, 0, 0)
        skin.blit(g, Sheets.PLEDIT, title, titleX, 0)
        skin.blit(g, Sheets.PLEDIT, tr, w - tr.w, 0)
        val sideH = h - Spr.PL_TOP_H - Spr.PL_BOTTOM_H
        skin.tileY(g, Sheets.PLEDIT, Spr.PL_LEFT, 0, Spr.PL_TOP_H, sideH)
        skin.tileY(g, Sheets.PLEDIT, Spr.PL_RIGHT, w - Spr.PL_RIGHT_W, Spr.PL_TOP_H, sideH)
        if (w > Spr.PL_BL.w + Spr.PL_BR.w) {
            skin.tileX(g, Sheets.PLEDIT, Spr.PL_BOTTOM, Spr.PL_BL.w, h - Spr.PL_BOTTOM_H, w - Spr.PL_BL.w - Spr.PL_BR.w)
        }
        skin.blit(g, Sheets.PLEDIT, Spr.PL_BL, 0, h - Spr.PL_BOTTOM_H)
        skin.blit(g, Sheets.PLEDIT, Spr.PL_BR, w - Spr.PL_BR.w, h - Spr.PL_BOTTOM_H)

        paintList(g)

        // scroll handle
        if (maxScroll > 0) {
            val hr = handleRect()
            skin.blit(g, Sheets.PLEDIT, if (dragMode == Mode.SCROLL) Spr.PL_SCROLL_PRESSED else Spr.PL_SCROLL, hr.x, hr.y)
        }

        // running time and total
        val tracks = app.player.tracks
        val total = tracks.sumOf { it.durationMs }
        val elapsed = TimeFormat.mmss(app.player.positionMs())
        BitmapFont.drawClipped(g, skin, "$elapsed/${TimeFormat.total(total)}", Rect(w - 140, h - 26, 92, 6), 0)
    }

    private fun paintList(g: Graphics2D) {
        val colors = app.skin.playlist
        val area = listArea
        g.color = colors.normalBg
        g.fillRect(area.x, area.y, area.w, area.h)

        val tracks = app.player.tracks
        if (tracks.isEmpty()) return
        val font = Font(colors.fontName, Font.PLAIN, 9)
        g.font = font
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val fm = g.fontMetrics
        val old = g.clip
        g.clipRect(area.x, area.y, area.w, area.h)
        val current = app.player.currentIndex
        for (r in 0 until rows + 1) {
            val i = scroll + r
            if (i >= tracks.size) break
            val y = area.y + r * ROW_H
            if (i in selected) {
                g.color = colors.selectedBg
                g.fillRect(area.x, y, area.w, ROW_H)
            }
            g.color = if (i == current) colors.current else colors.normal
            val track = tracks[i]
            val time = if (track.durationMs > 0) TimeFormat.mmss(track.durationMs) else ""
            val timeW = fm.stringWidth(time)
            var label = "${i + 1}. ${track.displayName}"
            val maxW = area.w - timeW - 8
            if (fm.stringWidth(label) > maxW) {
                while (label.length > 1 && fm.stringWidth("$label...") > maxW) label = label.dropLast(1)
                label += "..."
            }
            val baseline = y + ROW_H - 3
            g.drawString(label, area.x + 2, baseline)
            g.drawString(time, area.x + area.w - timeW - 2, baseline)
        }
        g.clip = old
    }

    private companion object {
        const val ROW_H = 13
    }
}
