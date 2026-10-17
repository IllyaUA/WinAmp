package com.illyaua.winamp.desktop.skin

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO

/**
 * Loads classic Winamp skins: a .wsz (zip) or a folder holding main.bmp, cbuttons.bmp and the rest.
 * File names are matched without regard to case or folder, as real skins are inconsistent about both.
 */
object SkinLoader {
    private const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    private const val MAX_ENTRIES = 600

    class SkinException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun load(file: File, fallback: Skin): Skin {
        val files = LinkedHashMap<String, ByteArray>()
        try {
            if (file.isDirectory) {
                file.walkTopDown().maxDepth(3).filter { it.isFile && it.length() <= MAX_ENTRY_BYTES }.take(MAX_ENTRIES)
                    .forEach { files.putIfAbsent(it.name.lowercase(), it.readBytes()) }
            } else {
                ZipFile(file).use { zip ->
                    var count = 0
                    for (entry in zip.entries()) {
                        if (entry.isDirectory || entry.size > MAX_ENTRY_BYTES || ++count > MAX_ENTRIES) continue
                        val base = entry.name.replace('\\', '/').substringAfterLast('/').lowercase()
                        if (base.isEmpty() || files.containsKey(base)) continue
                        files[base] = zip.getInputStream(entry).use { it.readNBytes(MAX_ENTRY_BYTES) }
                    }
                }
            }
        } catch (e: Exception) {
            throw SkinException("Cannot read skin ${file.name}: ${e.message}", e)
        }
        val skin = fromFiles(file.nameWithoutExtension, files, fallback)
        if (!skin.has(Sheets.MAIN)) throw SkinException("${file.name} has no main.bmp, so it is not a Winamp skin")
        return skin
    }

    /** [files] maps lower-case base names (with extension) to their bytes. */
    fun fromFiles(name: String, files: Map<String, ByteArray>, fallback: Skin): Skin {
        val sheets = HashMap<String, BufferedImage>()
        for ((fileName, bytes) in files) {
            val ext = fileName.substringAfterLast('.', "")
            if (ext != "bmp" && ext != "png" && ext != "gif") continue
            val key = fileName.substringBeforeLast('.')
            if (key !in Sheets.ALL || (sheets.containsKey(key) && ext != "bmp")) continue
            val img = try {
                ImageIO.read(ByteArrayInputStream(bytes))
            } catch (_: Exception) {
                null
            } ?: continue
            sheets[key] = toArgb(img)
        }
        val playlist = files["pledit.txt"]?.let { parsePlaylistColors(String(it, Charsets.ISO_8859_1), fallback.playlist) }
            ?: fallback.playlist
        val vis = files["viscolor.txt"]?.let { parseVisColors(String(it, Charsets.ISO_8859_1), fallback.visColors) }
            ?: fallback.visColors
        return Skin(name, sheets, playlist, vis, fallback)
    }

    private fun toArgb(src: BufferedImage): BufferedImage {
        if (src.type == BufferedImage.TYPE_INT_ARGB) return src
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }

    fun parsePlaylistColors(text: String, base: PlaylistColors): PlaylistColors {
        val values = HashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("[") || line.startsWith(";") || '=' !in line) continue
            values[line.substringBefore('=').trim().lowercase()] = line.substringAfter('=').trim()
        }
        fun color(key: String, default: Color): Color {
            val hex = values[key]?.removePrefix("#")?.take(6) ?: return default
            return hex.toIntOrNull(16)?.let { Color(it) } ?: default
        }
        return PlaylistColors(
            normal = color("normal", base.normal),
            current = color("current", base.current),
            normalBg = color("normalbg", base.normalBg),
            selectedBg = color("selectedbg", base.selectedBg),
            fontName = values["font"]?.takeIf { it.isNotBlank() } ?: base.fontName,
        )
    }

    /** viscolor.txt: one "r,g,b" per line, optionally followed by a comment; at most 24 colours are used. */
    fun parseVisColors(text: String, base: Array<Color>): Array<Color> {
        val out = base.copyOf()
        var n = 0
        for (raw in text.lineSequence()) {
            if (n >= out.size) break
            val parts = raw.substringBefore("//").split(',').map { it.trim().takeWhile { c -> c.isDigit() } }
            if (parts.size < 3) continue
            val rgb = parts.take(3).map { it.toIntOrNull() ?: return@map null }
            if (rgb.any { it == null }) continue
            out[n++] = Color(rgb[0]!!.coerceIn(0, 255), rgb[1]!!.coerceIn(0, 255), rgb[2]!!.coerceIn(0, 255))
        }
        return out
    }
}
