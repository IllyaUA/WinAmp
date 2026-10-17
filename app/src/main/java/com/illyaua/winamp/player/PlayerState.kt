package com.illyaua.winamp.player

import com.illyaua.winamp.core.Track

enum class PlayStatus { STOPPED, PLAYING, PAUSED }

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val centerFreqsHz: List<Int> = emptyList(),
    /** Band gains in millibels, one per band. */
    val levels: List<Int> = emptyList(),
    val presetNames: List<String> = emptyList(),
)

/** Everything the UI draws except the playback position and spectrum, which change much more often. */
data class PlayerState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val status: PlayStatus = PlayStatus.STOPPED,
    val durationMs: Int = 0,
    val volume: Float = 0.8f,
    /** -1 is full left, +1 full right. */
    val balance: Float = 0f,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
    val eq: EqState = EqState(),
    val error: String? = null,
) {
    val current: Track? get() = tracks.getOrNull(currentIndex)
}
