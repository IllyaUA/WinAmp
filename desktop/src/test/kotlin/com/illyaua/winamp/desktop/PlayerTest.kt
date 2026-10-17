package com.illyaua.winamp.desktop

import com.illyaua.winamp.core.Equalizer10
import com.illyaua.winamp.desktop.audio.Player
import com.illyaua.winamp.desktop.audio.Status
import com.illyaua.winamp.desktop.audio.TrackReader
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var sinks: FakeSinks
    private lateinit var player: Player

    @Before fun setUp() {
        sinks = FakeSinks(speedup = 10)
        player = Player(sinks)
    }

    @After fun tearDown() = player.close()

    private fun wav(name: String, seconds: Double, freq: Double = 440.0, channels: Int = 2) =
        TestAudio.wav(File(tmp.root, name), seconds, freq, channels = channels)

    private fun add(vararg files: File) {
        player.addTracks(files.map { TrackReader.quick(it) })
    }

    @Test fun readsDurationAndFormatFromAWavFile() {
        val track = TrackReader.read(TrackReader.quick(wav("a.wav", 1.0)))
        assertEquals(1000.0, track.durationMs.toDouble(), 20.0)
        assertEquals(44100, track.sampleRateHz)
        assertEquals(2, track.channels)
    }

    @Test fun playsATrackToTheEndAndThenStops() {
        add(wav("a.wav", 1.0))
        player.play()
        waitFor(what = "playing") { player.status == Status.PLAYING }
        waitFor(what = "stopped at the end") { player.status == Status.STOPPED }
        assertEquals(44100.0, sinks.last.frames.toDouble(), 1024.0)
        assertTrue(sinks.last.closed)
        assertEquals(0L, player.positionMs())
    }

    @Test fun positionAdvancesWhilePlaying() {
        add(wav("long.wav", 6.0))
        player.play()
        waitFor(what = "position moving") { player.positionMs() > 400 }
        assertEquals(Status.PLAYING, player.status)
    }

    @Test fun pauseHoldsTheStreamAndResumeContinues() {
        add(wav("long.wav", 8.0))
        player.play()
        waitFor { player.positionMs() > 200 }
        player.pause()
        assertEquals(Status.PAUSED, player.status)
        Thread.sleep(80) // let a chunk already in flight finish
        val held = sinks.last.frames
        Thread.sleep(250)
        assertEquals("nothing is written while paused", held, sinks.last.frames)
        assertTrue(sinks.last.paused)
        player.resume()
        waitFor(what = "writing again") { sinks.last.frames > held + 2048 }
        assertEquals(Status.PLAYING, player.status)
    }

    @Test fun seekingJumpsForwardAndSkipsTheAudioInBetween() {
        add(wav("long.wav", 8.0))
        player.play()
        waitFor { player.positionMs() > 100 }
        player.seek(6500)
        waitFor(what = "position after seek") { player.positionMs() >= 6500 }
        waitFor(what = "end of track") { player.status == Status.STOPPED }
        val written = sinks.last.frames
        assertTrue("skipped audio was not played: $written", written < 44100 * 8 - 44100 * 4)
    }

    @Test fun nextTrackStartsAutomaticallyAndRepeatWrapsAround() {
        add(wav("a.wav", 0.4), wav("b.wav", 0.4))
        player.setRepeat(true)
        player.play()
        waitFor(what = "second track") { player.currentIndex == 1 }
        waitFor(what = "wrap to first track") { player.currentIndex == 0 && sinks.opened.size >= 3 }
        assertEquals(Status.PLAYING, player.status)
    }

    @Test fun withoutRepeatTheLastTrackEndsPlayback() {
        add(wav("a.wav", 0.3), wav("b.wav", 0.3))
        player.play()
        waitFor { player.currentIndex == 1 }
        waitFor(what = "stop") { player.status == Status.STOPPED }
        assertEquals(1, player.currentIndex)
    }

    @Test fun nextAndPreviousFollowThePlaylist() {
        add(wav("a.wav", 5.0), wav("b.wav", 5.0), wav("c.wav", 5.0))
        player.playIndex(1)
        waitFor { player.status == Status.PLAYING }
        player.next()
        assertEquals(2, player.currentIndex)
        player.previous() // under three seconds in, so this goes back a track
        assertEquals(1, player.currentIndex)
    }

    @Test fun removingThePlayingTrackStopsAndKeepsTheOthers() {
        add(wav("a.wav", 5.0), wav("b.wav", 5.0), wav("c.wav", 5.0))
        player.playIndex(1)
        waitFor { player.status == Status.PLAYING }
        player.removeAt(setOf(1))
        assertEquals(Status.STOPPED, player.status)
        assertEquals(listOf("a", "c"), player.tracks.map { it.title })
        assertEquals(1, player.currentIndex)
    }

    @Test fun removingEarlierTracksShiftsTheCurrentIndex() {
        add(wav("a.wav", 5.0), wav("b.wav", 5.0), wav("c.wav", 5.0))
        player.playIndex(2)
        player.removeAt(setOf(0))
        assertEquals(1, player.currentIndex)
        assertEquals("c", player.currentTrack!!.title)
    }

    @Test fun reorderKeepsThePlayingTrackSelected() {
        add(wav("a.wav", 5.0), wav("b.wav", 5.0), wav("c.wav", 5.0))
        player.playIndex(0)
        player.reorder { it.reversed() }
        assertEquals("a", player.currentTrack!!.title)
        assertEquals(2, player.currentIndex)
    }

    @Test fun volumeScalesTheOutput() {
        add(wav("a.wav", 0.5))
        player.setVolume(1f)
        player.play()
        waitFor { player.status == Status.STOPPED }
        val loud = sinks.last.rms(0)

        player.setVolume(0.5f)
        player.play()
        waitFor { sinks.opened.size == 2 }
        waitFor { player.status == Status.STOPPED }
        val quiet = sinks.opened[1].rms(0)
        assertEquals("volume squared gives 0.25", 0.25, quiet / loud, 0.03)
    }

    @Test fun balanceSilencesOneSide() {
        add(wav("a.wav", 0.5))
        player.setVolume(1f)
        player.setBalance(1f)
        player.play()
        waitFor { player.status == Status.STOPPED }
        assertTrue(sinks.last.rms(0) < 0.001)
        assertTrue(sinks.last.rms(1) > 0.2)
    }

    @Test fun equalizerBandBoostsTheMatchingFrequency() {
        add(TestAudio.wav(File(tmp.root, "tone.wav"), 0.6, freq = 1000.0, amplitude = 0.08)) // quiet enough that +12 dB does not clip
        player.setVolume(1f)
        player.setEqEnabled(false)
        player.play()
        waitFor { player.status == Status.STOPPED }
        val flat = sinks.last.rms(0)

        player.setEqEnabled(true)
        player.setEqBand(4, 12f) // 1 kHz
        player.play()
        waitFor { sinks.opened.size == 2 }
        waitFor { player.status == Status.STOPPED }
        val boosted = sinks.opened[1].rms(0)
        // +12 dB is a factor of 4, but the filter needs a moment to settle and the output clips softly
        assertTrue("boost ratio ${boosted / flat}", boosted / flat in 3.0..4.4)
        assertEquals(Equalizer10.BANDS, 10)
    }

    @Test fun analyzerShowsEnergyForAToneAndFallsWhenStopped() {
        add(wav("tone.wav", 3.0, freq = 1000.0))
        player.setVolume(1f)
        player.play()
        waitFor { player.positionMs() > 300 }
        val frame = player.pollSpectrum()
        assertTrue("some bar is lit: ${frame.bars.toList()}", frame.bars.max() > 0.3f)
        player.stop()
        repeat(40) { player.pollSpectrum() }
        assertTrue(player.pollSpectrum().bars.all { it < 0.05f })
    }

    @Test fun anUnreadableFileReportsAnErrorInsteadOfCrashing() {
        val bad = File(tmp.root, "bad.mp3").also { it.writeText("this is not audio") }
        add(bad)
        player.play()
        waitFor(what = "error") { player.lastError != null && player.status == Status.STOPPED }
        assertTrue(player.lastError!!.startsWith("Cannot play"))
    }

    @Test fun aBadTrackIsSkippedWhenTheNextOneIsFine() {
        val bad = File(tmp.root, "bad.mp3").also { it.writeText("nope") }
        add(bad, wav("ok.wav", 2.0))
        player.play()
        waitFor(what = "skip to the good track") { player.currentIndex == 1 && player.status == Status.PLAYING }
        waitFor { sinks.opened.isNotEmpty() }
    }

    @Test fun stoppedPlayerIgnoresSeekPauseAndNext() {
        player.pause(); player.resume(); player.seek(5000); player.next(); player.previous(); player.play()
        assertEquals(Status.STOPPED, player.status)
        assertNull(player.currentTrack)
        assertFalse(player.shuffle)
    }

    @Test fun addFilesScansFoldersRecursivelyInNaturalOrder() {
        val dir = tmp.newFolder("album")
        wav("album/10.wav", 0.1); wav("album/2.wav", 0.1)
        File(dir, "cd2").mkdirs(); TestAudio.wav(File(dir, "cd2/1.wav"), 0.1)
        File(dir, "cover.jpg").writeText("x")
        player.addFiles(listOf(dir))
        waitFor(what = "tracks added") { player.tracks.size == 3 }
        assertEquals(listOf("2", "10", "1"), player.tracks.map { it.title })
        waitFor(what = "metadata") { player.tracks.all { it.durationMs > 0 } }
        assertNotNull(player.tracks[0].sampleRateHz)
    }

    @Test fun addFilesWithNothingPlayableSetsAnError() {
        player.addFiles(listOf(tmp.newFolder("empty")))
        waitFor(what = "error") { player.lastError != null }
        assertTrue(player.tracks.isEmpty())
    }
}
