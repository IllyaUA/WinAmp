package com.illyaua.winamp.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumTest {
    private fun fftWithTone(bin: Int, magnitude: Int, size: Int = 1024): ByteArray {
        val fft = ByteArray(size)
        fft[2 * bin] = magnitude.toByte()
        return fft
    }

    @Test fun silenceGivesZeroBars() {
        val bars = SpectrumMath.bands(ByteArray(1024), 19)
        assertEquals(19, bars.size)
        assertTrue(bars.all { it == 0f })
    }

    @Test fun allValuesStayInRange() {
        val loud = ByteArray(1024) { if (it % 2 == 0) 127 else -128 }
        val bars = SpectrumMath.bands(loud, 19)
        assertTrue(bars.all { it in 0f..1f })
        assertTrue(bars.all { it > 0.9f })
    }

    @Test fun lowToneLightsLeftBarsAndHighToneLightsRightBars() {
        val low = SpectrumMath.bands(fftWithTone(3, 100), 19)
        val high = SpectrumMath.bands(fftWithTone(300, 100), 19)
        val lowPeak = low.indices.maxByOrNull { low[it] }!!
        val highPeak = high.indices.maxByOrNull { high[it] }!!
        assertTrue("low tone at band $lowPeak", lowPeak < 6)
        assertTrue("high tone at band $highPeak", highPeak > 12)
        assertTrue(low.count { it > 0f } == 1)
    }

    @Test fun everyBinBelowTheCutoffBelongsToSomeBand() {
        for (bin in 1 until 380) {
            val bars = SpectrumMath.bands(fftWithTone(bin, 100), 19)
            assertTrue("bin $bin not shown", bars.any { it > 0f })
        }
    }

    @Test fun tinyOrEmptyInputDoesNotCrash() {
        assertEquals(19, SpectrumMath.bands(ByteArray(0), 19).size)
        assertEquals(19, SpectrumMath.bands(ByteArray(6), 19).size)
        assertEquals(0, SpectrumMath.bands(ByteArray(1024), 0).size)
        assertEquals(19, SpectrumMath.bands(ByteArray(16) { 50 }, 19).size)
    }

    @Test fun smootherFallsSlowlyAndPeaksFallSlower() {
        val s = SpectrumSmoother(2, barFall = 0.1f, peakFall = 0.02f)
        s.push(floatArrayOf(1f, 0.5f))
        val f = s.push(floatArrayOf(0f, 0f))
        assertEquals(0.9f, f.bars[0], 1e-6f)
        assertEquals(0.4f, f.bars[1], 1e-6f)
        assertEquals(0.98f, f.peaks[0], 1e-6f)
        assertTrue(f.peaks[0] >= f.bars[0])
    }

    @Test fun smootherAttackIsInstantAndResetClears() {
        val s = SpectrumSmoother(2)
        assertArrayEquals(floatArrayOf(0.7f, 0f), s.push(floatArrayOf(0.7f, 0f)).bars, 0f)
        val cleared = s.reset()
        assertArrayEquals(floatArrayOf(0f, 0f), cleared.bars, 0f)
        assertArrayEquals(floatArrayOf(0f, 0f), cleared.peaks, 0f)
    }
}
