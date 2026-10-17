package com.illyaua.winamp.desktop.audio

import com.illyaua.winamp.core.Track
import com.illyaua.winamp.core.TrackKind
import java.io.File
import java.net.URI
import javax.sound.midi.MidiSystem
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

fun Track.file(): File = try {
    File(URI(uri))
} catch (_: Exception) {
    File(uri)
}

object TrackReader {
    fun quick(file: File): Track = Track(
        uri = file.toURI().toString(),
        title = file.nameWithoutExtension,
        kind = TrackKind.fromName(file.name),
    )

    /** Reads tags, duration and format. Slow: call off the UI thread. Anything unreadable keeps the values in [base]. */
    fun read(base: Track): Track {
        val file = base.file()
        return try {
            if (base.kind == TrackKind.MIDI) readMidi(base, file) else readAudio(base, file)
        } catch (_: Exception) {
            base
        }
    }

    private fun readMidi(base: Track, file: File): Track {
        val sequence = MidiSystem.getSequence(file)
        return base.copy(durationMs = sequence.microsecondLength / 1000)
    }

    private fun readAudio(base: Track, file: File): Track {
        val fileFormat = AudioSystem.getAudioFileFormat(file)
        val props = fileFormat.properties()
        val format = fileFormat.format

        fun text(key: String) = (props[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        fun number(key: String) = (props[key] as? Number)?.toLong()

        var durationMs = number("duration")?.let { it / 1000 } ?: 0L
        if (durationMs <= 0 && fileFormat.frameLength > 0 && format.frameRate > 0) {
            durationMs = (fileFormat.frameLength * 1000L / format.frameRate).toLong()
        }
        var bitrate = ((number("mp3.bitrate.nominal.bps") ?: number("ogg.bitrate.nominal.bps") ?: number("bitrate")) ?: 0L).toInt() / 1000
        if (bitrate <= 0 && durationMs > 0) bitrate = (file.length() * 8 / durationMs).toInt()
        val sampleRate = format.sampleRate.takeIf { it > 0 }?.toInt() ?: (number("mp3.frequency.hz")?.toInt() ?: 0)

        return base.copy(
            title = text("title") ?: base.title,
            artist = text("author") ?: text("artist") ?: base.artist,
            durationMs = if (durationMs > 0) durationMs else base.durationMs,
            bitrateKbps = if (bitrate > 0) bitrate else base.bitrateKbps,
            sampleRateHz = if (sampleRate > 0) sampleRate else base.sampleRateHz,
            channels = format.channels.takeIf { it > 0 } ?: (number("mp3.channels")?.toInt() ?: base.channels),
        )
    }

    /** Opens [file] as 16-bit little-endian PCM, converting from MP3, Ogg, FLAC or any other format Java Sound can decode. */
    fun openPcm(file: File): AudioInputStream {
        val source = AudioSystem.getAudioInputStream(file)
        val f = source.format
        if (f.encoding == AudioFormat.Encoding.PCM_SIGNED && f.sampleSizeInBits == 16 && !f.isBigEndian) return source
        val target = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED, f.sampleRate, 16, f.channels, f.channels * 2, f.sampleRate, false,
        )
        return AudioSystem.getAudioInputStream(target, source)
    }
}
