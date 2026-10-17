@file:OptIn(ExperimentalFoundationApi::class)

package com.illyaua.winamp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illyaua.winamp.core.TimeFormat
import com.illyaua.winamp.core.TrackKind
import com.illyaua.winamp.player.PlayStatus
import com.illyaua.winamp.player.PlayerEngine

@Composable
fun MainWindow(
    engine: PlayerEngine,
    showEq: Boolean,
    showPlaylist: Boolean,
    onToggleEq: () -> Unit,
    onTogglePlaylist: () -> Unit,
    onAddFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val position by engine.position.collectAsStateWithLifecycle()
    val spectrum = engine.spectrum.collectAsStateWithLifecycle()

    var showRemaining by rememberSaveable { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val track = state.current
    val stopped = state.status == PlayStatus.STOPPED
    val duration = state.durationMs
    val progress = if (dragging) dragValue else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val clockMs = when {
        stopped -> 0L
        dragging -> (dragValue * duration).toLong()
        showRemaining && duration > 0 -> (duration - position).toLong()
        else -> position.toLong()
    }

    val titleText = when {
        state.error != null -> state.error!!
        track == null -> "WinAmp Classic - add files to start"
        else -> {
            val index = state.currentIndex + 1
            val length = if (track.durationMs > 0) " (${TimeFormat.mmss(track.durationMs)})" else ""
            "$index. ${track.displayName}$length  ***  "
        }
    }

    Box(modifier.windowBody(Skin.WIDTH, Skin.MAIN_H)) {
        TitleBar("WINAMP", Modifier.at(0, 0, Skin.WIDTH, Skin.TITLE_H))

        // Time display, tap to switch between elapsed and remaining.
        Box(
            Modifier
                .at(6, 18, 108, 44)
                .drawBehind { panel(0f, 0f, 108f, 44f, raised = false, fill = Skin.LcdBackground) }
        )
        Box(
            Modifier
                .at(8, 20, 12, 12)
                .drawBehind {
                    when (state.status) {
                        PlayStatus.PLAYING -> iconPlay(Skin.LcdGreen)
                        PlayStatus.PAUSED -> iconPause(Skin.LcdGreen)
                        PlayStatus.STOPPED -> iconStop(Skin.LcdGreen)
                    }
                }
        )
        LcdClock(
            digits = TimeFormat.lcd(clockMs),
            negative = showRemaining && !stopped,
            modifier = Modifier
                .at(24, 22, 56, 13)
                .pointerInput(Unit) { detectTapGestures { showRemaining = !showRemaining } },
        )
        SpectrumView(spectrum, Modifier.at(24, 40, 76, 16))

        // Title marquee and stream info.
        Box(
            Modifier
                .at(116, 18, 153, 11)
                .background(Skin.LcdBackground)
                .clipToBounds()
        ) {
            BasicText(
                titleText,
                style = lcdStyle(if (state.error != null) Skin.ListError else Skin.LcdGreen, 7f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .at(2, 1, 151, 9)
                    .basicMarquee(iterations = Int.MAX_VALUE, velocity = 25.dp),
            )
        }

        val kbps = when {
            track == null -> ""
            track.kind == TrackKind.MIDI -> "MIDI"
            track.bitrateKbps > 0 -> track.bitrateKbps.toString()
            else -> ""
        }
        val khz = if (track != null && track.sampleRateHz > 0) (track.sampleRateHz / 1000).toString() else ""
        LcdText(kbps, Skin.LcdGreen, Modifier.at(116, 32, 22, 8), 6f)
        if (track?.kind != TrackKind.MIDI) LcdText("kbps", Skin.LcdLabel, Modifier.at(139, 32, 22, 8), 6f)
        LcdText(khz, Skin.LcdGreen, Modifier.at(168, 32, 14, 8), 6f)
        LcdText("kHz", Skin.LcdLabel, Modifier.at(183, 32, 18, 8), 6f)
        val stereo = track != null && track.channels != 1
        val mono = track != null && track.channels == 1
        LcdText("mono", if (mono) Skin.LcdGreen else Skin.LcdDim, Modifier.at(213, 32, 24, 8), 6f)
        LcdText("stereo", if (stereo) Skin.LcdGreen else Skin.LcdDim, Modifier.at(239, 32, 30, 8), 6f)

        // Volume, balance and window toggles.
        ClassicSlider(
            value = state.volume,
            onValueChange = engine::setVolume,
            modifier = Modifier.at(116, 44, 68, 12),
            thumbLength = 14f,
            fillGradient = true,
        )
        ClassicSlider(
            value = (state.balance + 1f) / 2f,
            onValueChange = { engine.setBalance(it * 2f - 1f) },
            modifier = Modifier.at(190, 44, 38, 12),
            thumbLength = 14f,
        )
        BevelButton(Modifier.at(232, 43, 18, 13), onToggleEq, label = "EQ", active = showEq)
        BevelButton(Modifier.at(251, 43, 18, 13), onTogglePlaylist, label = "PL", active = showPlaylist)

        // Seek bar.
        ClassicSlider(
            value = progress,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                engine.seekTo((dragValue * duration).toInt())
                dragging = false
            },
            modifier = Modifier.at(6, 68, 263, 11),
            thumbLength = 29f,
            enabled = !stopped && duration > 0,
        )

        // Transport.
        BevelButton(Modifier.at(6, 86, 23, 18), engine::previous, icon = { iconPrevious(it) })
        BevelButton(Modifier.at(29, 86, 23, 18), engine::play, icon = { iconPlay(it) }, active = state.status == PlayStatus.PLAYING)
        BevelButton(Modifier.at(52, 86, 23, 18), engine::togglePause, icon = { iconPause(it) }, active = state.status == PlayStatus.PAUSED)
        BevelButton(Modifier.at(75, 86, 23, 18), engine::stop, icon = { iconStop(it) })
        BevelButton(Modifier.at(98, 86, 23, 18), engine::next, icon = { iconNext(it) })
        BevelButton(Modifier.at(127, 87, 23, 16), onAddFiles, icon = { iconEject(it) })

        BevelButton(Modifier.at(168, 87, 47, 16), { engine.setShuffle(!state.shuffle) }, label = "SHUFFLE", active = state.shuffle)
        BevelButton(Modifier.at(217, 87, 38, 16), { engine.setRepeat(!state.repeat) }, label = "REPEAT", active = state.repeat)
    }
}
