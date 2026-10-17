package com.illyaua.winamp.core

import java.io.File

/** Reads and writes extended M3U playlists. */
object M3u {
    class Entry(val path: String, val title: String?, val seconds: Int)

    fun write(entries: List<Entry>): String = buildString {
        append("#EXTM3U\n")
        for (e in entries) {
            append("#EXTINF:").append(e.seconds).append(',').append(e.title ?: File(e.path).nameWithoutExtension).append('\n')
            append(e.path).append('\n')
        }
    }

    /** Relative paths are resolved against [baseDir]. Comment lines and blanks are ignored. */
    fun parse(text: String, baseDir: File?): List<Entry> {
        val out = ArrayList<Entry>()
        var title: String? = null
        var seconds = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("﻿")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    seconds = body.substringBefore(',').trim().toIntOrNull() ?: 0
                    title = body.substringAfter(',', "").trim().ifEmpty { null }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val f = File(line)
                    val resolved = if (f.isAbsolute || baseDir == null) f else File(baseDir, line)
                    out += Entry(resolved.path, title, seconds)
                    title = null
                    seconds = 0
                }
            }
        }
        return out
    }
}
