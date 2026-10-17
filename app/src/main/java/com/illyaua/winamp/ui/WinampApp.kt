package com.illyaua.winamp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.illyaua.winamp.player.PlayerEngine
import com.illyaua.winamp.player.StorageRoots
import kotlin.math.max
import kotlin.math.min

/**
 * Lays the windows out in classic Winamp pixels. Overriding LocalDensity makes 1.dp equal one classic pixel
 * scaled to fill the screen width, so the windows keep their proportions on any phone or tablet.
 */
@Composable
fun WinampApp(
    engine: PlayerEngine,
    onSystemPicker: () -> Unit,
    onRequestStorage: () -> Unit,
) {
    var showEq by rememberSaveable { mutableStateOf(false) }
    var showPlaylist by rememberSaveable { mutableStateOf(true) }
    var showBrowser by rememberSaveable { mutableStateOf(false) }
    var browserDir by rememberSaveable { mutableStateOf(StorageRoots.startDirectory().absolutePath) }

    BackHandler(enabled = showBrowser) { showBrowser = false }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        // The browser takes the place of the equalizer and playlist while it is open.
        val eqHeight = if (showEq && !showBrowser) Skin.EQ_H else 0f
        val minBottom = when {
            showBrowser -> Skin.BROWSER_MIN_H
            showPlaylist -> Skin.PLAYLIST_MIN_H
            else -> 0f
        }

        // Width decides the scale on a phone held upright; height decides it in landscape.
        val scale = min(widthPx / Skin.WIDTH, heightPx / (Skin.MAIN_H + eqHeight + minBottom))
        val bottomHeight = if (minBottom > 0f) max(minBottom, heightPx / scale - Skin.MAIN_H - eqHeight) else 0f

        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            Column(
                Modifier
                    .width(Skin.WIDTH.dp)
                    .align(Alignment.TopCenter)
            ) {
                MainWindow(
                    engine = engine,
                    showEq = showEq,
                    showPlaylist = showPlaylist,
                    onToggleEq = { showEq = !showEq },
                    onTogglePlaylist = { showPlaylist = !showPlaylist },
                    onAddFiles = { showBrowser = true },
                )
                if (showBrowser) {
                    FileBrowserWindow(
                        engine = engine,
                        height = bottomHeight,
                        dir = browserDir,
                        onDirChange = { browserDir = it },
                        onClose = { showBrowser = false },
                        onSystemPicker = onSystemPicker,
                        onRequestStorage = onRequestStorage,
                    )
                } else {
                    if (showEq) EqualizerWindow(engine)
                    if (showPlaylist) {
                        PlaylistWindow(
                            engine = engine,
                            height = bottomHeight,
                            onAddFiles = { showBrowser = true },
                            onSystemPicker = onSystemPicker,
                        )
                    }
                }
            }
        }
    }
}
