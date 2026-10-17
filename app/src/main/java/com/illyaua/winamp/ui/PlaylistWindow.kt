package com.illyaua.winamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illyaua.winamp.core.TimeFormat
import com.illyaua.winamp.player.PlayerEngine

private const val ROW_H = 11f
private const val BAR_H = 20f

@Composable
fun PlaylistWindow(
    engine: PlayerEngine,
    height: Float,
    onAddFiles: () -> Unit,
    onSystemPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var selected by remember { mutableStateOf(setOf<Int>()) }

    // Indices shift when the list shrinks, so drop selections that no longer exist.
    LaunchedEffect(state.tracks.size) {
        selected = selected.filter { it < state.tracks.size }.toSet()
    }
    // Follow the playing track.
    LaunchedEffect(state.currentIndex) {
        if (state.currentIndex >= 0) listState.animateScrollToItem((state.currentIndex - 2).coerceAtLeast(0))
    }

    val listH = height - Skin.TITLE_H - 6f - BAR_H - 6f
    val total = state.tracks.sumOf { it.durationMs }

    Box(modifier.windowBody(Skin.WIDTH, height)) {
        TitleBar("WINAMP PLAYLIST", Modifier.at(0, 0, Skin.WIDTH, Skin.TITLE_H))

        Box(
            Modifier
                .at(6, Skin.TITLE_H + 4f, 263, listH)
                .drawBehind { panel(0f, 0f, 263f, listH, raised = false, fill = Skin.ListBackground) }
        ) {
            if (state.tracks.isEmpty()) {
                LcdText("Playlist is empty. Press ADD to browse.", Skin.LcdLabel, Modifier.at(4, 4, 250, 9), 7f)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .at(1, 1, 254, listH - 2f)
                    .drawBehind {
                        // scroll indicator
                        val info = listState.layoutInfo
                        val count = info.totalItemsCount
                        val visible = info.visibleItemsInfo.size
                        if (count > visible && visible > 0) {
                            val u = density
                            val h = size.height
                            val thumb = (h * visible / count).coerceAtLeast(10f * u)
                            val top = (h - thumb) * listState.firstVisibleItemIndex / (count - visible).coerceAtLeast(1)
                            fillBox(Skin.ButtonFace, 255f, top / u, 6f, thumb / u)
                        }
                    },
            ) {
                itemsIndexed(state.tracks) { index, track ->
                    val isCurrent = index == state.currentIndex
                    val isSelected = index in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_H.dp)
                            .background(if (isSelected) Skin.ListSelected else Skin.ListBackground)
                            .pointerInput(index) {
                                detectTapGestures(
                                    onTap = { engine.playIndex(index) },
                                    onLongPress = {
                                        selected = if (index in selected) selected - index else selected + index
                                    },
                                )
                            }
                            .padding(horizontal = 2.dp)
                    ) {
                        BasicText(
                            "${index + 1}. ${track.displayName}",
                            style = lcdStyle(if (isCurrent) Skin.ListCurrent else Skin.ListText, 7f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (track.durationMs > 0) {
                            BasicText(
                                TimeFormat.mmss(track.durationMs),
                                style = lcdStyle(if (isCurrent) Skin.ListCurrent else Skin.ListText, 7f),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 3.dp).width(26.dp),
                            )
                        }
                    }
                }
            }
        }

        val barY = height - BAR_H - 4f
        BevelButton(Modifier.at(6, barY, 30, 14), onAddFiles, label = "ADD")
        BevelButton(Modifier.at(38, barY, 30, 14), onSystemPicker, label = "SYS")
        BevelButton(
            Modifier.at(70, barY, 30, 14),
            {
                engine.removeAt(selected)
                selected = emptySet()
            },
            label = "REM",
            active = selected.isNotEmpty(),
        )
        BevelButton(
            Modifier.at(102, barY, 30, 14),
            {
                engine.clearPlaylist()
                selected = emptySet()
            },
            label = "CLR",
        )
        if (selected.isNotEmpty()) {
            LcdText("${selected.size} selected", Skin.LcdLabel, Modifier.at(138, barY + 3f, 50, 9), 6f)
        }
        LcdText(
            "${state.tracks.size} tracks  ${TimeFormat.total(total)}",
            Skin.LcdGreen,
            Modifier.at(150, barY + 3f, 119, 9),
            6f,
        )
    }
}
