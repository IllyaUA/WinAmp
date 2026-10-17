package com.illyaua.winamp.core

import java.io.File

/** Orders names the way people expect: "2.mp3" before "10.mp3", ignoring case. */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var endA = i
                while (endA < a.length && a[endA].isDigit()) endA++
                var endB = j
                while (endB < b.length && b[endB].isDigit()) endB++
                val numA = a.substring(i, endA).trimStart('0')
                val numB = b.substring(j, endB).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val c = numA.compareTo(numB)
                if (c != 0) return c
                i = endA
                j = endB
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }
}

data class DirEntry(val file: File, val isDirectory: Boolean) {
    val name: String get() = file.name
}

object DirectoryLister {
    const val MAX_SCAN_FILES = 5000
    private const val MAX_SCAN_DEPTH = 12

    /** Sub-folders first, then playable files, each group in natural order. Hidden entries (leading dot) are skipped. */
    fun list(dir: File): List<DirEntry> {
        val children = dir.listFiles() ?: return emptyList()
        val entries = children.mapNotNull { f ->
            if (f.name.startsWith('.')) return@mapNotNull null
            val isDir = f.isDirectory
            if (isDir || TrackKind.isSupported(f.name)) DirEntry(f, isDir) else null
        }
        return entries.sortedWith(
            compareBy<DirEntry> { !it.isDirectory }.thenComparator { a, b -> NaturalOrder.compare(a.name, b.name) }
        )
    }

    /** Every playable file under [root]: a folder's own files first, then its sub-folders, capped at [limit]. */
    fun scanAudio(root: File, limit: Int = MAX_SCAN_FILES): List<File> {
        val out = ArrayList<File>()
        walk(root, out, 0, limit)
        return out
    }

    private fun walk(dir: File, out: MutableList<File>, depth: Int, limit: Int) {
        if (depth > MAX_SCAN_DEPTH || out.size >= limit) return
        val (folders, files) = list(dir).partition { it.isDirectory }
        for (f in files) {
            if (out.size >= limit) return
            out += f.file
        }
        for (d in folders) walk(d.file, out, depth + 1, limit)
    }
}
