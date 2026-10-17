package com.illyaua.winamp.desktop

import com.illyaua.winamp.core.TrackKind
import com.illyaua.winamp.desktop.audio.Player
import com.illyaua.winamp.desktop.audio.Status
import com.illyaua.winamp.desktop.audio.TrackReader
import java.io.File
import javax.sound.midi.MidiEvent
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FormatsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun decodeAll(file: File): ByteArray = TrackReader.openPcm(file).use { it.readAllBytes() }

    private fun peak(pcm: ByteArray): Int {
        var m = 0
        var i = 0
        while (i + 1 < pcm.size) {
            m = maxOf(m, kotlin.math.abs(((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()))
            i += 2
        }
        return m
    }

    @Test fun mp3DecodesAndCarriesItsTags() {
        val f = TestAudio.resource("tone.mp3", tmp.root)
        val track = TrackReader.read(TrackReader.quick(f))
        assertEquals(TrackKind.MP3, track.kind)
        assertEquals(1000.0, track.durationMs.toDouble(), 150.0)
        assertEquals(44100, track.sampleRateHz)
        assertEquals(2, track.channels)
        assertEquals("Test Tone", track.title)
        assertEquals("Tester", track.artist)
        assertTrue("bitrate ${track.bitrateKbps}", track.bitrateKbps in 48..80)
        val pcm = decodeAll(f)
        assertEquals(44100.0 * 4, pcm.size.toDouble(), 44100.0 * 4 * 0.12)
        assertTrue("tone is audible, peak ${peak(pcm)}", peak(pcm) > 1500)
    }

    @Test fun oggVorbisDecodesAndCarriesItsTags() {
        val f = TestAudio.resource("tone.ogg", tmp.root)
        val track = TrackReader.read(TrackReader.quick(f))
        assertEquals(1000.0, track.durationMs.toDouble(), 150.0)
        assertEquals(44100, track.sampleRateHz)
        assertEquals("Test Tone", track.title)
        assertTrue(peak(decodeAll(f)) > 1500)
    }

    @Test fun flacDecodes() {
        val f = TestAudio.resource("tone.flac", tmp.root)
        val track = TrackReader.read(TrackReader.quick(f))
        assertEquals(1000.0, track.durationMs.toDouble(), 150.0)
        assertEquals(22050, track.sampleRateHz)
        assertEquals(1, track.channels)
        val pcm = decodeAll(f)
        assertEquals(22050.0 * 2, pcm.size.toDouble(), 22050.0 * 2 * 0.12)
        assertTrue(peak(pcm) > 1500)
    }

    @Test fun everyDecodedFormatPlaysThroughThePlayer() {
        for (name in listOf("tone.mp3", "tone.ogg", "tone.flac")) {
            val sinks = FakeSinks(speedup = 20)
            val player = Player(sinks)
            try {
                player.addTracks(listOf(TrackReader.quick(TestAudio.resource(name, tmp.newFolder()))))
                player.play()
                waitFor(what = "$name to finish") { sinks.opened.isNotEmpty() && player.status == Status.STOPPED }
                assertTrue("$name wrote audio: ${sinks.last.frames}", sinks.last.frames > 15000)
                assertTrue("$name is audible: ${sinks.last.rms(0)}", sinks.last.rms(0) > 0.02)
            } finally {
                player.close()
            }
        }
    }

    private fun midiFile(): File {
        val seq = Sequence(Sequence.PPQ, 480)
        val track = seq.createTrack()
        var tick = 0L
        for (note in listOf(60, 64, 67, 72)) {
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_ON, 0, note, 90), tick))
            tick += 480
            track.add(MidiEvent(ShortMessage(ShortMessage.NOTE_OFF, 0, note, 0), tick))
        }
        val f = File(tmp.root, "tune.mid")
        MidiSystem.write(seq, 1, f)
        return f
    }

    @Test fun midiFilesReportTheirLength() {
        val track = TrackReader.read(TrackReader.quick(midiFile()))
        assertEquals(TrackKind.MIDI, track.kind)
        // 4 quarter notes at the default 120 bpm
        assertEquals(2000.0, track.durationMs.toDouble(), 100.0)
    }

    @Test fun midiPlaybackStartsOrFailsCleanlyWhenThereIsNoSoundDevice() {
        val player = Player(FakeSinks())
        try {
            player.addTracks(listOf(TrackReader.quick(midiFile())))
            player.play()
            waitFor(8000, "MIDI to start or report an error") {
                player.positionMs() > 100 || player.lastError != null || player.status == Status.STOPPED
            }
            if (player.lastError != null) {
                assertTrue(player.lastError!!.startsWith("Cannot play"))
                assertEquals(Status.STOPPED, player.status)
            } else {
                assertEquals(Status.PLAYING, player.status)
                player.pause()
                assertEquals(Status.PAUSED, player.status)
                player.stop()
            }
        } finally {
            player.close()
        }
    }
}
