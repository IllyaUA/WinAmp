package com.illyaua.winamp.player

import android.content.Context
import com.illyaua.winamp.core.Track
import com.illyaua.winamp.core.TrackKind
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SavedPlayer(
    val tracks: List<Track>,
    val index: Int,
    val volume: Float,
    val balance: Float,
    val shuffle: Boolean,
    val repeat: Boolean,
    val eqEnabled: Boolean,
    val eqLevels: List<Int>,
)

/** Keeps the playlist, current track and settings in SharedPreferences between launches. */
class PlayerStore(context: Context) {
    private val prefs = context.getSharedPreferences("winamp", Context.MODE_PRIVATE)

    fun load(): SavedPlayer = SavedPlayer(
        tracks = readTracks(prefs.getString(KEY_PLAYLIST, null)),
        index = prefs.getInt(KEY_INDEX, -1),
        volume = prefs.getFloat(KEY_VOLUME, 0.8f),
        balance = prefs.getFloat(KEY_BALANCE, 0f),
        shuffle = prefs.getBoolean(KEY_SHUFFLE, false),
        repeat = prefs.getBoolean(KEY_REPEAT, false),
        eqEnabled = prefs.getBoolean(KEY_EQ_ON, false),
        eqLevels = prefs.getString(KEY_EQ_LEVELS, null)
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?: emptyList(),
    )

    fun save(state: PlayerState) {
        prefs.edit()
            .putString(KEY_PLAYLIST, writeTracks(state.tracks))
            .putInt(KEY_INDEX, state.currentIndex)
            .putFloat(KEY_VOLUME, state.volume)
            .putFloat(KEY_BALANCE, state.balance)
            .putBoolean(KEY_SHUFFLE, state.shuffle)
            .putBoolean(KEY_REPEAT, state.repeat)
            .putBoolean(KEY_EQ_ON, state.eq.enabled)
            .putString(KEY_EQ_LEVELS, state.eq.levels.joinToString(","))
            .apply()
    }

    private fun writeTracks(tracks: List<Track>): String {
        val array = JSONArray()
        for (t in tracks) {
            array.put(
                JSONObject()
                    .put("u", t.uri)
                    .put("t", t.title)
                    .put("a", t.artist ?: JSONObject.NULL)
                    .put("d", t.durationMs)
                    .put("b", t.bitrateKbps)
                    .put("r", t.sampleRateHz)
                    .put("c", t.channels)
                    .put("k", t.kind.name)
            )
        }
        return array.toString()
    }

    private fun readTracks(json: String?): List<Track> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("u").ifEmpty { return@mapNotNull null }
                Track(
                    uri = uri,
                    title = o.optString("t", uri),
                    artist = if (o.isNull("a")) null else o.optString("a"),
                    durationMs = o.optLong("d", 0L),
                    bitrateKbps = o.optInt("b", 0),
                    sampleRateHz = o.optInt("r", 0),
                    channels = o.optInt("c", 0),
                    kind = runCatching { TrackKind.valueOf(o.optString("k")) }.getOrDefault(TrackKind.OTHER),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_PLAYLIST = "playlist"
        const val KEY_INDEX = "index"
        const val KEY_VOLUME = "volume"
        const val KEY_BALANCE = "balance"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_EQ_ON = "eq_on"
        const val KEY_EQ_LEVELS = "eq_levels"
    }
}
