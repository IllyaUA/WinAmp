package com.illyaua.winamp.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.illyaua.winamp.core.Track
import com.illyaua.winamp.core.TrackKind
import java.io.File

/** Turns picked files into [Track]s and fills in tags, duration and format details. */
object TrackLibrary {
    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
                    }
            } catch (_: Exception) {
                // fall through to the path-based name
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }

    /** A track built from the file name alone. Cheap, so the playlist can show up before tags are read. */
    fun quickTrack(uri: Uri, fileName: String): Track = Track(
        uri = uri.toString(),
        title = fileName.substringBeforeLast('.', fileName),
        kind = TrackKind.fromName(fileName),
    )

    fun quickTrack(file: File): Track = quickTrack(Uri.fromFile(file), file.name)

    /** Reads tags and stream info. Slow: call off the main thread. Missing data keeps the values from [base]. */
    fun readMetadata(context: Context, base: Track): Track {
        val uri = Uri.parse(base.uri)
        var title = base.title
        var artist = base.artist
        var durationMs = base.durationMs
        var bitrateKbps = base.bitrateKbps
        var sampleRate = base.sampleRateHz
        var channels = base.channels

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()?.takeIf { it.isNotEmpty() }?.let { title = it }
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                ?.trim()?.takeIf { it.isNotEmpty() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 }?.let { durationMs = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()?.takeIf { it > 0 }?.let { bitrateKbps = it / 1000 }
        } catch (_: Exception) {
            // unreadable tags are not an error, the file name stays as the title
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        if (base.kind != TrackKind.MIDI) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                if (extractor.trackCount > 0) {
                    val format = extractor.getTrackFormat(0)
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    extractor.release()
                } catch (_: Exception) {
                }
            }
        }

        return base.copy(
            title = title,
            artist = artist,
            durationMs = durationMs,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRate,
            channels = channels,
        )
    }
}
