package com.illyaua.winamp.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.illyaua.winamp.core.PlaybackOrder
import com.illyaua.winamp.core.SpectrumFrame
import com.illyaua.winamp.core.SpectrumMath
import com.illyaua.winamp.core.SpectrumSmoother
import com.illyaua.winamp.core.Track
import com.illyaua.winamp.core.TrackKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

const val SPECTRUM_BANDS = 19

/**
 * Plays the playlist with one MediaPlayer, which handles MP3, WAV and MIDI.
 *
 * A single instance lives for the whole process so the UI and the foreground service share state.
 * Everything here runs on the main thread, except the Visualizer callback which only touches the spectrum flow.
 */
class PlayerEngine private constructor(private val app: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = PlayerStore(app)
    private val order = PlaybackOrder()
    private val smoother = SpectrumSmoother(SPECTRUM_BANDS)
    private val audioManager = app.getSystemService(AudioManager::class.java)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _spectrum = MutableStateFlow(SpectrumFrame.empty(SPECTRUM_BANDS))
    val spectrum: StateFlow<SpectrumFrame> = _spectrum.asStateFlow()

    private val player = MediaPlayer()
    private var prepared = false
    private var pendingSeekMs = -1
    private var failureStreak = 0
    private var tickerJob: Job? = null
    private var persistJob: Job? = null

    private var equalizer: Equalizer? = null
    private var presetIndex = -1
    private var visualizer: Visualizer? = null

    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var ducked = false

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        player.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
        player.setOnPreparedListener { onPrepared() }
        player.setOnCompletionListener { onCompleted() }
        player.setOnErrorListener { _, _, _ ->
            onTrackFailed()
            true
        }

        val saved = store.load()
        order.shuffle = saved.shuffle
        order.repeat = saved.repeat
        _state.value = PlayerState(
            tracks = saved.tracks,
            currentIndex = saved.index.takeIf { it in saved.tracks.indices } ?: -1,
            volume = saved.volume,
            balance = saved.balance,
            shuffle = saved.shuffle,
            repeat = saved.repeat,
        )
        initEqualizer(saved.eqEnabled, saved.eqLevels)
    }

    // ---- playlist -------------------------------------------------------------------------------------------

    fun addUris(uris: List<Uri>, playFirst: Boolean = false) {
        if (uris.isEmpty()) return
        scope.launch {
            val quick = withContext(Dispatchers.IO) {
                uris.map { TrackLibrary.quickTrack(it, TrackLibrary.displayName(app, it)) }
            }
            appendTracks(quick, playFirst)
        }
    }

    fun addFolder(treeUri: Uri) {
        scope.launch {
            val found = withContext(Dispatchers.IO) { TrackLibrary.scanFolder(app, treeUri) }
            if (found.isEmpty()) {
                _state.update { it.copy(error = "No playable files in that folder") }
            } else {
                appendTracks(found, playFirst = false)
            }
        }
    }

    private fun appendTracks(tracks: List<Track>, playFirst: Boolean) {
        val firstNew = _state.value.tracks.size
        _state.update { it.copy(tracks = it.tracks + tracks) }
        schedulePersist()
        if (playFirst) playIndex(firstNew)
        enrich(tracks)
    }

    /** Reads tags in the background and swaps the full entries into the playlist as they arrive. */
    private fun enrich(tracks: List<Track>) {
        scope.launch {
            for (track in tracks) {
                val full = withContext(Dispatchers.IO) { TrackLibrary.readMetadata(app, track) }
                if (full == track) continue
                _state.update { s ->
                    s.copy(tracks = s.tracks.map { if (it.uri == full.uri && it.kind == full.kind) full else it })
                }
            }
            schedulePersist()
        }
    }

    fun removeAt(indices: Set<Int>) {
        if (indices.isEmpty()) return
        val s = _state.value
        val currentRemoved = s.currentIndex in indices
        if (currentRemoved) stop()
        val remaining = s.tracks.filterIndexed { i, _ -> i !in indices }
        val shift = indices.count { it < s.currentIndex }
        val newIndex = when {
            remaining.isEmpty() || s.currentIndex < 0 -> -1
            currentRemoved -> (s.currentIndex - shift).coerceIn(0, remaining.lastIndex)
            else -> s.currentIndex - shift
        }
        order.reset()
        _state.update { it.copy(tracks = remaining, currentIndex = newIndex) }
        schedulePersist()
    }

    fun clearPlaylist() {
        stop()
        order.reset()
        _state.update { it.copy(tracks = emptyList(), currentIndex = -1, durationMs = 0, error = null) }
        schedulePersist()
    }

    // ---- transport ------------------------------------------------------------------------------------------

    /** The user picked [index] directly. */
    fun playIndex(index: Int) {
        if (index !in _state.value.tracks.indices) return
        if (index != _state.value.currentIndex) order.leave(_state.value.currentIndex)
        failureStreak = 0
        load(index)
    }

    fun play() {
        val s = _state.value
        when (s.status) {
            PlayStatus.PLAYING -> if (prepared) player.seekTo(0)
            PlayStatus.PAUSED -> resume()
            PlayStatus.STOPPED -> {
                if (s.tracks.isEmpty()) return
                failureStreak = 0
                load(if (s.currentIndex in s.tracks.indices) s.currentIndex else 0)
            }
        }
    }

    /** Pauses if playing; does nothing otherwise. Used by media-session and headset events. */
    fun pause() {
        if (_state.value.status != PlayStatus.PLAYING) return
        resumeOnFocusGain = false
        if (prepared) {
            try {
                player.pause()
            } catch (_: IllegalStateException) {
            }
        }
        stopTicker()
        _state.update { it.copy(status = PlayStatus.PAUSED) }
        _spectrum.value = smoother.reset()
    }

    fun resume() {
        if (_state.value.status != PlayStatus.PAUSED) return
        if (!requestFocus()) return
        _state.update { it.copy(status = PlayStatus.PLAYING) }
        if (prepared) {
            try {
                player.start()
            } catch (_: IllegalStateException) {
            }
            startTicker()
        }
    }

    /** Play from a media key or lock screen: resumes when paused, starts when stopped, leaves a playing track alone. */
    fun playOrResume() {
        when (_state.value.status) {
            PlayStatus.PLAYING -> Unit
            PlayStatus.PAUSED -> resume()
            PlayStatus.STOPPED -> play()
        }
    }

    /** The pause button: toggles between pause and resume. */
    fun togglePause() {
        when (_state.value.status) {
            PlayStatus.PLAYING -> pause()
            PlayStatus.PAUSED -> resume()
            PlayStatus.STOPPED -> play()
        }
    }

    fun stop() {
        stopTicker()
        prepared = false
        pendingSeekMs = -1
        resumeOnFocusGain = false
        try {
            player.reset()
        } catch (_: IllegalStateException) {
        }
        abandonFocus()
        _position.value = 0
        _state.update { it.copy(status = PlayStatus.STOPPED) }
        _spectrum.value = smoother.reset()
    }

    fun next() = advance(auto = false)

    fun previous() {
        val s = _state.value
        if (s.tracks.isEmpty()) return
        if (s.status != PlayStatus.STOPPED && _position.value > RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }
        val target = order.previous(s.currentIndex, s.tracks.size)
        if (target != null) {
            failureStreak = 0
            load(target)
        } else if (s.status != PlayStatus.STOPPED) {
            seekTo(0)
        }
    }

    fun seekTo(ms: Int) {
        val s = _state.value
        if (s.status == PlayStatus.STOPPED) return
        val target = if (s.durationMs > 0) ms.coerceIn(0, s.durationMs) else ms.coerceAtLeast(0)
        if (prepared) {
            try {
                player.seekTo(target)
            } catch (_: IllegalStateException) {
                return
            }
        } else {
            pendingSeekMs = target
        }
        _position.value = target
    }

    private fun advance(auto: Boolean) {
        val s = _state.value
        val target = order.next(s.currentIndex, s.tracks.size)
        when {
            target != null -> load(target)
            auto -> stop()
        }
    }

    private fun load(index: Int) {
        val track = _state.value.tracks.getOrNull(index) ?: return
        if (!requestFocus()) {
            _state.update { it.copy(error = "Another app is holding audio focus") }
            return
        }
        stopTicker()
        prepared = false
        pendingSeekMs = -1
        _position.value = 0
        _state.update {
            it.copy(
                currentIndex = index,
                status = PlayStatus.PLAYING,
                durationMs = track.durationMs.toInt(),
                error = null,
            )
        }
        try {
            player.reset()
            player.setDataSource(app, Uri.parse(track.uri))
            player.prepareAsync()
            PlaybackService.start(app)
        } catch (e: Exception) {
            onTrackFailed()
            return
        }
        schedulePersist()
    }

    private fun onPrepared() {
        prepared = true
        failureStreak = 0
        val duration = player.duration.coerceAtLeast(0)
        applyVolume()
        _state.update { s ->
            val current = s.current
            val tracks = if (current != null && current.durationMs == 0L && duration > 0) {
                s.tracks.mapIndexed { i, t -> if (i == s.currentIndex) t.copy(durationMs = duration.toLong()) else t }
            } else {
                s.tracks
            }
            s.copy(durationMs = duration, tracks = tracks)
        }
        if (pendingSeekMs >= 0) {
            player.seekTo(pendingSeekMs)
            pendingSeekMs = -1
        }
        if (_state.value.status == PlayStatus.PLAYING) {
            player.start()
            startTicker()
        }
        val current = _state.value.current
        if (current != null && current.kind != TrackKind.MIDI && current.sampleRateHz == 0) enrich(listOf(current))
    }

    private fun onCompleted() {
        if (prepared) advance(auto = true)
    }

    /** Playback of the current track failed. Move on to the next one, but stop once every track has failed in a row. */
    private fun onTrackFailed() {
        prepared = false
        failureStreak++
        val s = _state.value
        val name = s.current?.displayName ?: "track"
        _state.update { it.copy(error = "Cannot play $name") }
        if (failureStreak < s.tracks.size) {
            val target = order.next(s.currentIndex, s.tracks.size)
            if (target != null) {
                load(target)
                return
            }
        }
        stop()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                if (prepared) {
                    try {
                        _position.value = player.currentPosition
                    } catch (_: IllegalStateException) {
                    }
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ---- volume, modes --------------------------------------------------------------------------------------

    fun setVolume(value: Float) {
        _state.update { it.copy(volume = value.coerceIn(0f, 1f)) }
        applyVolume()
        schedulePersist()
    }

    fun setBalance(value: Float) {
        // small dead zone so the slider snaps back to centre without fiddling
        val snapped = if (abs(value) < 0.06f) 0f else value.coerceIn(-1f, 1f)
        _state.update { it.copy(balance = snapped) }
        applyVolume()
        schedulePersist()
    }

    fun setShuffle(on: Boolean) {
        order.shuffle = on
        _state.update { it.copy(shuffle = on) }
        schedulePersist()
    }

    fun setRepeat(on: Boolean) {
        order.repeat = on
        _state.update { it.copy(repeat = on) }
        schedulePersist()
    }

    private fun applyVolume() {
        val s = _state.value
        // squared so the lower half of the slider is usable
        val gain = s.volume * s.volume * (if (ducked) DUCK_GAIN else 1f)
        try {
            player.setVolume(gain * min(1f, 1f - s.balance), gain * min(1f, 1f + s.balance))
        } catch (_: IllegalStateException) {
        }
    }

    // ---- equalizer ------------------------------------------------------------------------------------------

    private fun initEqualizer(enabled: Boolean, savedLevels: List<Int>) {
        try {
            val eq = Equalizer(0, player.audioSessionId)
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            val bandCount = eq.numberOfBands.toInt()
            val levels = List(bandCount) { band -> (savedLevels.getOrNull(band) ?: 0).coerceIn(minLevel, maxLevel) }
            levels.forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level.toShort()) }
            eq.setEnabled(enabled)
            val eqState = EqState(
                available = true,
                enabled = enabled,
                minLevel = minLevel,
                maxLevel = maxLevel,
                centerFreqsHz = List(bandCount) { band -> eq.getCenterFreq(band.toShort()) / 1000 },
                levels = levels,
                presetNames = List(eq.numberOfPresets.toInt()) { preset -> eq.getPresetName(preset.toShort()) },
            )
            equalizer = eq
            _state.update { it.copy(eq = eqState) }
        } catch (e: Exception) {
            equalizer = null
        }
    }

    fun setEqEnabled(on: Boolean) {
        val eq = equalizer ?: return
        try {
            eq.setEnabled(on)
        } catch (_: Exception) {
            return
        }
        _state.update { it.copy(eq = it.eq.copy(enabled = on)) }
        schedulePersist()
    }

    fun setEqBand(band: Int, level: Int) {
        val eq = equalizer ?: return
        val current = _state.value.eq
        if (band !in current.levels.indices) return
        val clamped = level.coerceIn(current.minLevel, current.maxLevel)
        // snap to flat near zero
        val value = if (abs(clamped) < (current.maxLevel - current.minLevel) * 0.03f) 0 else clamped
        try {
            eq.setBandLevel(band.toShort(), value.toShort())
        } catch (_: Exception) {
            return
        }
        _state.update { s ->
            val levels = s.eq.levels.toMutableList()
            levels[band] = value
            s.copy(eq = s.eq.copy(levels = levels))
        }
        schedulePersist()
    }

    fun resetEq() {
        val count = _state.value.eq.levels.size
        for (band in 0 until count) setEqBand(band, 0)
    }

    fun nextEqPreset() {
        val eq = equalizer ?: return
        val names = _state.value.eq.presetNames
        if (names.isEmpty()) return
        presetIndex = (presetIndex + 1) % names.size
        try {
            eq.usePreset(presetIndex.toShort())
            eq.setEnabled(true)
            val levels = List(_state.value.eq.levels.size) { band -> eq.getBandLevel(band.toShort()).toInt() }
            _state.update { it.copy(eq = it.eq.copy(enabled = true, levels = levels)) }
            schedulePersist()
        } catch (_: Exception) {
        }
    }

    // ---- spectrum -------------------------------------------------------------------------------------------

    /** Needs the RECORD_AUDIO permission. Safe to call again; does nothing once running. */
    fun startVisualizer() {
        if (visualizer != null) return
        var created: Visualizer? = null
        try {
            val v = Visualizer(player.audioSessionId)
            created = v
            v.setEnabled(false)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null || _state.value.status != PlayStatus.PLAYING) return
                        _spectrum.value = smoother.push(SpectrumMath.bands(fft, SPECTRUM_BANDS))
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true,
            )
            v.setEnabled(true)
            visualizer = v
        } catch (e: Exception) {
            created?.release()
            visualizer = null
        }
    }

    // ---- audio focus ----------------------------------------------------------------------------------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.status == PlayStatus.PLAYING) {
                    pause()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                applyVolume()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                ducked = false
                applyVolume()
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resume()
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
            .build()
            .also { focusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        ducked = false
    }

    // ---- persistence ----------------------------------------------------------------------------------------

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(PERSIST_DELAY_MS)
            val snapshot = _state.value
            withContext(Dispatchers.Default) { store.save(snapshot) }
        }
    }

    companion object {
        private const val TICK_MS = 200L
        private const val RESTART_THRESHOLD_MS = 3000
        private const val PERSIST_DELAY_MS = 600L
        private const val DUCK_GAIN = 0.25f

        @Volatile
        private var instance: PlayerEngine? = null

        fun get(context: Context): PlayerEngine =
            instance ?: synchronized(this) {
                instance ?: PlayerEngine(context.applicationContext).also { instance = it }
            }
    }
}
