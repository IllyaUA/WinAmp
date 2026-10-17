package com.illyaua.winamp.desktop

import java.io.File
import java.util.Properties

/** Settings kept between runs in a plain properties file next to the playlist and the skins folder. */
class Config(val dir: File = defaultDir()) {
    private val props = Properties()
    private val file get() = File(dir, "config.properties")
    val skinsDir get() = File(dir, "skins")
    val playlistFile get() = File(dir, "playlist.m3u8")

    init {
        dir.mkdirs()
        try {
            if (file.exists()) file.inputStream().use { props.load(it) }
        } catch (_: Exception) {
            // a damaged file just means defaults
        }
    }

    fun string(key: String): String? = props.getProperty(key)
    fun int(key: String, default: Int) = props.getProperty(key)?.toIntOrNull() ?: default
    fun float(key: String, default: Float) = props.getProperty(key)?.toFloatOrNull() ?: default
    fun bool(key: String, default: Boolean) = props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    fun set(key: String, value: Any?) {
        if (value == null) props.remove(key) else props.setProperty(key, value.toString())
    }

    fun save() {
        try {
            dir.mkdirs()
            file.outputStream().use { props.store(it, "WinAmp Classic") }
        } catch (_: Exception) {
        }
    }

    companion object {
        fun defaultDir(): File {
            val os = System.getProperty("os.name", "").lowercase()
            val home = System.getProperty("user.home", ".")
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "WinAmpClassic")
                os.contains("mac") -> File(home, "Library/Application Support/WinAmpClassic")
                else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "winamp-classic")
            }
        }
    }
}
