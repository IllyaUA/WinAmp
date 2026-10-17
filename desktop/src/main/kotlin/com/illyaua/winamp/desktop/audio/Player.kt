package com.illyaua.winamp.desktop.audio

import com.illyaua.winamp.core.Equalizer10
import com.illyaua.winamp.core.Fft
import com.illyaua.winamp.core.PlaybackOrder
import com.illyaua.winamp.core.SpectrumBars
import com.illyaua.winamp.core.SpectrumFrame
import com.illyaua.winamp.core.SpectrumSmoother
import com.illyaua.winamp.core.Track
import com.illyaua.winamp.core.TrackKind
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioInputStream
import kotlin.math.min

enum class Status { STOPPED, PLAYING, PAUSED }

enum class Event { PLAYLIST, TRACK, STATUS, SETTINGS, ERROR }

/**
 * The player: playlist, transport, volume, equalizer and analyzer.
 * MP3, WAV, Ogg and FLAC are decoded to PCM and pushed to an [AudioSink]; MIDI goes through the Java synthesizer.
 * Events reach listeners through [dispatch] (the UI passes the Swing event thread).
 */
class Player(
    private val sinks: SinkFactory = SinkFactory { LineSink(it) },
    private val dispatch: (Runnable) -> Unit = { it.run() },
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(Event) -> Unit>()
    private val pending = Array(Event.values().size) { AtomicBoolean(false) }
    private val control = Executors.newSingleThreadExecutor { r -> Thread(r, "player-control").apply { isDaemon = true } }
    private val metadata = Executors.newSingleThreadExecutor { r -> Thread(r, "player-metadata").apply { isDaemon = true } }
    private val order = PlaybackOrder()
    private val smoother = SpectrumSmoother(BARS)
    private val failureStreak = AtomicInteger()

    @Volatile var tracks: List<Track> = emptyList(); private set
    @Volatile var currentIndex: Int = -1; private set
    @Volatile var status: Status = Status.STOPPED; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var volume: Float = 0.8f; private set
    @Volatile var balance: Float = 0f; private set
    @Volatile var soundFont: File? = null

    val shuffle: Boolean get() = order.shuffle
    val repeat: Boolean get() = order.repeat
    val currentTrack: Track? get() = tracks.getOrNull(currentIndex)

    @Volatile var eqEnabled: Boolean = true; private set
    @Volatile var eqPreampDb: Float = 0f; private set
    private val eqDb = FloatArray(Equalizer10.BANDS)
    private val eqVersion = AtomicInteger()

    @Volatile private var job: Job? = null
    @Volatile private var pcmSpectrum: SpectrumFrame = SpectrumFrame.empty(BARS)

    fun addListener(l: (Event) -> Unit) {
        listeners += l
    }

    private fun fire(e: Event) {
        if (pending[e.ordinal].compareAndSet(false, true)) {
            dispatch(Runnable {
                pending[e.ordinal].set(false)
                for (l in listeners) l(e)
            })
        }
    }

    // ---- playlist -------------------------------------------------------------------------------------------

    fun addTracks(list: List<Track>, playFirst: Boolean = false) {
        if (list.isEmpty()) return
        synchronized(lock) {
            val firstNew = tracks.size
            tracks = tracks + list
            fire(Event.PLAYLIST)
            if (playFirst) playIndex(firstNew)
        }
        enrich(list)
    }

    /** Adds files, and every playable file inside any folders, in the order given. */
    fun addFiles(files: List<File>, playFirst: Boolean = false) {
        metadata.execute {
            val found = files.flatMap {
                if (it.isDirectory) com.illyaua.winamp.core.DirectoryLister.scanAudio(it) else listOf(it)
            }.map { TrackReader.quick(it) }
            if (found.isEmpty()) {
                lastError = "No audio files found"
                fire(Event.ERROR)
            } else {
                addTracks(found, playFirst)
            }
        }
    }

    private fun enrich(list: List<Track>) {
        metadata.execute {
            for (old in list) {
                val full = TrackReader.read(old)
                if (full == old) continue
                synchronized(lock) { tracks = tracks.map { if (it === old) full else it } }
                fire(Event.PLAYLIST)
            }
        }
    }

    fun removeAt(indices: Set<Int>) {
        if (indices.isEmpty()) return
        synchronized(lock) {
            val removedCurrent = currentIndex in indices
            if (removedCurrent) stop()
            val shift = indices.count { it < currentIndex }
            val remaining = tracks.filterIndexed { i, _ -> i !in indices }
            currentIndex = when {
                remaining.isEmpty() || currentIndex < 0 -> -1
                removedCurrent -> (currentIndex - shift).coerceIn(0, remaining.lastIndex)
                else -> currentIndex - shift
            }
            tracks = remaining
            order.reset()
            fire(Event.PLAYLIST)
            fire(Event.TRACK)
        }
    }

    fun clear() {
        synchronized(lock) {
            stop()
            tracks = emptyList()
            currentIndex = -1
            order.reset()
            fire(Event.PLAYLIST)
            fire(Event.TRACK)
        }
    }

    /** Reorders the playlist, keeping the playing track playing. */
    fun reorder(transform: (List<Track>) -> List<Track>) {
        synchronized(lock) {
            val current = currentTrack
            tracks = transform(tracks)
            currentIndex = if (current == null) -1 else tracks.indexOfFirst { it === current }
            order.reset()
            fire(Event.PLAYLIST)
        }
    }

    // ---- transport ------------------------------------------------------------------------------------------

    fun playIndex(index: Int) {
        synchronized(lock) {
            if (index !in tracks.indices) return
            if (index != currentIndex) order.leave(currentIndex)
            failureStreak.set(0)
            start(index, 0)
        }
    }

    fun play() {
        synchronized(lock) {
            when (status) {
                Status.PLAYING -> seek(0)
                Status.PAUSED -> resume()
                Status.STOPPED -> {
                    if (tracks.isEmpty()) return
                    failureStreak.set(0)
                    start(if (currentIndex in tracks.indices) currentIndex else 0, 0)
                }
            }
        }
    }

    fun pause() {
        synchronized(lock) {
            if (status != Status.PLAYING) return
            job?.pause()
            status = Status.PAUSED
            fire(Event.STATUS)
        }
    }

    fun resume() {
        synchronized(lock) {
            if (status != Status.PAUSED) return
            job?.resume()
            status = Status.PLAYING
            fire(Event.STATUS)
        }
    }

    fun togglePause() {
        synchronized(lock) {
            when (status) {
                Status.PLAYING -> pause()
                Status.PAUSED -> resume()
                Status.STOPPED -> play()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            stopJob()
            val changed = status != Status.STOPPED
            status = Status.STOPPED
            if (changed) fire(Event.STATUS)
        }
    }

    fun next() {
        synchronized(lock) {
            val target = order.next(currentIndex, tracks.size) ?: return
            failureStreak.set(0)
            start(target, 0)
        }
    }

    fun previous() {
        synchronized(lock) {
            if (tracks.isEmpty()) return
            if (status != Status.STOPPED && positionMs() > RESTART_THRESHOLD_MS) {
                seek(0)
                return
            }
            val target = order.previous(currentIndex, tracks.size)
            if (target != null) {
                failureStreak.set(0)
                start(target, 0)
            } else if (status != Status.STOPPED) {
                seek(0)
            }
        }
    }

    fun seek(ms: Long) {
        synchronized(lock) {
            if (status == Status.STOPPED) return
            val duration = currentTrack?.durationMs ?: 0
            job?.seek(if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0))
        }
    }

    fun positionMs(): Long = if (status == Status.STOPPED) 0 else job?.positionMs() ?: 0

    private fun start(index: Int, startMs: Long) {
        stopJob()
        val track = tracks[index]
        currentIndex = index
        lastError = null
        status = Status.PLAYING
        val j = if (track.kind == TrackKind.MIDI) MidiJob(track, startMs) else PcmJob(track, startMs)
        job = j
        j.begin()
        fire(Event.TRACK)
        fire(Event.STATUS)
    }

    private fun stopJob() {
        val j = job ?: return
        job = null
        j.stop()
    }

    private fun onTrackEnded(ended: Job) {
        synchronized(lock) {
            if (ended !== job) return
            val target = order.next(currentIndex, tracks.size)
            if (target != null) {
                start(target, 0)
            } else {
                stop()
            }
        }
    }

    /** The current track could not be played. Move on, but give up once every track has failed in a row. */
    private fun onJobFailed(failed: Job, message: String) {
        synchronized(lock) {
            if (failed !== job) return
            lastError = "Cannot play ${currentTrack?.displayName ?: "track"}: $message"
            fire(Event.ERROR)
            if (failureStreak.incrementAndGet() < tracks.size) {
                val target = order.next(currentIndex, tracks.size)
                if (target != null) {
                    start(target, 0)
                    return
                }
            }
            stop()
        }
    }

    // ---- settings -------------------------------------------------------------------------------------------

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        job?.volumeChanged()
        fire(Event.SETTINGS)
    }

    fun setBalance(b: Float) {
        balance = if (kotlin.math.abs(b) < 0.05f) 0f else b.coerceIn(-1f, 1f)
        fire(Event.SETTINGS)
    }

    fun setShuffle(on: Boolean) {
        order.shuffle = on
        fire(Event.SETTINGS)
    }

    fun setRepeat(on: Boolean) {
        order.repeat = on
        fire(Event.SETTINGS)
    }

    fun setEqEnabled(on: Boolean) {
        eqEnabled = on
        eqVersion.incrementAndGet()
        fire(Event.SETTINGS)
    }

    fun setEqBand(i: Int, db: Float) {
        eqDb[i] = db.coerceIn(-12f, 12f)
        eqVersion.incrementAndGet()
        fire(Event.SETTINGS)
    }

    fun eqBand(i: Int) = eqDb[i]

    fun setEqPreamp(db: Float) {
        eqPreampDb = db.coerceIn(-12f, 12f)
        eqVersion.incrementAndGet()
        fire(Event.SETTINGS)
    }

    fun resetEq() {
        eqDb.fill(0f)
        eqPreampDb = 0f
        eqVersion.incrementAndGet()
        fire(Event.SETTINGS)
    }

    fun loadEqPreset(bands: FloatArray, preamp: Float) {
        for (i in 0 until min(bands.size, Equalizer10.BANDS)) eqDb[i] = bands[i].coerceIn(-12f, 12f)
        eqPreampDb = preamp.coerceIn(-12f, 12f)
        eqVersion.incrementAndGet()
        fire(Event.SETTINGS)
    }

    // ---- analyzer -------------------------------------------------------------------------------------------

    /** Call from a UI timer. Returns the bars to draw now; they fall away when nothing is playing. */
    fun pollSpectrum(): SpectrumFrame {
        val j = job
        if (status != Status.PLAYING) return smoother.push(FloatArray(BARS))
        if (j is MidiJob) {
            val raw = FloatArray(BARS)
            val energy = j.midi.energy
            synchronized(energy) {
                for (i in 0 until BARS) {
                    raw[i] = energy[i]
                    energy[i] *= 0.82f
                }
            }
            return smoother.push(raw)
        }
        return pcmSpectrum
    }

    fun close() {
        stop()
        control.shutdownNow()
        metadata.shutdownNow()
    }

    // ---- jobs -----------------------------------------------------------------------------------------------

    private abstract inner class Job {
        abstract fun begin()
        abstract fun pause()
        abstract fun resume()
        abstract fun seek(ms: Long)
        abstract fun stop()
        abstract fun positionMs(): Long
        open fun volumeChanged() {}
    }

    private inner class PcmJob(private val track: Track, private val startMs: Long) : Job() {
        @Volatile private var cancelled = false
        @Volatile private var paused = false
        @Volatile private var seekMs = -1L
        @Volatile private var sink: AudioSink? = null
        @Volatile private var offsetFrames = 0L
        @Volatile private var baseFrames = 0L
        @Volatile private var sampleRate = 44100f
        private val thread = Thread({ run() }, "player-pcm").apply { isDaemon = true }

        override fun begin() = thread.start()
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun seek(ms: Long) { seekMs = ms }

        override fun stop() {
            cancelled = true
            paused = false
            sink?.close() // unblocks a write that is waiting on a paused or full line
            thread.interrupt()
            if (Thread.currentThread() !== thread) thread.join(1500)
        }

        override fun positionMs(): Long {
            val s = sink ?: return 0
            val frames = offsetFrames + (s.framePosition() - baseFrames)
            return (frames.coerceAtLeast(0) * 1000L / sampleRate).toLong()
        }

        private fun skipFrames(stream: AudioInputStream, frames: Long, frameSize: Int) {
            var remaining = frames * frameSize
            val scratch = ByteArray(8192)
            while (remaining > 0 && !cancelled) {
                var skipped = stream.skip(remaining)
                if (skipped <= 0) {
                    skipped = stream.read(scratch, 0, min(scratch.size.toLong(), remaining).toInt()).toLong()
                    if (skipped <= 0) return
                }
                remaining -= skipped
            }
        }

        private fun run() {
            val file = track.file()
            var stream: AudioInputStream? = null
            var out: AudioSink? = null
            var ended = false
            try {
                var pcm = TrackReader.openPcm(file)
                stream = pcm
                val fmt = pcm.format
                val channels = fmt.channels
                val frameSize = fmt.frameSize
                sampleRate = fmt.sampleRate
                out = sinks.open(fmt)
                sink = out
                if (cancelled) return

                val eq = Equalizer10(fmt.sampleRate, channels)
                var eqSeen = -1
                val fft = Fft(CHUNK_FRAMES)
                val mono = FloatArray(CHUNK_FRAMES)
                val bytes = ByteArray(CHUNK_FRAMES * frameSize)
                val floats = FloatArray(CHUNK_FRAMES * channels)

                if (startMs > 0) {
                    offsetFrames = (startMs * sampleRate / 1000).toLong()
                    skipFrames(pcm, offsetFrames, frameSize)
                }
                baseFrames = out.framePosition()
                failureStreak.set(0)

                while (!cancelled) {
                    val seekTo = seekMs
                    if (seekTo >= 0) {
                        seekMs = -1
                        pcm.close()
                        pcm = TrackReader.openPcm(file)
                        stream = pcm
                        offsetFrames = (seekTo * sampleRate / 1000).toLong()
                        skipFrames(pcm, offsetFrames, frameSize)
                        out.flush()
                        baseFrames = out.framePosition()
                    }
                    if (paused) {
                        out.pause()
                        while (paused && !cancelled && seekMs < 0) Thread.sleep(15)
                        if (cancelled) break
                        if (!paused) out.resume()
                        continue
                    }

                    val n = pcm.read(bytes, 0, bytes.size)
                    if (n < 0) {
                        ended = true
                        break
                    }
                    val frames = n / frameSize
                    if (frames == 0) continue

                    var bi = 0
                    for (i in 0 until frames * channels) {
                        floats[i] = ((bytes[bi + 1].toInt() shl 8) or (bytes[bi].toInt() and 0xFF)).toShort() / 32768f
                        bi += 2
                    }

                    val version = eqVersion.get()
                    if (version != eqSeen) {
                        eqSeen = version
                        eq.enabled = eqEnabled
                        for (b in 0 until Equalizer10.BANDS) eq.setBand(b, eqDb[b])
                        eq.setPreamp(eqPreampDb)
                    }
                    eq.process(floats, frames)

                    java.util.Arrays.fill(mono, 0f)
                    for (f in 0 until min(frames, CHUNK_FRAMES)) {
                        var sum = 0f
                        for (c in 0 until channels) sum += floats[f * channels + c]
                        mono[f] = sum / channels
                    }
                    pcmSpectrum = smoother.push(SpectrumBars.fromMagnitudes(fft.magnitudes(mono), BARS, sampleRate))

                    val v = volume
                    val gain = v * v
                    val left = gain * min(1f, 1f - balance)
                    val right = gain * min(1f, 1f + balance)
                    bi = 0
                    for (f in 0 until frames) {
                        for (c in 0 until channels) {
                            val g = if (channels >= 2 && c == 0) left else if (channels >= 2 && c == 1) right else gain
                            val s = (floats[f * channels + c] * g * 32767f).toInt().coerceIn(-32768, 32767)
                            bytes[bi] = s.toByte()
                            bytes[bi + 1] = (s shr 8).toByte()
                            bi += 2
                        }
                    }
                    out.write(bytes, 0, frames * frameSize)
                }

                if (ended && !cancelled) {
                    out.drain()
                    if (!cancelled) control.execute { onTrackEnded(this) }
                }
            } catch (_: InterruptedException) {
                // stopped
            } catch (e: Exception) {
                if (!cancelled) control.execute { onJobFailed(this, friendlyMessage(e)) }
            } finally {
                out?.close()
                try {
                    stream?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private inner class MidiJob(private val track: Track, private val startMs: Long) : Job() {
        val midi = MidiPlayback(soundFont) { control.execute { onTrackEnded(this) } }
        @Volatile private var cancelled = false

        override fun begin() {
            // opening the synthesizer can take a moment, so keep it off the caller's thread
            control.execute {
                try {
                    synchronized(this) {
                        if (cancelled) return@execute
                        midi.start(track.file(), startMs, volume)
                    }
                    failureStreak.set(0)
                } catch (e: Exception) {
                    midi.close()
                    if (!cancelled) onJobFailed(this, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        override fun pause() = midi.pause()
        override fun resume() = midi.resume()
        override fun seek(ms: Long) = midi.seek(ms)
        override fun positionMs() = midi.positionMs()
        override fun volumeChanged() = midi.setVolume(volume)

        override fun stop() {
            cancelled = true
            synchronized(this) { midi.close() }
        }
    }

    companion object {
        /** Java Sound's own messages are cryptic; name the common causes. */
        fun friendlyMessage(e: Exception): String = when {
            e is javax.sound.sampled.UnsupportedAudioFileException -> "unsupported or damaged file"
            e is javax.sound.sampled.LineUnavailableException || e.message?.startsWith("No line matching") == true ->
                "no audio output device is available"
            else -> e.message ?: e.javaClass.simpleName
        }

        const val BARS = 19
        private const val CHUNK_FRAMES = 1024
        private const val RESTART_THRESHOLD_MS = 3000
    }
}
