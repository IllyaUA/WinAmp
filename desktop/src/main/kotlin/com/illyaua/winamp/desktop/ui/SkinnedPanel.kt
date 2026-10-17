package com.illyaua.winamp.desktop.ui

import com.illyaua.winamp.desktop.skin.Rect
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent

fun Rect.contains(px: Int, py: Int) = px >= x && py >= y && px < x + w && py < y + h

/**
 * A component drawn at classic pixel size into an off-screen image, then scaled by a whole number
 * with nearest-neighbour sampling so skins stay pixel-sharp at 2x, 3x and so on.
 */
abstract class SkinnedPanel(protected val app: App, val baseWidth: Int, baseHeight: Int) : JComponent() {
    var baseHeight: Int = baseHeight
        set(value) {
            field = value
            updateSize()
        }

    private var buffer: BufferedImage? = null

    init {
        isOpaque = true
        isFocusable = true
        updateSize()
    }

    fun updateSize() {
        val s = app.scale
        val d = Dimension(baseWidth * s, baseHeight * s)
        preferredSize = d
        minimumSize = d
        revalidate()
        repaint()
    }

    protected abstract fun paintBase(g: Graphics2D)

    override fun paintComponent(g: Graphics) {
        var buf = buffer
        if (buf == null || buf.width != baseWidth || buf.height != baseHeight) {
            buf = BufferedImage(baseWidth, baseHeight, BufferedImage.TYPE_INT_ARGB)
            buffer = buf
        }
        val bg = buf.createGraphics()
        bg.composite = java.awt.AlphaComposite.Src
        bg.color = java.awt.Color.BLACK
        bg.fillRect(0, 0, baseWidth, baseHeight)
        bg.composite = java.awt.AlphaComposite.SrcOver
        try {
            paintBase(bg)
        } finally {
            bg.dispose()
        }
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        val s = app.scale
        g2.drawImage(buf, 0, 0, baseWidth * s, baseHeight * s, null)
        g2.dispose()
    }

    /** Renders at 1x for tests and screenshots. */
    fun renderBase(): BufferedImage {
        val img = BufferedImage(baseWidth, baseHeight, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        try {
            paintBase(g)
        } finally {
            g.dispose()
        }
        return img
    }

    protected fun bx(e: MouseEvent) = e.x / app.scale
    protected fun by(e: MouseEvent) = e.y / app.scale
}
