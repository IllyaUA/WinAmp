package com.illyaua.winamp.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOrderTest {
    private fun order(shuffle: Boolean = false, repeat: Boolean = false) =
        PlaybackOrder(Random(42)).also { it.shuffle = shuffle; it.repeat = repeat }

    @Test fun sequentialWalksToTheEndThenStops() {
        val o = order()
        assertEquals(0, o.next(-1, 3))
        assertEquals(1, o.next(0, 3))
        assertEquals(2, o.next(1, 3))
        assertNull(o.next(2, 3))
    }

    @Test fun sequentialWrapsWithRepeat() {
        val o = order(repeat = true)
        assertEquals(0, o.next(2, 3))
    }

    @Test fun singleTrackWithRepeatRestartsIt() {
        assertEquals(0, order(repeat = true).next(0, 1))
        assertNull(order().next(0, 1))
    }

    @Test fun emptyPlaylistHasNoNext() {
        assertNull(order().next(-1, 0))
        assertNull(order(shuffle = true).next(-1, 0))
    }

    @Test fun previousFollowsHistoryAndSkipsStaleIndices() {
        val o = order()
        o.next(0, 5)     // 0 -> 1
        o.next(1, 5)     // 1 -> 2
        assertEquals(1, o.previous(2, 5))
        assertEquals(0, o.previous(1, 5))
        // history exhausted: falls back to the row above, then stops at the top
        assertEquals(2, o.previous(3, 5))
        assertNull(o.previous(0, 5))
    }

    @Test fun previousIgnoresHistoryEntriesPastTheEndOfAShrunkList() {
        val o = order()
        o.leave(9)
        o.leave(1)
        assertEquals(1, o.previous(0, 3))
        // 9 no longer exists in a 3-track list, so it is skipped and the sequential fallback is used
        assertNull(o.previous(0, 3))
    }

    @Test fun previousWrapsWithRepeatAtTop() {
        assertEquals(4, order(repeat = true).previous(0, 5))
    }

    @Test fun shuffleVisitsEveryTrackOnceBeforeEnding() {
        val o = order(shuffle = true)
        val seen = mutableListOf(0)
        var current = 0
        while (true) {
            current = o.next(current, 6) ?: break
            seen += current
        }
        assertEquals(6, seen.size)
        assertEquals((0 until 6).toSet(), seen.toSet())
    }

    @Test fun shuffleWithRepeatKeepsGoingAndNeverRepeatsImmediately() {
        val o = order(shuffle = true, repeat = true)
        var current = 0
        repeat(50) {
            val next = o.next(current, 4)!!
            assertTrue(next in 0 until 4)
            assertTrue("same track twice in a row", next != current)
            current = next
        }
    }

    @Test fun shuffleWithRepeatOnSingleTrack() {
        assertEquals(0, order(shuffle = true, repeat = true).next(0, 1))
    }

    @Test fun togglingShuffleClearsHistory() {
        val o = order()
        o.next(0, 5)
        o.shuffle = true
        o.shuffle = false
        // history was cleared, so Previous uses the sequential fallback
        assertEquals(2, o.previous(3, 5))
    }
}
