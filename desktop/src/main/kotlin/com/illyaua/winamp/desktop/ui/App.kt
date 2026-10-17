package com.illyaua.winamp.desktop.ui

import com.illyaua.winamp.core.M3u
import com.illyaua.winamp.core.SpectrumFrame
import com.illyaua.winamp.core.TrackKind
import com.illyaua.winamp.desktop.Config
import com.illyaua.winamp.desktop.audio.Event
import com.illyaua.winamp.desktop.audio.Player
import com.illyaua.winamp.desktop.audio.Status
import com.illyaua.winamp.desktop.audio.TrackReader
import com.illyaua.winamp.desktop.audio.file
import com.illyaua.winamp.desktop.skin.DefaultSkin
import com.illyaua.winamp.desktop.skin.Skin
import com.illyaua.winamp.desktop.skin.SkinLoader
import com.illyaua.winamp.desktop.skin.Spr
import java.awt.Desktop
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.JWindow
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

/** Owns the player, the skin and the three windows. Creating an [App] opens nothing; call [start] to show the UI. */
class App(
    val config: Config = Config(),
    val player: Player = Player(dispatch = { SwingUtilities.invokeLater(it) }),
) {
    var skin: Skin = DefaultSkin.skin
        private set
    var scale: Int = config.int("scale", defaultScale()).coerceIn(1, 4)
        private set
    var spectrum: SpectrumFrame = SpectrumFrame.empty(Player.BARS)
        private set

    var eqVisible = config.bool("eq.visible", false)
        private set
    var playlistVisible = config.bool("playlist.visible", true)
        private set
    var mainActive = true
    var eqActive = false
    var playlistActive = false

    val mainPanel by lazy { MainPanel(this) }
    val eqPanel by lazy { EqPanel(this) }
    val playlistPanel by lazy { PlaylistPanel(this, config.int("playlist.height", 232).coerceIn(Spr.MAIN_H, 1200)) }

    private var frame: JFrame? = null
    private var eqWindow: JWindow? = null
    private var playlistWindow: JWindow? = null
    private var skinName = config.string("skin")
    private var settingsDirtyAt = 0L
    private var soundFont: File? = config.string("soundfont")?.let { File(it) }?.takeIf { it.exists() }

    init {
        loadSettings()
    }

    private fun defaultScale(): Int = try {
        if (java.awt.GraphicsEnvironment.isHeadless()) 1 else if (java.awt.Toolkit.getDefaultToolkit().screenSize.width >= 1900) 2 else 1
    } catch (_: Exception) {
        1
    }

    // ---- start-up -------------------------------------------------------------------------------------------

    fun start(files: List<File>) {
        val main = JFrame("WinAmp Classic").apply {
            isUndecorated = true
            isResizable = false
            defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            iconImages = listOf(AppIcon.create(32), AppIcon.create(64), AppIcon.create(128))
            contentPane.add(mainPanel)
            pack()
        }
        frame = main
        eqWindow = JWindow(main).apply { contentPane.add(eqPanel); pack() }
        playlistWindow = JWindow(main).apply { contentPane.add(playlistPanel); pack() }

        val x = config.int("main.x", 100)
        val y = config.int("main.y", 100)
        main.setLocation(x, y)
        eqWindow!!.setLocation(config.int("eq.x", x), config.int("eq.y", y + main.height))
        val plDefaultY = y + main.height + if (eqVisible) eqWindow!!.height else 0
        playlistWindow!!.setLocation(config.int("playlist.x", x), config.int("playlist.y", plDefaultY))

        for (w in listOf<Window>(main, eqWindow!!, playlistWindow!!)) installTitleFocus(w)
        for (panel in listOf<JComponent>(mainPanel, eqPanel, playlistPanel)) panel.transferHandler = DropHandler(this)
        installKeys(main.rootPane, volumeKeys = true)
        installKeys(eqWindow!!.rootPane, volumeKeys = true)
        installKeys(playlistWindow!!.rootPane, volumeKeys = false)

        main.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = quit()
        })

        applyAlwaysOnTop(config.bool("alwaysOnTop", false))
        eqWindow!!.isVisible = eqVisible
        playlistWindow!!.isVisible = playlistVisible
        main.isVisible = true

        player.addListener { onPlayerEvent(it) }
        Timer(40) { tick() }.start()
        restorePlaylist()
        if (files.isNotEmpty()) player.addFiles(files, playFirst = true)
    }

    private var tickCount = 0

    internal fun tick() {
        spectrum = player.pollSpectrum()
        mainPanel.tick()
        tickCount++
        if (tickCount % 6 == 0 && player.status == Status.PLAYING) playlistPanel.repaint()
        if (settingsDirtyAt != 0L && System.currentTimeMillis() - settingsDirtyAt > 2000) saveConfig()
    }

    private fun onPlayerEvent(e: Event) {
        when (e) {
            Event.PLAYLIST -> { playlistPanel.playlistChanged(); playlistPanel.repaint() }
            Event.TRACK -> { playlistPanel.ensureVisible(player.currentIndex); playlistPanel.repaint(); mainPanel.repaint() }
            Event.SETTINGS -> { settingsDirtyAt = System.currentTimeMillis(); mainPanel.repaint(); eqPanel.repaint() }
            Event.STATUS, Event.ERROR -> { mainPanel.repaint(); playlistPanel.repaint() }
        }
    }

    private fun loadSettings() {
        player.setVolume(config.float("volume", 0.8f))
        player.setBalance(config.float("balance", 0f))
        player.setShuffle(config.bool("shuffle", false))
        player.setRepeat(config.bool("repeat", false))
        player.setEqEnabled(config.bool("eq.on", true))
        player.setEqPreamp(config.float("eq.preamp", 0f))
        for (i in 0 until 10) player.setEqBand(i, config.float("eq.band$i", 0f))
        player.soundFont = soundFont
        val saved = skinName?.let { name -> listSkins().firstOrNull { it.name == name } }
        if (saved != null) {
            try {
                skin = SkinLoader.load(saved, DefaultSkin.skin)
            } catch (_: SkinLoader.SkinException) {
                skinName = null
            }
        }
    }

    // ---- config ---------------------------------------------------------------------------------------------

    fun saveConfig() {
        settingsDirtyAt = 0
        config.set("scale", scale)
        config.set("volume", player.volume)
        config.set("balance", player.balance)
        config.set("shuffle", player.shuffle)
        config.set("repeat", player.repeat)
        config.set("eq.on", player.eqEnabled)
        config.set("eq.preamp", player.eqPreampDb)
        for (i in 0 until 10) config.set("eq.band$i", player.eqBand(i))
        config.set("eq.visible", eqVisible)
        config.set("playlist.visible", playlistVisible)
        config.set("playlist.height", playlistPanel.baseHeight)
        config.set("skin", skinName)
        config.set("soundfont", soundFont?.absolutePath)
        frame?.takeIf { it.isShowing }?.let { config.set("main.x", it.x); config.set("main.y", it.y) }
        eqWindow?.let { config.set("eq.x", it.x); config.set("eq.y", it.y) }
        playlistWindow?.let { config.set("playlist.x", it.x); config.set("playlist.y", it.y) }
        config.save()
        savePlaylist(config.playlistFile)
    }

    private fun savePlaylist(target: File) {
        try {
            val entries = player.tracks.map { M3u.Entry(it.file().path, it.displayName, (it.durationMs / 1000).toInt()) }
            target.parentFile?.mkdirs()
            target.writeText(M3u.write(entries), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun restorePlaylist() {
        val f = config.playlistFile
        if (!f.exists()) return
        try {
            val files = M3u.parse(f.readText(Charsets.UTF_8), f.parentFile).map { File(it.path) }.filter { it.isFile }
            if (files.isNotEmpty()) player.addFiles(files)
        } catch (_: Exception) {
        }
    }

    // ---- windows --------------------------------------------------------------------------------------------

    fun visibleWindows(): List<Window> = listOfNotNull(frame, eqWindow, playlistWindow).filter { it.isVisible }

    /** Windows sitting directly under [w], and under those in turn, which move along with it. */
    fun windowsBelow(w: Window): List<Window> {
        val result = ArrayList<Window>()
        var queue = listOf(w)
        while (queue.isNotEmpty()) {
            val next = ArrayList<Window>()
            for (parent in queue) {
                for (other in visibleWindows()) {
                    if (other === w || other in result) continue
                    val p = parent.bounds
                    val o = other.bounds
                    val touching = kotlin.math.abs(o.y - (p.y + p.height)) <= 3
                    val overlapping = o.x < p.x + p.width && o.x + o.width > p.x
                    if (touching && overlapping) { result += other; next += other }
                }
            }
            queue = next
        }
        return result
    }

    private fun installTitleFocus(w: Window) {
        w.addWindowFocusListener(object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent) = setActive(w, true)
            override fun windowLostFocus(e: WindowEvent) = setActive(w, false)
        })
    }

    private fun setActive(w: Window, active: Boolean) {
        when (w) {
            frame -> mainActive = active
            eqWindow -> eqActive = active
            playlistWindow -> playlistActive = active
        }
        mainPanel.repaint(); eqPanel.repaint(); playlistPanel.repaint()
    }

    private fun isDirectlyBelow(upper: Window, lower: Window) =
        kotlin.math.abs(lower.y - (upper.y + upper.height)) <= 3 && lower.x < upper.x + upper.width && lower.x + lower.width > upper.x

    fun setEqVisible(visible: Boolean) {
        val w = eqWindow ?: return
        val main = frame ?: return
        if (visible == eqVisible) return
        val pl = playlistWindow?.takeIf { it.isVisible }
        if (visible) {
            // slot the equalizer in under the main window and push a docked playlist down
            val playlistWasDocked = pl != null && isDirectlyBelow(main, pl)
            w.setLocation(main.x, main.y + main.height)
            if (playlistWasDocked) pl!!.setLocation(pl.x, pl.y + w.height)
        } else if (pl != null && isDirectlyBelow(w, pl)) {
            pl.setLocation(pl.x, pl.y - w.height)
        }
        eqVisible = visible
        w.isVisible = visible
        mainPanel.repaint()
        saveConfig()
    }

    fun setPlaylistVisible(visible: Boolean) {
        val w = playlistWindow ?: return
        if (visible == playlistVisible) return
        playlistVisible = visible
        if (visible) {
            val anchor = if (eqVisible) eqWindow!! else frame!!
            w.setLocation(anchor.x, anchor.y + anchor.height)
        }
        w.isVisible = visible
        mainPanel.repaint()
        saveConfig()
    }

    fun resizePlaylist(height: Int) {
        playlistPanel.baseHeight = height
        playlistWindow?.pack()
    }

    fun minimize() {
        frame?.extendedState = JFrame.ICONIFIED
    }

    fun setScale(n: Int) {
        val newScale = n.coerceIn(1, 4)
        if (newScale == scale) return
        val main = frame
        val oldScale = scale
        val rel = listOfNotNull(eqWindow, playlistWindow).map { w ->
            Triple(w, (w.x - (main?.x ?: 0)) / oldScale, (w.y - (main?.y ?: 0)) / oldScale)
        }
        scale = newScale
        for (p in listOf<SkinnedPanel>(mainPanel, eqPanel, playlistPanel)) p.updateSize()
        main?.pack(); eqWindow?.pack(); playlistWindow?.pack()
        for ((w, dx, dy) in rel) w.setLocation((main?.x ?: 0) + dx * newScale, (main?.y ?: 0) + dy * newScale)
        saveConfig()
    }

    private fun applyAlwaysOnTop(on: Boolean) {
        config.set("alwaysOnTop", on)
        for (w in listOfNotNull(frame, eqWindow, playlistWindow)) w.isAlwaysOnTop = on
    }

    fun quit() {
        saveConfig()
        player.close()
        for (w in listOfNotNull(frame, eqWindow, playlistWindow)) w.dispose()
        exitProcess(0)
    }

    // ---- skins ----------------------------------------------------------------------------------------------

    fun listSkins(): List<File> {
        val dir = config.skinsDir
        dir.mkdirs()
        return dir.listFiles { f ->
            f.isDirectory && File(f, "main.bmp").exists() ||
                f.isFile && f.extension.lowercase() in setOf("wsz", "zip")
        }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    /** Applies a skin file or folder, or the built-in skin when [file] is null. Returns an error message on failure. */
    fun applySkin(file: File?): String? {
        if (file == null) {
            skin = DefaultSkin.skin
            skinName = null
        } else {
            try {
                skin = SkinLoader.load(file, DefaultSkin.skin)
            } catch (e: SkinLoader.SkinException) {
                return e.message
            }
            // keep a copy in the skins folder so it can be picked from the menu next time
            val inSkinsDir = file.absoluteFile.parentFile == config.skinsDir.absoluteFile
            if (!inSkinsDir && file.isFile) {
                try {
                    file.copyTo(File(config.skinsDir, file.name), overwrite = true)
                } catch (_: Exception) {
                }
            }
            skinName = file.name
        }
        mainPanel.repaint(); eqPanel.repaint(); playlistPanel.repaint()
        saveConfig()
        return null
    }

    // ---- files and menus ------------------------------------------------------------------------------------

    private fun chooser(): JFileChooser = JFileChooser(config.string("lastDir")?.let { File(it) }).apply {
        isMultiSelectionEnabled = true
    }

    private fun remember(chooser: JFileChooser) {
        (chooser.currentDirectory)?.let { config.set("lastDir", it.absolutePath) }
    }

    fun openFiles(play: Boolean = false) {
        val c = chooser().apply {
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            dialogTitle = "Add files or folders"
            fileFilter = FileNameExtensionFilter("Audio files (mp3, wav, mid, ogg, flac)", "mp3", "wav", "wave", "mid", "midi", "kar", "ogg", "oga", "flac")
        }
        if (c.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        player.addFiles(c.selectedFiles.toList(), playFirst = play)
    }

    fun openFolder() {
        val c = chooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Add folders (sub-folders are included)"
        }
        if (c.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        player.addFiles(c.selectedFiles.toList())
    }

    fun openPlaylistFile() {
        val c = chooser().apply {
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Playlists (m3u, m3u8)", "m3u", "m3u8")
        }
        if (c.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        val f = c.selectedFile
        val files = M3u.parse(f.readText(Charsets.UTF_8), f.parentFile).map { File(it.path) }.filter { it.exists() }
        player.clear()
        player.addFiles(files)
    }

    fun savePlaylistFile() {
        val c = chooser().apply {
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("Playlists (m3u8)", "m3u8")
            selectedFile = File(currentDirectory, "playlist.m3u8")
        }
        if (c.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        var f = c.selectedFile
        if (f.extension.isEmpty()) f = File(f.path + ".m3u8")
        savePlaylist(f)
    }

    private fun chooseSkin() {
        val c = chooser().apply {
            isMultiSelectionEnabled = false
            fileSelectionMode = JFileChooser.FILES_AND_DIRECTORIES
            dialogTitle = "Load a skin"
            fileFilter = FileNameExtensionFilter("Winamp skins (wsz, zip)", "wsz", "zip")
        }
        if (c.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        applySkin(c.selectedFile)?.let { JOptionPane.showMessageDialog(frame, it, "Skin", JOptionPane.ERROR_MESSAGE) }
    }

    private fun chooseSoundFont() {
        val c = chooser().apply {
            isMultiSelectionEnabled = false
            dialogTitle = "Choose a SoundFont for MIDI"
            fileFilter = FileNameExtensionFilter("SoundFonts (sf2, dls)", "sf2", "dls")
        }
        if (c.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        soundFont = c.selectedFile
        player.soundFont = soundFont
        saveConfig()
        JOptionPane.showMessageDialog(frame, "The SoundFont is used from the next MIDI file you play.", "SoundFont", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun exportTemplate() {
        val c = chooser().apply {
            isMultiSelectionEnabled = false
            dialogTitle = "Save the built-in skin as a template"
            selectedFile = File(currentDirectory, "template.wsz")
        }
        if (c.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return
        remember(c)
        DefaultSkin.exportWsz(c.selectedFile)
    }

    fun showMainMenu(owner: JComponent, x: Int, y: Int) {
        val menu = JPopupMenu()
        fun item(text: String, action: () -> Unit) = JMenuItem(text).apply { addActionListener { action() } }

        menu.add(item("Play file(s)...") { openFiles(play = true) })
        menu.add(item("Add folder(s)...") { openFolder() })
        menu.add(item("Open playlist...") { openPlaylistFile() })
        menu.add(item("Save playlist...") { savePlaylistFile() })
        menu.addSeparator()

        val skins = JMenu("Skins")
        val group = javax.swing.ButtonGroup()
        val builtIn = JRadioButtonMenuItem("Built-in", skinName == null).apply { addActionListener { applySkin(null) } }
        group.add(builtIn); skins.add(builtIn)
        for (f in listSkins()) {
            val label = f.nameWithoutExtension
            val mi = JRadioButtonMenuItem(label, skinName == f.name).apply {
                addActionListener {
                    applySkin(f)?.let { JOptionPane.showMessageDialog(frame, it, "Skin", JOptionPane.ERROR_MESSAGE) }
                }
            }
            group.add(mi); skins.add(mi)
        }
        skins.addSeparator()
        skins.add(item("Load skin file...") { chooseSkin() })
        skins.add(item("Open skins folder") {
            try {
                config.skinsDir.mkdirs()
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(config.skinsDir)
            } catch (_: Exception) {
                JOptionPane.showMessageDialog(frame, config.skinsDir.absolutePath, "Skins folder", JOptionPane.INFORMATION_MESSAGE)
            }
        })
        skins.add(item("Save built-in skin as template...") { exportTemplate() })
        menu.add(skins)

        val options = JMenu("Options")
        options.add(JCheckBoxMenuItem("Double size", scale == 2).apply { addActionListener { setScale(if (isSelected) 2 else 1) } })
        options.add(JMenu("Window size").apply {
            for (n in 1..4) add(JRadioButtonMenuItem("${n}x", scale == n).apply { addActionListener { setScale(n) } })
        })
        options.add(JCheckBoxMenuItem("Always on top", config.bool("alwaysOnTop", false)).apply {
            addActionListener { applyAlwaysOnTop(isSelected); saveConfig() }
        })
        options.add(JCheckBoxMenuItem("Shuffle", player.shuffle).apply { addActionListener { player.setShuffle(isSelected) } })
        options.add(JCheckBoxMenuItem("Repeat", player.repeat).apply { addActionListener { player.setRepeat(isSelected) } })
        menu.add(options)

        val windows = JMenu("Windows")
        windows.add(JCheckBoxMenuItem("Equalizer", eqVisible).apply { addActionListener { setEqVisible(isSelected) } })
        windows.add(JCheckBoxMenuItem("Playlist editor", playlistVisible).apply { addActionListener { setPlaylistVisible(isSelected) } })
        menu.add(windows)

        menu.add(item("Choose MIDI SoundFont...") { chooseSoundFont() })
        if (soundFont != null) menu.add(item("Use default MIDI sounds") { soundFont = null; player.soundFont = null; saveConfig() })
        menu.addSeparator()
        menu.add(item("About") {
            JOptionPane.showMessageDialog(
                frame,
                "WinAmp Classic (desktop)\nMP3, WAV, MIDI, Ogg and FLAC with classic Winamp skins.\n\n" +
                    "Skins folder:\n${config.skinsDir.absolutePath}",
                "About", JOptionPane.INFORMATION_MESSAGE,
            )
        })
        menu.add(item("Exit") { quit() })
        menu.show(owner, x, y)
    }

    // ---- keyboard and drag-and-drop ---------------------------------------------------------------------------

    private fun installKeys(root: javax.swing.JRootPane, volumeKeys: Boolean) {
        val inputs = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        val actions = root.actionMap
        fun bind(name: String, stroke: KeyStroke, action: () -> Unit) {
            inputs.put(stroke, name)
            actions.put(name, object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) = action()
            })
        }
        fun key(code: Int, mods: Int = 0) = KeyStroke.getKeyStroke(code, mods)
        bind("prev", key(KeyEvent.VK_Z)) { player.previous() }
        bind("play", key(KeyEvent.VK_X)) { player.play() }
        bind("pause", key(KeyEvent.VK_C)) { player.togglePause() }
        bind("stop", key(KeyEvent.VK_V)) { player.stop() }
        bind("next", key(KeyEvent.VK_B)) { player.next() }
        bind("open", key(KeyEvent.VK_L)) { openFiles(play = true) }
        bind("folder", key(KeyEvent.VK_L, KeyEvent.SHIFT_DOWN_MASK)) { openFolder() }
        bind("shuffle", key(KeyEvent.VK_S)) { player.setShuffle(!player.shuffle) }
        bind("repeat", key(KeyEvent.VK_R)) { player.setRepeat(!player.repeat) }
        bind("double", key(KeyEvent.VK_D)) { setScale(if (scale == 1) 2 else 1) }
        bind("eq", key(KeyEvent.VK_E, KeyEvent.ALT_DOWN_MASK)) { setEqVisible(!eqVisible) }
        bind("pl", key(KeyEvent.VK_G, KeyEvent.ALT_DOWN_MASK)) { setPlaylistVisible(!playlistVisible) }
        bind("pl2", key(KeyEvent.VK_P, KeyEvent.ALT_DOWN_MASK)) { setPlaylistVisible(!playlistVisible) }
        if (volumeKeys) {
            bind("back", key(KeyEvent.VK_LEFT)) { player.seek(player.positionMs() - 5000) }
            bind("fwd", key(KeyEvent.VK_RIGHT)) { player.seek(player.positionMs() + 5000) }
            bind("volup", key(KeyEvent.VK_UP)) { player.setVolume(player.volume + 0.05f) }
            bind("voldown", key(KeyEvent.VK_DOWN)) { player.setVolume(player.volume - 0.05f) }
        }
    }

    fun handleDropped(files: List<File>) {
        val skinFile = files.singleOrNull()?.takeIf { it.isFile && it.extension.lowercase() in setOf("wsz", "zip") }
        if (skinFile != null) {
            applySkin(skinFile)?.let { JOptionPane.showMessageDialog(frame, it, "Skin", JOptionPane.ERROR_MESSAGE) }
            return
        }
        val playlist = files.singleOrNull()?.takeIf { it.isFile && it.extension.lowercase() in setOf("m3u", "m3u8") }
        if (playlist != null) {
            val entries = M3u.parse(playlist.readText(Charsets.UTF_8), playlist.parentFile).map { File(it.path) }.filter { it.exists() }
            player.addFiles(entries, playFirst = player.status == Status.STOPPED)
            return
        }
        player.addFiles(files, playFirst = player.status == Status.STOPPED && player.tracks.isEmpty())
    }

    private class DropHandler(private val app: App) : TransferHandler() {
        override fun canImport(support: TransferSupport) = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

        override fun importData(support: TransferSupport): Boolean {
            if (!canImport(support)) return false
            return try {
                @Suppress("UNCHECKED_CAST")
                val files = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                app.handleDropped(files)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
