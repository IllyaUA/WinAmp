package com.illyaua.winamp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {
    @Test fun kindFromName() {
        assertEquals(TrackKind.MP3, TrackKind.fromName("Song.MP3"))
        assertEquals(TrackKind.WAV, TrackKind.fromName("a.b.wav"))
        assertEquals(TrackKind.MIDI, TrackKind.fromName("canyon.mid"))
        assertEquals(TrackKind.MIDI, TrackKind.fromName("canyon.MIDI"))
        assertEquals(TrackKind.OTHER, TrackKind.fromName("noextension"))
    }

    @Test fun supportedFiles() {
        assertTrue(TrackKind.isSupported("x.mp3"))
        assertTrue(TrackKind.isSupported("x.mid"))
        assertTrue(TrackKind.isSupported("x.flac"))
        assertFalse(TrackKind.isSupported("cover.jpg"))
        assertFalse(TrackKind.isSupported("mp3"))
    }

    @Test fun displayName() {
        assertEquals("Song", Track("u", "Song").displayName)
        assertEquals("Band - Song", Track("u", "Song", artist = "Band").displayName)
        assertEquals("Song", Track("u", "Song", artist = " ").displayName)
    }
}
