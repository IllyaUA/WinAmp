package com.illyaua.winamp.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file(path: String): File {
        val f = File(tmp.root, path)
        f.parentFile.mkdirs()
        f.writeText("x")
        return f
    }

    private fun names(list: List<DirEntry>) = list.map { (if (it.isDirectory) "[" else "") + it.name }

    @Test fun naturalOrderSortsNumbersByValue() {
        val sorted = listOf("10.mp3", "2.mp3", "1.mp3", "b.mp3", "A.mp3", "02 song.mp3").sortedWith(NaturalOrder)
        assertEquals(listOf("1.mp3", "02 song.mp3", "2.mp3", "10.mp3", "A.mp3", "b.mp3"), sorted)
    }

    @Test fun naturalOrderHandlesPrefixesAndEquality() {
        assertTrue(NaturalOrder.compare("track", "track2") < 0)
        assertEquals(0, NaturalOrder.compare("same", "same"))
        assertTrue(NaturalOrder.compare("CD1", "cd2") < 0)
        assertTrue(NaturalOrder.compare("a99999999999999999999", "a100000000000000000000") < 0)
    }

    @Test fun listShowsFoldersFirstThenAudioAndSkipsEverythingElse() {
        file("b.mp3"); file("a.WAV"); file("10.mid"); file("2.mid")
        file("cover.jpg"); file("notes.txt"); file(".hidden.mp3")
        file("Zed/x.mp3"); file("alpha/y.mp3"); File(tmp.root, ".cache").mkdirs()
        assertEquals(
            listOf("[alpha", "[Zed", "2.mid", "10.mid", "a.WAV", "b.mp3"),
            names(DirectoryLister.list(tmp.root)),
        )
    }

    @Test fun listOfMissingOrFileIsEmpty() {
        assertTrue(DirectoryLister.list(File(tmp.root, "nope")).isEmpty())
        assertTrue(DirectoryLister.list(file("one.mp3")).isEmpty())
    }

    @Test fun scanFindsFilesInSubfoldersWithOwnFilesFirst() {
        file("album/02.mp3"); file("album/01.mp3"); file("album/CD2/01.mp3"); file("album/CD1/01.mp3")
        file("album/cover.jpg")
        val found = DirectoryLister.scanAudio(File(tmp.root, "album")).map { it.relativeTo(tmp.root).invariantSeparatorsPath }
        assertEquals(
            listOf("album/01.mp3", "album/02.mp3", "album/CD1/01.mp3", "album/CD2/01.mp3"),
            found,
        )
    }

    @Test fun scanRespectsLimit() {
        for (i in 1..20) file("m/$i.mp3")
        assertEquals(7, DirectoryLister.scanAudio(File(tmp.root, "m"), limit = 7).size)
    }

    @Test fun scanOfEmptyFolderIsEmpty() {
        File(tmp.root, "empty").mkdirs()
        assertTrue(DirectoryLister.scanAudio(File(tmp.root, "empty")).isEmpty())
    }
}
