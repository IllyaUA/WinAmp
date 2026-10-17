package com.illyaua.winamp.core

enum class TrackKind {
    MP3, WAV, MIDI, OTHER;

    companion object {
        private val MP3_EXT = setOf("mp3", "mp2")
        private val WAV_EXT = setOf("wav", "wave")
        private val MIDI_EXT = setOf("mid", "midi", "kar")
        private val OTHER_EXT = setOf("ogg", "oga", "flac", "m4a", "aac", "opus")

        private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

        fun fromName(name: String): TrackKind {
            val ext = extensionOf(name)
            return when (ext) {
                in MP3_EXT -> MP3
                in WAV_EXT -> WAV
                in MIDI_EXT -> MIDI
                else -> OTHER
            }
        }

        /** True for files the folder scanner should pick up. */
        fun isSupported(name: String): Boolean {
            val ext = extensionOf(name)
            return ext in MP3_EXT || ext in WAV_EXT || ext in MIDI_EXT || ext in OTHER_EXT
        }
    }
}

/**
 * One playlist entry. [uri] is kept as a String so this class stays free of Android types.
 * Zero means "unknown" for the numeric fields.
 */
data class Track(
    val uri: String,
    val title: String,
    val artist: String? = null,
    val durationMs: Long = 0L,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val channels: Int = 0,
    val kind: TrackKind = TrackKind.OTHER,
) {
    val displayName: String
        get() = if (artist.isNullOrBlank()) title else "$artist - $title"
}
