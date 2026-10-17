package com.illyaua.winamp.core

import java.util.Locale

object TimeFormat {
    private const val LCD_MAX_SECONDS = 99 * 60 + 59

    /** "3:07", used in the playlist rows. */
    fun mmss(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000
        return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60)
    }

    /** "1:02:03" once past an hour, otherwise same as [mmss]. */
    fun total(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000
        val h = total / 3600
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, (total / 60) % 60, total % 60)
        } else {
            mmss(ms)
        }
    }

    /** Four digits "MMSS" for the seven-segment clock, minutes capped at 99. */
    fun lcd(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 1000).coerceAtMost(LCD_MAX_SECONDS.toLong())
        return String.format(Locale.ROOT, "%02d%02d", total / 60, total % 60)
    }
}
