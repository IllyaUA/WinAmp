package com.illyaua.winamp.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {
    @Test fun roundTrip() {
        val text = M3u.write(listOf(M3u.Entry("/m/a.mp3", "Band - A", 185), M3u.Entry("/m/b.wav", null, 0)))
        val back = M3u.parse(text, null)
        assertEquals(2, back.size)
        assertEquals("/m/a.mp3", back[0].path)
        assertEquals("Band - A", back[0].title)
        assertEquals(185, back[0].seconds)
        assertEquals("b", back[1].title)
    }

    @Test fun relativePathsUseTheBaseFolderAndJunkIsIgnored() {
        val text = "﻿#EXTM3U\n\n#EXTINF:12,T\nsub/x.mid\n# a comment\n  /abs/y.mp3  \n#EXTINF:abc\nz.mp3\n"
        val entries = M3u.parse(text, File("/base"))
        assertEquals(listOf(File("/base/sub/x.mid").path, File("/abs/y.mp3").path, File("/base/z.mp3").path), entries.map { it.path })
        assertEquals("T", entries[0].title)
        assertNull(entries[1].title)
        assertEquals(0, entries[2].seconds)
    }
}
