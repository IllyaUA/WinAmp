package com.illyaua.winamp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.illyaua.winamp.core.DirEntry
import com.illyaua.winamp.core.DirectoryLister
import com.illyaua.winamp.player.PlayerEngine
import com.illyaua.winamp.player.StorageAccess
import com.illyaua.winamp.player.StorageRoots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val ROW_H = 12f
private const val BAR_H = 14f

/**
 * Browses folders by path. Tick files and folders (a ticked folder adds everything playable inside it,
 * including sub-folders), then press ADD. Tap a folder name to open it.
 */
@Composable
fun FileBrowserWindow(
    engine: PlayerEngine,
    height: Float,
    dir: String,
    onDirChange: (String) -> Unit,
    onClose: () -> Unit,
    onSystemPicker: () -> Unit,
    onRequestStorage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.hasAccess(context)) }
    // Coming back from the system settings page re-checks the permission.
    LifecycleResumeEffect(Unit) {
        hasAccess = StorageAccess.hasAccess(context)
        onPauseOrDispose { }
    }

    val shortcuts = remember { StorageRoots.shortcuts(context) }
    val primaryPath = remember { StorageRoots.primary().absolutePath }
    val rootPaths = remember { shortcuts.filter { it.label == "INT" || it.label == "SD" }.map { it.dir.absolutePath } }

    var selected by remember { mutableStateOf(setOf<String>()) }

    val entries by produceState(emptyList<DirEntry>(), dir, hasAccess) {
        value = if (hasAccess) withContext(Dispatchers.IO) { DirectoryLister.list(File(dir)) } else emptyList()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(dir) { listState.scrollToItem(0) }

    val parent = File(dir).parentFile
    val canGoUp = dir !in rootPaths && parent != null
    fun goUp() {
        if (canGoUp && parent != null) onDirChange(parent.absolutePath)
    }
    // Back walks up the folder tree first and closes the browser once at the top.
    BackHandler(enabled = canGoUp) { goUp() }

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    val filesHere = entries.filter { !it.isDirectory }
    val listTop = Skin.TITLE_H + 4f + 12f + 3f + 12f + 3f
    val statusY = height - BAR_H - 8f - 11f
    val listH = statusY - listTop - 2f

    Box(modifier.windowBody(Skin.WIDTH, height)) {
        TitleBar("WINAMP ADD FILES", Modifier.at(0, 0, Skin.WIDTH, Skin.TITLE_H))

        // Current folder and the Up button.
        val shown = when {
            dir.startsWith(primaryPath) -> "INT:" + dir.removePrefix(primaryPath).ifEmpty { "/" }
            else -> dir
        }
        Box(
            Modifier
                .at(6, Skin.TITLE_H + 4f, 232, 12)
                .drawBehind { panel(0f, 0f, 232f, 12f, raised = false, fill = Skin.LcdBackground) }
        ) {
            LcdText(
                if (shown.length > 46) "..." + shown.takeLast(43) else shown,
                Skin.LcdGreen,
                Modifier.at(3, 2, 227, 9),
                7f,
            )
        }
        BevelButton(Modifier.at(240, Skin.TITLE_H + 4f, 29, 12), ::goUp, label = "UP", active = canGoUp)

        // Shortcuts.
        var x = 6f
        for (s in shortcuts) {
            val w = 6f * s.label.length + 10f
            BevelButton(
                Modifier.at(x, Skin.TITLE_H + 19f, w, 12),
                { onDirChange(s.dir.absolutePath) },
                label = s.label,
                active = dir == s.dir.absolutePath,
            )
            x += w + 2f
        }

        Box(
            Modifier
                .at(6, listTop, 263, listH)
                .drawBehind { panel(0f, 0f, 263f, listH, raised = false, fill = Skin.ListBackground) }
        ) {
            when {
                !hasAccess -> {
                    LcdText("Android needs your OK to browse folders.", Skin.LcdLabel, Modifier.at(6, 6, 250, 9), 7f)
                    LcdText("Tap GRANT, switch on \"Allow access to", Skin.LcdLabel, Modifier.at(6, 17, 250, 9), 7f)
                    LcdText("manage all files\", then come back.", Skin.LcdLabel, Modifier.at(6, 28, 250, 9), 7f)
                    BevelButton(Modifier.at(6, 44, 70, 14), onRequestStorage, label = "GRANT ACCESS")
                }
                entries.isEmpty() -> {
                    LcdText("No folders or audio files here.", Skin.LcdLabel, Modifier.at(6, 6, 250, 9), 7f)
                }
                else -> LazyColumn(state = listState, modifier = Modifier.at(1, 1, 261, listH - 2f)) {
                    items(entries, key = { it.file.path }) { entry ->
                        val path = entry.file.absolutePath
                        val isSelected = path in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(ROW_H.dp)
                                .background(if (isSelected) Skin.ListSelected else Skin.ListBackground)
                                .pointerInput(path) {
                                    detectTapGestures(
                                        onTap = { if (entry.isDirectory) onDirChange(path) else toggle(path) },
                                        onLongPress = { toggle(path) },
                                    )
                                }
                        ) {
                            Box(
                                Modifier
                                    .size(ROW_H.dp)
                                    .pointerInput(path) { detectTapGestures(onTap = { toggle(path) }) }
                                    .drawBehind {
                                        panel(2f, 2f, 8f, 8f, raised = false, fill = Skin.LcdBackground)
                                        if (isSelected) fillBox(Skin.LcdGreen, 4f, 4f, 4f, 4f)
                                    }
                            )
                            BasicText(
                                if (entry.isDirectory) "[${entry.name}]" else entry.name,
                                style = lcdStyle(if (entry.isDirectory) Skin.SpectrumMid else Skin.ListText, 7f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        LcdText(
            "${selected.size} ticked   ${filesHere.size} audio files in this folder",
            Skin.LcdGreen,
            Modifier.at(8, statusY, 258, 9),
            6f,
        )

        val barY = height - BAR_H - 5f
        BevelButton(
            Modifier.at(6, barY, 22, BAR_H),
            { selected = selected + filesHere.map { it.file.absolutePath } },
            label = "ALL",
        )
        BevelButton(Modifier.at(30, barY, 30, BAR_H), { selected = emptySet() }, label = "NONE")
        BevelButton(Modifier.at(62, barY, 24, BAR_H), onSystemPicker, label = "SYS")
        BevelButton(Modifier.at(166, barY, 40, BAR_H), onClose, label = "CLOSE")
        BevelButton(
            Modifier.at(208, barY, 61, BAR_H),
            {
                if (selected.isNotEmpty()) {
                    engine.addFiles(selected.map { File(it) })
                    onClose()
                }
            },
            label = if (selected.isEmpty()) "ADD" else "ADD ${selected.size}",
            active = selected.isNotEmpty(),
        )
    }
}
