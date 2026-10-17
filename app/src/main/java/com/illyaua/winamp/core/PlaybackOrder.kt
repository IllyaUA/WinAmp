package com.illyaua.winamp.core

import kotlin.random.Random

/**
 * Decides which playlist index comes next or previous, for plain, repeat and shuffle modes.
 * The player owns the current index; this class only keeps shuffle bookkeeping and history.
 */
class PlaybackOrder(private val random: Random = Random.Default) {

    var shuffle: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                reset()
            }
        }

    var repeat: Boolean = false

    private val history = ArrayList<Int>()
    private val played = HashSet<Int>()

    /** Forget history. Call when tracks are removed, since indices shift. */
    fun reset() {
        history.clear()
        played.clear()
    }

    /** Index to play after [current], or null at the end of the list. Records [current] in history on success. */
    fun next(current: Int, size: Int): Int? {
        if (size <= 0) return null
        val target = if (shuffle) pickShuffled(current, size) else pickSequential(current, size)
        if (target != null) leave(current)
        return target
    }

    /** Record that playback is leaving [current] for a track the user picked, so Previous can return to it. */
    fun leave(current: Int) {
        if (current < 0) return
        played.add(current)
        history.add(current)
        if (history.size > MAX_HISTORY) history.removeAt(0)
    }

    /** Index to go back to: most recent history entry, else the previous row in sequential mode. */
    fun previous(current: Int, size: Int): Int? {
        while (history.isNotEmpty()) {
            val index = history.removeAt(history.lastIndex)
            if (index in 0 until size) return index
        }
        if (shuffle || size <= 0) return null
        return when {
            current > 0 -> current - 1
            repeat -> size - 1
            else -> null
        }
    }

    private fun pickSequential(current: Int, size: Int): Int? = when {
        current < 0 -> 0
        current + 1 < size -> current + 1
        repeat -> 0
        else -> null
    }

    private fun pickShuffled(current: Int, size: Int): Int? {
        if (current >= 0) played.add(current)
        var candidates = (0 until size).filter { it !in played }
        if (candidates.isEmpty()) {
            if (!repeat) return null
            played.clear()
            if (current >= 0) played.add(current)
            candidates = (0 until size).filter { it !in played }
            if (candidates.isEmpty()) return current.coerceAtLeast(0)
        }
        return candidates[random.nextInt(candidates.size)]
    }

    private companion object {
        const val MAX_HISTORY = 500
    }
}
