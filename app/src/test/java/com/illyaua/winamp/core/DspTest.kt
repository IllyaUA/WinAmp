package com.illyaua.winamp.core

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DspTest {
    private fun sine(freq: Float, rate: Float, n: Int, amp: Float = 1f) =
        FloatArray(n) { (amp * sin(2.0 * PI * freq * it / rate)).toFloat() }

    @Test fun fftFindsTheToneBin() {
        val fft = Fft(1024)
        val rate = 44100f
        val freq = 100 * rate / 1024 // exactly bin 100
        val mags = fft.magnitudes(sine(freq, rate, 1024))
        val peak = mags.indices.maxByOrNull { mags[it] }!!
        assertEquals(100, peak)
        assertEquals(1f, mags[peak], 0.15f)
        assertTrue(mags[300] < 0.01f)
    }

    @Test fun fftOfSilenceIsZero() {
        assertTrue(Fft(256).magnitudes(FloatArray(256)).all { it == 0f })
    }

    @Test fun barsPlaceLowAndHighTonesApart() {
        val fft = Fft(1024)
        val rate = 44100f
        val low = SpectrumBars.fromMagnitudes(fft.magnitudes(sine(150f, rate, 1024)), 19, rate)
        val high = SpectrumBars.fromMagnitudes(fft.magnitudes(sine(9000f, rate, 1024)), 19, rate)
        val lo = low.indices.maxByOrNull { low[it] }!!
        val hi = high.indices.maxByOrNull { high[it] }!!
        assertTrue("low tone in bar $lo", lo <= 5)
        assertTrue("high tone in bar $hi", hi >= 13)
        assertTrue(low.all { it in 0f..1f } && high.all { it in 0f..1f })
        assertTrue(low[lo] > 0.5f)
    }

    @Test fun barsOfSilenceAreEmpty() {
        assertTrue(SpectrumBars.fromMagnitudes(FloatArray(512), 19, 44100f).all { it == 0f })
    }

    private fun rms(a: FloatArray, from: Int) = sqrt(a.drop(from).sumOf { (it * it).toDouble() } / (a.size - from)).toFloat()

    private fun gainDb(eq: Equalizer10, freq: Float, rate: Float = 44100f): Float {
        val n = 44100
        val x = sine(freq, rate, n, 0.25f)
        val y = x.copyOf()
        eq.process(y, n)
        return 20f * log10(rms(y, 8000) / rms(x, 8000))
    }

    @Test fun flatEqualizerIsTransparent() {
        val eq = Equalizer10(44100f, 1)
        assertEquals(0f, gainDb(eq, 1000f), 0.01f)
    }

    @Test fun bandBoostsItsOwnFrequencyMostAndLeavesFarAwayAlone() {
        val eq = Equalizer10(44100f, 1)
        eq.setBand(4, 12f) // 1 kHz
        assertEquals(12f, gainDb(Equalizer10(44100f, 1).also { it.setBand(4, 12f) }, 1000f), 0.6f)
        assertEquals(0f, gainDb(Equalizer10(44100f, 1).also { it.setBand(4, 12f) }, 60f), 0.8f)
        assertTrue(gainDb(eq, 1500f) > 3f)
    }

    @Test fun bandCutsAndPreampScales() {
        assertEquals(-12f, gainDb(Equalizer10(44100f, 1).also { it.setBand(1, -12f) }, 170f), 0.6f)
        assertEquals(6f, gainDb(Equalizer10(44100f, 1).also { it.setPreamp(6f) }, 500f), 0.1f)
    }

    @Test fun disabledEqualizerDoesNothingAndStereoChannelsStayIndependent() {
        val off = Equalizer10(44100f, 1).also { it.setBand(4, 12f); it.enabled = false }
        assertEquals(0f, gainDb(off, 1000f), 0.01f)

        val eq = Equalizer10(44100f, 2).also { it.setBand(4, 12f) }
        val n = 20000
        val left = sine(1000f, 44100f, n, 0.25f)
        val buf = FloatArray(n * 2)
        for (i in 0 until n) buf[i * 2] = left[i] // right stays silent
        eq.process(buf, n)
        assertTrue(buf.indices.filter { it % 2 == 1 }.all { buf[it] == 0f })
        assertTrue(buf.indices.filter { it % 2 == 0 }.any { kotlin.math.abs(buf[it]) > 0.3f })
    }

    @Test fun highBandsAreSkippedAtLowSampleRates() {
        val eq = Equalizer10(8000f, 1)
        eq.setBand(9, 12f) // 16 kHz is above Nyquist
        assertEquals(0f, gainDb(eq, 1000f, 8000f), 0.01f)
    }
}
