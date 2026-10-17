package com.illyaua.winamp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test fun mmss() {
        assertEquals("0:00", TimeFormat.mmss(0))
        assertEquals("3:07", TimeFormat.mmss(187_999))
        assertEquals("0:00", TimeFormat.mmss(-5))
        assertEquals("75:00", TimeFormat.mmss(75 * 60_000L))
    }

    @Test fun total() {
        assertEquals("59:59", TimeFormat.total(3_599_000))
        assertEquals("1:00:00", TimeFormat.total(3_600_000))
        assertEquals("2:03:04", TimeFormat.total((2 * 3600 + 3 * 60 + 4) * 1000L))
    }

    @Test fun lcdPadsAndCaps() {
        assertEquals("0000", TimeFormat.lcd(0))
        assertEquals("0307", TimeFormat.lcd(187_000))
        assertEquals("9959", TimeFormat.lcd(10_000 * 60_000L))
    }
}
