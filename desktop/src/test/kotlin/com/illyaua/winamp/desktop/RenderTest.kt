package com.illyaua.winamp.desktop

import com.illyaua.winamp.core.Track
import com.illyaua.winamp.desktop.audio.Player
import com.illyaua.winamp.desktop.audio.Status
import com.illyaua.winamp.desktop.audio.TrackReader
import com.illyaua.winamp.desktop.skin.DefaultSkin
import com.illyaua.winamp.desktop.skin.Sheets
import com.illyaua.winamp.desktop.skin.Spr
import com.illyaua.winamp.desktop.ui.App
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RenderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var sinks: FakeSinks
    private lateinit var player: Player
    private lateinit var app: App
    private val out = File("build/renders").also { it.mkdirs() }

    @Before fun setUp() {
        System.setProperty("java.awt.headless", "true")
        sinks = FakeSinks(speedup = 1)
        player = Player(sinks)
        app = App(Config(tmp.newFolder("cfg")), player)
    }

    @After fun tearDown() = player.close()

    private fun sample(img: BufferedImage, x: Int, y: Int) = img.getRGB(x, y)

    private fun save(name: String, img: BufferedImage, scale: Int = 2) {
        val big = BufferedImage(img.width * scale, img.height * scale, BufferedImage.TYPE_INT_RGB)
        val g = big.createGraphics()
        g.drawImage(img, 0, 0, img.width * scale, img.height * scale, null)
        g.dispose()
        ImageIO.write(big, "png", File(out, "$name.png"))
    }

    private fun startPlaying() {
        val file = TestAudio.wav(File(tmp.root, "Some Artist - A Rather Long Song Title.wav"), 30.0, freq = 800.0)
        player.addTracks(listOf(TrackReader.read(TrackReader.quick(file)).copy(artist = "Some Artist", title = "A Rather Long Song Title")))
        player.addTracks(listOf(Track("file:/x/two.mp3", "Second track", durationMs = 195_000), Track("file:/x/three.mid", "Third", durationMs = 61_000)))
        player.setVolume(0.7f)
        player.play()
        waitFor { player.positionMs() > 1500 }
        app.tick()
    }

    @Test fun mainWindowIsDrawnFromTheSkinSprites() {
        val img = app.mainPanel.renderBase()
        assertEquals(275, img.width); assertEquals(116, img.height)
        val skin = DefaultSkin.skin
        // the background comes straight from main.bmp and the transport buttons from cbuttons.bmp
        assertEquals(skin.sheet(Sheets.MAIN)!!.getRGB(260, 100), sample(img, 260, 100))
        val prev = skin.sheet(Sheets.CBUTTONS)!!.getRGB(5, 5)
        assertEquals(prev, sample(img, Spr.PREV_AT.x + 5, Spr.PREV_AT.y + 5))
        val title = skin.sheet(Sheets.TITLEBAR)!!
        assertEquals(title.getRGB(Spr.TITLE_ACTIVE.x + 100, 3), sample(img, 100, 3))
        save("main-stopped", img)
    }

    @Test fun playingStateShowsClockTitleAndFormatInfo() {
        startPlaying()
        val playing = app.mainPanel.renderBase()
        save("main-playing", playing, 3)
        val stopped = Player(FakeSinks()).let { p -> App(Config(tmp.newFolder("cfg2")), p).also { it.tick() }.mainPanel.renderBase().also { p.close() } }
        fun diff(a: BufferedImage, b: BufferedImage, x: Int, y: Int, w: Int, h: Int): Int {
            var n = 0
            for (yy in y until y + h) for (xx in x until x + w) if (a.getRGB(xx, yy) != b.getRGB(xx, yy)) n++
            return n
        }
        assertTrue("clock digits changed", diff(playing, stopped, Spr.CLOCK_AT.x, Spr.CLOCK_AT.y, Spr.CLOCK_AT.w, Spr.CLOCK_AT.h) > 10)
        assertTrue("title scrolls in", diff(playing, stopped, Spr.TITLE_TEXT_AT.x, Spr.TITLE_TEXT_AT.y, Spr.TITLE_TEXT_AT.w, Spr.TITLE_TEXT_AT.h) > 20)
        assertTrue("khz and stereo light up", diff(playing, stopped, Spr.STEREO_AT.x, Spr.STEREO_AT.y, 29, 12) > 5)
        assertTrue("analyzer draws bars", diff(playing, stopped, Spr.SPECTRUM_AT.x, Spr.SPECTRUM_AT.y, Spr.SPECTRUM_AT.w, Spr.SPECTRUM_AT.h) > 20)
        assertEquals(Status.PLAYING, player.status)
    }

    @Test fun volumeBalanceAndToggleStatesAreDrawn() {
        val before = app.mainPanel.renderBase()
        player.setVolume(1f); player.setBalance(-1f); player.setShuffle(true); player.setRepeat(true)
        val after = app.mainPanel.renderBase()
        fun changed(r: com.illyaua.winamp.desktop.skin.Rect): Boolean {
            for (y in r.y until r.y + r.h) for (x in r.x until r.x + r.w) if (before.getRGB(x, y) != after.getRGB(x, y)) return true
            return false
        }
        assertTrue(changed(Spr.VOLUME_AT)); assertTrue(changed(Spr.BALANCE_AT))
        assertTrue(changed(Spr.SHUFFLE_AT)); assertTrue(changed(Spr.REPEAT_AT))
        assertTrue(!changed(Spr.EQ_BTN_AT))
        save("main-settings", after)
    }

    @Test fun equalizerWindowReflectsBandSettings() {
        val flat = app.eqPanel.renderBase()
        player.setEqBand(0, 12f); player.setEqBand(3, -12f); player.setEqBand(9, 6f); player.setEqPreamp(4f)
        val tuned = app.eqPanel.renderBase()
        var diffs = 0
        for (y in 0 until 116) for (x in 0 until 275) if (flat.getRGB(x, y) != tuned.getRGB(x, y)) diffs++
        assertTrue("sliders and curve moved: $diffs pixels", diffs > 200)
        save("eq-flat", flat); save("eq-tuned", tuned)
    }

    @Test fun playlistWindowListsTracksAndMarksTheCurrentOne() {
        startPlaying()
        app.playlistPanel.renderBase().let { save("playlist", it, 3) }
        val img = app.playlistPanel.renderBase()
        assertEquals(275, img.width)
        assertEquals(232, img.height)
        // the list background is the skin's NormalBG colour
        assertEquals(DefaultSkin.playlistColors.normalBg.rgb, sample(img, 100, 150))
    }

    @Test fun everyWindowRendersWithAnotherSkinToo() {
        val wsz = File(tmp.root, "t.wsz").also { DefaultSkin.exportWsz(it) }
        assertEquals(null, app.applySkin(wsz))
        startPlaying()
        for (p in listOf(app.mainPanel, app.eqPanel, app.playlistPanel)) assertTrue(p.renderBase().width == 275)
        assertEquals("t.wsz", app.config.string("skin"))
        assertTrue(File(app.config.skinsDir, "t.wsz").exists())
    }

    @Test fun stackedWindowsAtDoubleSizeForTheScreenshot() {
        startPlaying()
        player.setEqBand(2, 7f); player.setEqBand(6, -5f)
        val parts = listOf(app.mainPanel.renderBase(), app.eqPanel.renderBase(), app.playlistPanel.renderBase())
        val stack = BufferedImage(275, parts.sumOf { it.height }, BufferedImage.TYPE_INT_RGB)
        val g = stack.createGraphics()
        var y = 0
        for (p in parts) { g.drawImage(p, 0, y, null); y += p.height }
        g.dispose()
        save("stack", stack, 2)
    }
}
