package com.illyaua.winamp.desktop

import com.illyaua.winamp.desktop.audio.Player
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.UnsupportedAudioFileException
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {
    @Test fun cryptoJavaSoundErrorsGetPlainWording() {
        assertEquals("no audio output device is available", Player.friendlyMessage(IllegalArgumentException("No line matching interface SourceDataLine supporting format PCM_SIGNED 44100.0 Hz")))
        assertEquals("no audio output device is available", Player.friendlyMessage(LineUnavailableException("busy")))
        assertEquals("unsupported or damaged file", Player.friendlyMessage(UnsupportedAudioFileException("x")))
        assertEquals("boom", Player.friendlyMessage(RuntimeException("boom")))
    }
}
