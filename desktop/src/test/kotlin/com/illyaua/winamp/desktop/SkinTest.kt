package com.illyaua.winamp.desktop

import com.illyaua.winamp.desktop.skin.BitmapFont
import com.illyaua.winamp.desktop.skin.DefaultSkin
import com.illyaua.winamp.desktop.skin.Sheets
import com.illyaua.winamp.desktop.skin.SkinLoader
import com.illyaua.winamp.desktop.skin.Spr
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SkinTest {
    @get:Rule val tmp = TemporaryFolder()

    private val skin = DefaultSkin.skin

    private fun size(sheet: String) = skin.sheet(sheet)!!.let { it.width to it.height }

    @Test fun defaultSkinSheetsMatchTheClassicLayout() {
        assertEquals(275 to 116, size(Sheets.MAIN))
        assertEquals(136 to 36, size(Sheets.CBUTTONS))
        assertEquals(344 to 87, size(Sheets.TITLEBAR))
        assertEquals(99 to 13, size(Sheets.NUMBERS))
        assertEquals(42 to 9, size(Sheets.PLAYPAUS))
        assertEquals(56 to 24, size(Sheets.MONOSTEREO))
        assertEquals(68 to 433, size(Sheets.VOLUME))
        assertEquals(47 to 433, size(Sheets.BALANCE))
        assertEquals(307 to 10, size(Sheets.POSBAR))
        assertEquals(92 to 85, size(Sheets.SHUFREP))
        assertEquals(155 to 18, size(Sheets.TEXT))
        assertEquals(275 to 315, size(Sheets.EQMAIN))
        assertEquals(280 to 186, size(Sheets.PLEDIT))
        assertEquals(24, skin.visColors.size)
    }

    @Test fun everySpriteRectangleFitsInsideItsSheet() {
        fun fits(sheet: String, r: com.illyaua.winamp.desktop.skin.Rect) {
            val (w, h) = size(sheet)
            assertTrue("$sheet $r", r.x >= 0 && r.y >= 0 && r.x + r.w <= w && r.y + r.h <= h)
        }
        listOf(Spr.PREV, Spr.PLAY, Spr.PAUSE, Spr.STOP, Spr.NEXT).forEach { fits(Sheets.CBUTTONS, it) }
        fits(Sheets.CBUTTONS, Spr.EJECT.copy(y = Spr.EJECT.y + Spr.EJECT_PRESSED_DY))
        listOf(Spr.TITLE_ACTIVE, Spr.TITLE_INACTIVE, Spr.TB_MENU, Spr.TB_MINIMIZE, Spr.TB_CLOSE).forEach { fits(Sheets.TITLEBAR, it) }
        listOf(Spr.PS_PLAY, Spr.PS_PAUSE, Spr.PS_STOP).forEach { fits(Sheets.PLAYPAUS, it) }
        listOf(Spr.STEREO_ON, Spr.STEREO_OFF, Spr.MONO_ON, Spr.MONO_OFF).forEach { fits(Sheets.MONOSTEREO, it) }
        fits(Sheets.VOLUME, Spr.SLIDER_THUMB); fits(Sheets.VOLUME, Spr.SLIDER_THUMB_PRESSED)
        fits(Sheets.POSBAR, Spr.POS_THUMB); fits(Sheets.POSBAR, Spr.POS_THUMB_PRESSED)
        fits(Sheets.SHUFREP, Spr.SHUFFLE.copy(y = Spr.SR_SELECTED_PRESSED_DY))
        fits(Sheets.SHUFREP, Spr.REPEAT.copy(y = Spr.SR_SELECTED_PRESSED_DY))
        fits(Sheets.SHUFREP, Spr.EQ_BTN.copy(x = Spr.EQ_BTN.x + Spr.TOGGLE_PRESSED_DX, y = Spr.EQ_BTN.y + Spr.TOGGLE_SELECTED_DY))
        fits(Sheets.SHUFREP, Spr.PL_BTN.copy(x = Spr.PL_BTN.x + Spr.TOGGLE_PRESSED_DX, y = Spr.PL_BTN.y + Spr.TOGGLE_SELECTED_DY))
        listOf(Spr.EQ_BG, Spr.EQ_TITLE_ACTIVE, Spr.EQ_TITLE_INACTIVE, Spr.EQ_THUMB, Spr.EQ_THUMB_PRESSED, Spr.EQ_GRAPH_COLORS, Spr.EQ_PREAMP_LINE).forEach { fits(Sheets.EQMAIN, it) }
        fits(Sheets.EQMAIN, Spr.EQ_ON.copy(x = Spr.EQ_ON.x + Spr.EQ_BTN_SELECTED_DX + Spr.EQ_BTN_PRESSED_DX))
        fits(Sheets.EQMAIN, Spr.EQ_AUTO.copy(x = Spr.EQ_AUTO.x + Spr.EQ_BTN_SELECTED_DX + Spr.EQ_BTN_PRESSED_DX))
        fits(Sheets.EQMAIN, Spr.EQ_PRESETS.copy(y = Spr.EQ_PRESETS.y + Spr.EQ_PRESETS_PRESSED_DY))
        // last slider background state: column 13, row 1
        fits(Sheets.EQMAIN, com.illyaua.winamp.desktop.skin.Rect(Spr.EQ_SLIDER_BG_X + 13 * Spr.EQ_SLIDER_COL_STEP, Spr.EQ_SLIDER_BG_Y + Spr.EQ_SLIDER_ROW_STEP, Spr.EQ_SLIDER_W, Spr.EQ_SLIDER_H))
        listOf(Spr.PL_TL_ACTIVE, Spr.PL_TL_INACTIVE, Spr.PL_TITLE_ACTIVE, Spr.PL_TOP_ACTIVE, Spr.PL_TR_ACTIVE, Spr.PL_LEFT, Spr.PL_RIGHT, Spr.PL_BL, Spr.PL_BOTTOM, Spr.PL_BR, Spr.PL_SCROLL, Spr.PL_SCROLL_PRESSED).forEach { fits(Sheets.PLEDIT, it) }
        fits(Sheets.VOLUME, com.illyaua.winamp.desktop.skin.Rect(0, 27 * Spr.SLIDER_STATE_DY, 68, 13))
    }

    @Test fun exportedSkinLoadsBackIdentically() {
        val wsz = File(tmp.root, "template.wsz")
        DefaultSkin.exportWsz(wsz)
        val loaded = SkinLoader.load(wsz, skin)
        for (name in Sheets.ALL) {
            assertTrue("$name loaded from the file", loaded.has(name))
            val a = skin.sheet(name)!!
            val b = loaded.sheet(name)!!
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            for (y in 0 until a.height step 7) for (x in 0 until a.width step 5) {
                assertEquals("$name at $x,$y", a.getRGB(x, y) or (0xFF shl 24), b.getRGB(x, y) or (0xFF shl 24))
            }
        }
        assertEquals(skin.visColors.toList(), loaded.visColors.toList())
        assertEquals(skin.playlist.selectedBg, loaded.playlist.selectedBg)
        assertEquals("template", loaded.name)
    }

    private fun bmp(w: Int, h: Int, color: Color): ByteArray {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics(); g.color = color; g.fillRect(0, 0, w, h); g.dispose()
        return ByteArrayOutputStream().also { ImageIO.write(img, "bmp", it) }.toByteArray()
    }

    private fun zip(file: File, entries: Map<String, ByteArray>): File {
        ZipOutputStream(file.outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return file
    }

    @Test fun realWorldQuirksFileNamesInSubfoldersWithOddCaseAndBackslashes() {
        val wsz = zip(File(tmp.root, "quirky.wsz"), mapOf(
            "Skin Folder\\MAIN.BMP" to bmp(275, 116, Color(10, 20, 30)),
            "Skin Folder/Cbuttons.bmp" to bmp(136, 36, Color.RED),
            "Skin Folder/readme.txt" to "hello".toByteArray(),
            "Skin Folder/PLEDIT.TXT" to "[Text]\r\nNormal=#112233\r\nCurrent=FFEEDD\r\nNormalBG=#000010\r\nSelectedBG=#0000C6\r\nFont=Tahoma\r\n".toByteArray(),
            "viscolor.txt" to "0,0,0 // bg\r\n1,2,3\r\n  4 , 5 ,6 // spaced\r\nbogus\r\n7,8,9\r\n".toByteArray(),
        ))
        val s = SkinLoader.load(wsz, skin)
        assertTrue(s.has(Sheets.MAIN)); assertTrue(s.has(Sheets.CBUTTONS))
        assertEquals(Color(10, 20, 30).rgb, s.sheet(Sheets.MAIN)!!.getRGB(5, 5))
        assertEquals(Color(0x11, 0x22, 0x33), s.playlist.normal)
        assertEquals(Color(0xFF, 0xEE, 0xDD), s.playlist.current)
        assertEquals("Tahoma", s.playlist.fontName)
        assertEquals(Color(1, 2, 3), s.visColors[1])
        assertEquals(Color(4, 5, 6), s.visColors[2])
        assertEquals(Color(7, 8, 9), s.visColors[3])
        assertEquals(skin.visColors[4], s.visColors[4])
    }

    @Test fun missingSheetsFallBackToTheBuiltInSkin() {
        val wsz = zip(File(tmp.root, "partial.wsz"), mapOf("main.bmp" to bmp(275, 116, Color.BLUE)))
        val s = SkinLoader.load(wsz, skin)
        assertFalse(s.has(Sheets.NUMBERS))
        assertNotNull(s.sheet(Sheets.NUMBERS))
        assertEquals(99, s.sheet(Sheets.NUMBERS)!!.width)
    }

    @Test fun nonSkinsAndBrokenFilesAreRejectedWithAMessage() {
        val noMain = zip(File(tmp.root, "nomain.wsz"), mapOf("cbuttons.bmp" to bmp(136, 36, Color.RED)))
        try { SkinLoader.load(noMain, skin); fail() } catch (e: SkinLoader.SkinException) { assertTrue(e.message!!.contains("main.bmp")) }
        val junk = File(tmp.root, "junk.wsz").also { it.writeText("not a zip") }
        try { SkinLoader.load(junk, skin); fail() } catch (e: SkinLoader.SkinException) { assertTrue(e.message!!.contains("junk.wsz")) }
        val corruptImage = zip(File(tmp.root, "corrupt.wsz"), mapOf("main.bmp" to bmp(275, 116, Color.RED), "numbers.bmp" to byteArrayOf(1, 2, 3)))
        assertFalse(SkinLoader.load(corruptImage, skin).has(Sheets.NUMBERS))
    }

    @Test fun skinCanBeLoadedFromAFolder() {
        val dir = tmp.newFolder("folderskin")
        File(dir, "main.bmp").writeBytes(bmp(275, 116, Color.GREEN))
        assertTrue(SkinLoader.load(dir, skin).has(Sheets.MAIN))
    }

    @Test fun bitmapFontCellsFollowTheTextBmpLayout() {
        assertEquals(0 to 0, BitmapFont.cell('A'))
        assertEquals(0 to 0, BitmapFont.cell('a'))
        assertEquals(25 to 0, BitmapFont.cell('Z'))
        assertEquals(0 to 1, BitmapFont.cell('0'))
        assertEquals(9 to 1, BitmapFont.cell('9'))
        assertEquals(10 to 1, BitmapFont.cell('.'))
        assertEquals(11 to 1, BitmapFont.cell(':'))
        assertNull(BitmapFont.cell(' '))
        assertEquals(BitmapFont.cell('?'), BitmapFont.cell('中'))
        assertEquals(15, BitmapFont.width("abc"))
    }

    @Test fun bitmapFontDrawsGlyphPixels() {
        val img = BufferedImage(40, 8, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        BitmapFont.draw(g, skin, "A", 2, 1)
        g.dispose()
        var lit = 0
        for (y in 0 until 8) for (x in 0 until 40) if (img.getRGB(x, y) ushr 24 != 0) lit++
        assertTrue("glyph A should paint pixels", lit in 10..30)
        assertEquals(0, img.getRGB(30, 4) ushr 24)
    }
}
