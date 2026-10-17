package com.illyaua.winamp.desktop

import com.illyaua.winamp.desktop.skin.DefaultSkin
import com.illyaua.winamp.desktop.ui.App
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--export-default-skin") {
        val target = File(args.getOrNull(1) ?: "template.wsz")
        DefaultSkin.exportWsz(target)
        println("Wrote ${target.absolutePath}")
        return
    }
    try {
        val os = System.getProperty("os.name", "").lowercase()
        UIManager.setLookAndFeel(if (os.contains("win")) UIManager.getSystemLookAndFeelClassName() else "javax.swing.plaf.nimbus.NimbusLookAndFeel")
    } catch (_: Exception) {
        // the default look and feel is fine
    }
    val files = args.map { File(it) }.filter { it.exists() }
    SwingUtilities.invokeLater { App().start(files) }
}
