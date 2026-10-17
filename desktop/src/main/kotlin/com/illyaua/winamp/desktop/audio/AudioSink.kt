package com.illyaua.winamp.desktop.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/** Where decoded PCM goes. The real one is a sound card line; tests use a recording fake. */
interface AudioSink : AutoCloseable {
    fun write(buf: ByteArray, off: Int, len: Int): Int

    /** Frames the device has actually played since this sink was opened (not just queued). */
    fun framePosition(): Long

    fun pause()
    fun resume()

    /** Drops audio that is queued but not yet played, used when seeking. */
    fun flush()

    /** Blocks until everything queued has played. */
    fun drain()
}

fun interface SinkFactory {
    fun open(format: AudioFormat): AudioSink
}

class LineSink(format: AudioFormat) : AudioSink {
    private val line: SourceDataLine = AudioSystem.getSourceDataLine(format)

    init {
        // about 120 ms of buffer keeps the analyzer and seeking responsive
        val bytes = (format.sampleRate / 8f).toInt() * format.frameSize
        line.open(format, bytes)
        line.start()
    }

    override fun write(buf: ByteArray, off: Int, len: Int) = line.write(buf, off, len)
    override fun framePosition() = line.longFramePosition
    override fun pause() = line.stop()
    override fun resume() = line.start()
    override fun flush() = line.flush()
    override fun drain() = line.drain()

    override fun close() {
        try {
            line.stop()
            line.close()
        } catch (_: Exception) {
        }
    }
}
