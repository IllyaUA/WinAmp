package com.illyaua.winamp.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** In-place radix-2 FFT. [size] must be a power of two. */
class Fft(val size: Int) {
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { -sin(2.0 * PI * it / size).toFloat() }
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2.0 * PI * it / (size - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)

    init {
        require(size >= 4 && size and (size - 1) == 0) { "size must be a power of two" }
    }

    /**
     * Hann-windowed magnitude spectrum of the first [size] samples of [samples].
     * Returns size/2 values scaled so a full-scale sine reads about 1.0.
     */
    fun magnitudes(samples: FloatArray): FloatArray {
        for (i in 0 until size) {
            re[i] = (if (i < samples.size) samples[i] else 0f) * window[i]
            im[i] = 0f
        }
        transform()
        val out = FloatArray(size / 2)
        val scale = 4f / size
        for (k in out.indices) out[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
        return out
    }

    private fun transform() {
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var start = 0
            while (start < size) {
                var k = 0
                for (n in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sinTable[k]
                    val xr = re[n + half] * wr - im[n + half] * wi
                    val xi = re[n + half] * wi + im[n + half] * wr
                    re[n + half] = re[n] - xr
                    im[n + half] = im[n] - xi
                    re[n] += xr
                    im[n] += xi
                    k += step
                }
                start += len
            }
            len = len shl 1
        }
    }
}

object SpectrumBars {
    private const val FULL_SCALE_DB = 48f

    /** Folds FFT magnitudes (as from [Fft.magnitudes]) into [bandCount] log-spaced bars in 0..1. */
    fun fromMagnitudes(mags: FloatArray, bandCount: Int, sampleRate: Float): FloatArray {
        val out = FloatArray(bandCount)
        if (mags.size < 8 || bandCount <= 0) return out
        val binHz = sampleRate / 2f / mags.size
        val lowBin = max(1, (40f / binHz).toInt())
        val highBin = ((16000f / binHz).toInt()).coerceIn(lowBin + bandCount, mags.size)
        val ratio = (highBin.toFloat() / lowBin).toDouble()
        var from = lowBin
        for (b in 0 until bandCount) {
            val edge = (lowBin * ratio.pow((b + 1).toDouble() / bandCount)).toInt()
            val to = max(from + 1, minOf(edge, highBin))
            var peak = 0f
            for (k in from until to) peak = max(peak, mags[k])
            // magnitude 1.0 = full-scale sine
            val db = 20f * log10(peak * 4f + 1e-4f)
            out[b] = ((db + 36f) / (FULL_SCALE_DB - 36f + 36f)).coerceIn(0f, 1f)
            from = to
        }
        return out
    }
}

/**
 * Winamp-style 10 band graphic equalizer plus preamp, on interleaved float samples.
 * Each band is an RBJ peaking filter; bands above the usable range for the sample rate are skipped.
 */
class Equalizer10(private val sampleRate: Float, private val channels: Int) {
    var enabled: Boolean = true
    private val gainsDb = FloatArray(BANDS) // -12..+12
    private var preampDb = 0f
    private var preampGain = 1f

    // biquad coefficients per band and per-channel state
    private val b0 = FloatArray(BANDS)
    private val b1 = FloatArray(BANDS)
    private val b2 = FloatArray(BANDS)
    private val a1 = FloatArray(BANDS)
    private val a2 = FloatArray(BANDS)
    private val z1 = Array(BANDS) { FloatArray(channels) }
    private val z2 = Array(BANDS) { FloatArray(channels) }
    private val active = BooleanArray(BANDS)

    init {
        for (i in 0 until BANDS) update(i)
    }

    fun setBand(index: Int, db: Float) {
        gainsDb[index] = db.coerceIn(-12f, 12f)
        update(index)
    }

    fun band(index: Int) = gainsDb[index]

    fun setPreamp(db: Float) {
        preampDb = db.coerceIn(-12f, 12f)
        preampGain = 10f.pow(preampDb / 20f)
    }

    private fun update(i: Int) {
        val f0 = FREQUENCIES[i]
        val gain = gainsDb[i]
        active[i] = gain != 0f && f0 < sampleRate * 0.45f
        if (!active[i]) return
        val a = 10.0.pow(gain / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2.0 * Q)
        val cosW = cos(w0)
        val a0 = 1.0 + alpha / a
        b0[i] = ((1.0 + alpha * a) / a0).toFloat()
        b1[i] = ((-2.0 * cosW) / a0).toFloat()
        b2[i] = ((1.0 - alpha * a) / a0).toFloat()
        a1[i] = ((-2.0 * cosW) / a0).toFloat()
        a2[i] = ((1.0 - alpha / a) / a0).toFloat()
    }

    /** Processes [frames] interleaved frames in place. */
    fun process(buf: FloatArray, frames: Int) {
        if (!enabled) return
        for (i in 0 until BANDS) {
            if (!active[i]) continue
            val c0 = b0[i]; val c1 = b1[i]; val c2 = b2[i]; val d1 = a1[i]; val d2 = a2[i]
            for (ch in 0 until channels) {
                var s1 = z1[i][ch]
                var s2 = z2[i][ch]
                var idx = ch
                for (n in 0 until frames) {
                    val x = buf[idx]
                    val y = c0 * x + s1
                    s1 = c1 * x - d1 * y + s2
                    s2 = c2 * x - d2 * y
                    buf[idx] = y
                    idx += channels
                }
                z1[i][ch] = s1
                z2[i][ch] = s2
            }
        }
        if (preampGain != 1f) for (n in 0 until frames * channels) buf[n] *= preampGain
    }

    companion object {
        const val BANDS = 10
        const val Q = 1.2
        val FREQUENCIES = floatArrayOf(60f, 170f, 310f, 600f, 1000f, 3000f, 6000f, 12000f, 14000f, 16000f)
    }
}
