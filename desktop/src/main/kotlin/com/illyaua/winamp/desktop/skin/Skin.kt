package com.illyaua.winamp.desktop.skin

import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage

class PlaylistColors(
    val normal: Color = Color(0x00FF00),
    val current: Color = Color(0xFFFFFF),
    val normalBg: Color = Color(0x000000),
    val selectedBg: Color = Color(0x0000FF),
    val fontName: String = "SansSerif",
)

/**
 * A loaded skin: bitmaps by sheet name, playlist colours and the 24 analyzer colours.
 * A sheet the skin does not provide is taken from [fallback], so partial skins still render.
 */
class Skin(
    val name: String,
    private val sheets: Map<String, BufferedImage>,
    val playlist: PlaylistColors,
    val visColors: Array<Color>,
    private val fallback: Skin? = null,
) {
    fun sheet(key: String): BufferedImage? = sheets[key] ?: fallback?.sheet(key)

    fun has(key: String) = sheets.containsKey(key)

    fun blit(g: Graphics2D, sheet: String, src: Rect, dx: Int, dy: Int) =
        blit(g, sheet, src.x, src.y, src.w, src.h, dx, dy)

    /** Copies a w x h piece of [sheet] to ([dx], [dy]). A piece outside the bitmap is drawn partly or not at all. */
    fun blit(g: Graphics2D, sheet: String, sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        val img = sheet(sheet) ?: return
        g.drawImage(img, dx, dy, dx + w, dy + h, sx, sy, sx + w, sy + h, null)
    }

    /** Tiles a piece horizontally across [width] pixels, clipping the last tile. */
    fun tileX(g: Graphics2D, sheet: String, src: Rect, dx: Int, dy: Int, width: Int) {
        var x = 0
        while (x < width) {
            val w = minOf(src.w, width - x)
            blit(g, sheet, src.x, src.y, w, src.h, dx + x, dy)
            x += src.w
        }
    }

    fun tileY(g: Graphics2D, sheet: String, src: Rect, dx: Int, dy: Int, height: Int) {
        var y = 0
        while (y < height) {
            val h = minOf(src.h, height - y)
            blit(g, sheet, src.x, src.y, src.w, h, dx, dy + y)
            y += src.h
        }
    }
}
