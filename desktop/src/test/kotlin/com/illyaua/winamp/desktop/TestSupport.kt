package com.illyaua.winamp.desktop

import com.illyaua.winamp.desktop.audio.AudioSink
import com.illyaua.winamp.desktop.audio.SinkFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** A sound card stand-in that records what it is given and plays at [speedup] times real time. */
class FakeSink(val format: AudioFormat, private val speedup: Int) : AudioSink {
    private val data = ByteArrayOutputStream()
    @Volatile var frames = 0L
    @Volatile var closed = false
    @Volatile var paused = false

    override fun write(buf: ByteArray, off: Int, len: Int): Int {
        synchronized(this) {
            data.write(buf, off, len)
            frames += len / format.frameSize
        }
        val ms = (len / format.frameSize) * 1000L / format.sampleRate.toLong() / speedup
        if (ms > 0) Thread.sleep(ms)
        return len
    }

    override fun framePosition() = frames
    override fun pause() { paused = true }
    override fun resume() { paused = false }
    override fun flush() {}
    override fun drain() {}
    override fun close() { closed = true }

    @Synchronized fun pcm(): ByteArray = data.toByteArray()

    /** RMS (0..1) of one channel of the recorded 16-bit PCM. */
    fun rms(channel: Int): Double {
        val b = pcm()
        val ch = format.channels
        var sum = 0.0
        var n = 0
        var i = channel * 2
        while (i + 1 < b.size) {
            val s = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort() / 32768.0
            sum += s * s
            n++
            i += ch * 2
        }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }
}

class FakeSinks(private val speedup: Int = 10) : SinkFactory {
    val opened = CopyOnWriteArrayList<FakeSink>()
    override fun open(format: AudioFormat) = FakeSink(format, speedup).also { opened += it }
    val last get() = opened.last()
}

fun waitFor(timeoutMs: Long = 5000, what: String = "condition", cond: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < end) {
        if (cond()) return
        Thread.sleep(10)
    }
    throw AssertionError("Timed out waiting for $what")
}

object TestAudio {
    fun wav(file: File, seconds: Double, freq: Double = 440.0, rate: Int = 44100, channels: Int = 2, amplitude: Double = 0.5): File {
        val frames = (seconds * rate).toInt()
        val bytes = ByteArray(frames * channels * 2)
        var i = 0
        for (f in 0 until frames) {
            val s = (amplitude * 32767 * sin(2 * PI * freq * f / rate)).toInt()
            repeat(channels) {
                bytes[i++] = s.toByte()
                bytes[i++] = (s shr 8).toByte()
            }
        }
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        AudioSystem.write(AudioInputStream(bytes.inputStream(), format, frames.toLong()), AudioFileFormat.Type.WAVE, file)
        return file
    }

    fun resource(name: String, into: File): File {
        val out = File(into, name)
        TestAudio::class.java.getResourceAsStream("/$name")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }
}
