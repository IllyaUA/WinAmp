package com.illyaua.winamp.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SevenSegmentTest {
    @Test fun eightLightsEverySegment() {
        assertEquals(127, SevenSegment.mask('8'))
    }

    @Test fun everyDigitIsDistinct() {
        val masks = ('0'..'9').map { SevenSegment.mask(it) }
        assertEquals(10, masks.toSet().size)
        masks.forEach { assertNotEquals(0, it) }
    }

    @Test fun minusIsMiddleBarOnly() {
        assertEquals(SevenSegment.G, SevenSegment.mask('-'))
        assertEquals(0, SevenSegment.mask(' '))
    }
}
