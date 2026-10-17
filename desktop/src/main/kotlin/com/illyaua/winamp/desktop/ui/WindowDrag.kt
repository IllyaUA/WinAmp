package com.illyaua.winamp.desktop.ui

import java.awt.Point
import java.awt.Window
import java.awt.event.MouseEvent
import kotlin.math.abs

/**
 * Drags a window by its title bar. Windows stacked directly underneath move with it, and the
 * dragged window snaps to the edges of its neighbours so the stack is easy to rebuild.
 */
class WindowDrag(private val app: App, private val windowOf: () -> Window?) {
    private var start: Point? = null
    private var origins: List<Pair<Window, Point>> = emptyList()

    fun press(e: MouseEvent) {
        val window = windowOf() ?: return
        start = e.locationOnScreen
        origins = (listOf(window) + app.windowsBelow(window)).map { it to it.location }
    }

    fun drag(e: MouseEvent) {
        val s = start ?: return
        val now = e.locationOnScreen
        var dx = now.x - s.x
        var dy = now.y - s.y
        val moving = origins.map { it.first }.toSet()
        val (w0, p0) = origins.first()
        var nx = p0.x + dx
        var ny = p0.y + dy
        if (!e.isShiftDown) {
            for (other in app.visibleWindows()) {
                if (other in moving) continue
                val ob = other.bounds
                val wb = w0.bounds
                val overlapsX = nx < ob.x + ob.width && nx + wb.width > ob.x
                val overlapsY = ny < ob.y + ob.height && ny + wb.height > ob.y
                if (overlapsX && abs(ny - (ob.y + ob.height)) <= SNAP) ny = ob.y + ob.height
                if (overlapsX && abs(ny + wb.height - ob.y) <= SNAP) ny = ob.y - wb.height
                if (overlapsY && abs(nx - ob.x) <= SNAP && overlapsX) nx = ob.x
                if (overlapsY && abs(nx - (ob.x + ob.width)) <= SNAP) nx = ob.x + ob.width
                if (overlapsY && abs(nx + wb.width - ob.x) <= SNAP) nx = ob.x - wb.width
            }
        }
        dx = nx - p0.x
        dy = ny - p0.y
        for ((w, p) in origins) w.setLocation(p.x + dx, p.y + dy)
    }

    fun release() {
        start = null
        app.saveConfig()
    }

    private companion object {
        const val SNAP = 12
    }
}
