package com.illyaua.winamp.desktop.ui

import java.awt.Color
import java.awt.image.BufferedImage

object AppIcon {
    fun create(size: Int = 64): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        val u = size / 64f
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x1B1B26)
        g.fillRoundRect(0, 0, size, size, (14 * u).toInt(), (14 * u).toInt())
        g.color = Color(0xE6A91C)
        val xs = intArrayOf(36, 16, 29, 24, 48, 35).map { (it * u).toInt() }.toIntArray()
        val ys = intArrayOf(6, 36, 36, 58, 26, 26).map { (it * u).toInt() }.toIntArray()
        g.fillPolygon(xs, ys, 6)
        g.dispose()
        return img
    }
}
