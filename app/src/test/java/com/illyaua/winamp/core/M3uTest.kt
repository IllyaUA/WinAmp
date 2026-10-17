package com.illyaua.winamp.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    @Test fun roundTrip() {
        val a = File("/m/a.mp3").path
        val text = M3u.write(listOf(M3u.Entry(a, "Band - A", 185), M3u.Entry(File("/m/b.wav").path, null, 0)))
        val back = M3u.parse(text, null)
        assertEquals(2, back.size)
        assertEquals(a, back[0].path)
        assertEquals("Band - A", back[0].title)
        assertEquals(185, back[0].seconds)
        assertEquals("b", back[1].title)
    }

    @Test fun relativePathsUseTheBaseFolderAndJunkIsIgnored() {
        // an absolute path on every OS, including Windows where "/abs" has no drive letter
        val abs = File("m3u-abs-test").absoluteFile
        val text = "\uFEFF#EXTM3U\n\n#EXTINF:12,T\nsub/x.mid\n# a comment\n  ${abs.path}  \n#EXTINF:abc\nz.mp3\n"
        val entries = M3u.parse(text, File("/base"))
        assertEquals(listOf(File(File("/base"), "sub/x.mid").path, abs.path, File(File("/base"), "z.mp3").path), entries.map { it.path })
        assertEquals("T", entries[0].title)
        assertNull(entries[1].title)
        assertEquals(0, entries[2].seconds)
    }
}
