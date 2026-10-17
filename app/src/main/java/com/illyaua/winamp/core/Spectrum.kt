package com.illyaua.winamp.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

class SpectrumFrame(val bars: FloatArray, val peaks: FloatArray) {
    companion object {
        fun empty(bandCount: Int) = SpectrumFrame(FloatArray(bandCount), FloatArray(bandCount))
    }
}

object SpectrumMath {
    // dB value that maps to a full-height bar
    private const val FULL_SCALE_DB = 38f

    /**
     * Folds the byte array delivered by android.media.audiofx.Visualizer into [bandCount] log-spaced bars (0..1).
     * Layout of [fft]: index 0 and 1 hold the DC and Nyquist real parts, then (real, imaginary) pairs per bin.
     */
    fun bands(fft: ByteArray, bandCount: Int): FloatArray {
        val out = FloatArray(bandCount)
        val bins = fft.size / 2
        if (bandCount <= 0 || bins < 4) return out

        // Skip the top quarter of the spectrum; bars up there stay empty on most music.
        val upper = (bins * 0.75f).toInt().coerceIn(2, bins)
        val logUpper = ln(upper.toFloat())
        val edges = IntArray(bandCount + 1)
        edges[0] = 1
        for (i in 1..bandCount) {
            edges[i] = maxOf(exp(logUpper * i / bandCount).toInt(), edges[i - 1] + 1)
        }

        for (band in 0 until bandCount) {
            val from = edges[band].coerceAtMost(bins - 1)
            val to = maxOf(edges[band + 1].coerceAtMost(bins), from + 1)
            var peak = 0f
            for (k in from until to) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                peak = max(peak, sqrt(re * re + im * im))
            }
            out[band] = (20f * log10(1f + peak) / FULL_SCALE_DB).coerceIn(0f, 1f)
        }
        return out
    }
}

/** Gives bars a quick attack and a slow fall, and keeps a peak marker that drops more slowly still. */
class SpectrumSmoother(
    private val bandCount: Int,
    private val barFall: Float = 0.06f,
    private val peakFall: Float = 0.015f,
) {
    private val bars = FloatArray(bandCount)
    private val peaks = FloatArray(bandCount)

    @Synchronized
    fun push(raw: FloatArray): SpectrumFrame {
        for (i in 0 until bandCount) {
            val value = if (i < raw.size) raw[i] else 0f
            bars[i] = max(value, bars[i] - barFall)
            peaks[i] = max(bars[i], peaks[i] - peakFall)
        }
        return SpectrumFrame(bars.copyOf(), peaks.copyOf())
    }

    @Synchronized
    fun reset(): SpectrumFrame {
        bars.fill(0f)
        peaks.fill(0f)
        return SpectrumFrame(bars.copyOf(), peaks.copyOf())
    }
}
