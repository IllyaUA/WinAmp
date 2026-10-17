package com.illyaua.winamp.desktop.audio

import java.io.File
import javax.sound.midi.MetaEventListener
import javax.sound.midi.MetaMessage
import javax.sound.midi.MidiMessage
import javax.sound.midi.MidiSystem
import javax.sound.midi.Receiver
import javax.sound.midi.Sequencer
import javax.sound.midi.ShortMessage
import javax.sound.midi.Synthesizer
import javax.sound.midi.SysexMessage

/**
 * Plays one MIDI file through Java's built-in software synthesizer, optionally with a user SoundFont.
 * The analyzer for MIDI is driven by note-on events because the synthesizer does not expose its audio.
 */
class MidiPlayback(private val soundFont: File?, private val onEnd: () -> Unit) {
    private var synth: Synthesizer? = null
    private var sequencer: Sequencer? = null
    private var receiver: Receiver? = null

    @Volatile private var closed = false
    @Volatile var lengthMs: Long = 0
        private set

    /** Energy per analyzer bar, raised by note-ons and read (and decayed) by the UI. */
    val energy = FloatArray(BARS)

    fun start(file: File, startMs: Long, volume: Float) {
        val sequence = MidiSystem.getSequence(file)
        lengthMs = sequence.microsecondLength / 1000
        val s = MidiSystem.getSynthesizer()
        s.open()
        if (soundFont != null) {
            try {
                val bank = MidiSystem.getSoundbank(soundFont)
                if (s.isSoundbankSupported(bank)) {
                    s.unloadAllInstruments(s.defaultSoundbank)
                    s.loadAllInstruments(bank)
                }
            } catch (_: Exception) {
                // a bad SoundFont leaves the default sounds in place
            }
        }
        synth = s
        val tap = TapReceiver(s.receiver)
        receiver = tap
        val q = MidiSystem.getSequencer(false)
        q.open()
        q.transmitter.receiver = tap
        q.sequence = sequence
        q.addMetaEventListener(MetaEventListener { meta: MetaMessage ->
            if (meta.type == END_OF_TRACK && !closed) onEnd()
        })
        sequencer = q
        setVolume(volume)
        if (startMs > 0) q.microsecondPosition = startMs * 1000
        q.start()
    }

    fun pause() {
        sequencer?.stop()
        allNotesOff()
    }

    fun resume() {
        sequencer?.start()
    }

    fun seek(ms: Long) {
        val q = sequencer ?: return
        allNotesOff()
        q.microsecondPosition = ms.coerceIn(0, lengthMs) * 1000
    }

    fun positionMs(): Long = sequencer?.microsecondPosition?.div(1000) ?: 0

    fun isRunning(): Boolean = sequencer?.isRunning == true

    /** General MIDI master volume, which the built-in synthesizer honours. */
    fun setVolume(volume: Float) {
        val r = receiver ?: return
        val value = (volume.coerceIn(0f, 1f) * 16383).toInt()
        try {
            val msg = SysexMessage()
            val data = byteArrayOf(
                0xF0.toByte(), 0x7F, 0x7F, 0x04, 0x01, (value and 0x7F).toByte(), ((value shr 7) and 0x7F).toByte(), 0xF7.toByte(),
            )
            msg.setMessage(data, data.size)
            r.send(msg, -1)
        } catch (_: Exception) {
        }
    }

    private fun allNotesOff() {
        val r = receiver ?: return
        try {
            for (ch in 0 until 16) r.send(ShortMessage(ShortMessage.CONTROL_CHANGE, ch, 123, 0), -1)
        } catch (_: Exception) {
        }
    }

    fun close() {
        closed = true
        try {
            sequencer?.stop()
            sequencer?.close()
        } catch (_: Exception) {
        }
        try {
            synth?.close()
        } catch (_: Exception) {
        }
        sequencer = null
        synth = null
        receiver = null
    }

    private inner class TapReceiver(private val target: Receiver) : Receiver {
        override fun send(message: MidiMessage, timeStamp: Long) {
            if (message is ShortMessage && message.command == ShortMessage.NOTE_ON && message.data2 > 0) {
                val bar = ((message.data1 - LOW_NOTE) * BARS / (HIGH_NOTE - LOW_NOTE)).coerceIn(0, BARS - 1)
                val level = (message.data2 / 127f).coerceIn(0.25f, 1f)
                synchronized(energy) { if (level > energy[bar]) energy[bar] = level }
            }
            target.send(message, timeStamp)
        }

        override fun close() = target.close()
    }

    companion object {
        const val BARS = 19
        private const val END_OF_TRACK = 0x2F
        private const val LOW_NOTE = 28
        private const val HIGH_NOTE = 100
    }
}
