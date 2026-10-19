package com.doomslug.carlyrics

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SpotifyMatch(val song: SpotifySong, val video: KaraokeVideo? = null,
                        val state: String = "pending", val checkedAt: Long = 0)
data class SpotifyPlaylist(val id: String, val name: String, val total: Int,
                           val songs: List<SpotifyMatch> = emptyList(), val problem: String? = null) {
    val matched get() = songs.count { it.video != null }
    val summary get() = problem?.let { if (songs.isEmpty()) it else "$matched matched · $it" } ?: "$matched of $total matched"
    val albumName get() = "$name · Spotify [${id.takeLast(6)}]"
}

/** One atomic file per import: progress never rewrites every other playlist or a handmade album. */
class SpotifyImportStore(context: Context) {
    private val prefs = context.getSharedPreferences("karaoke_playlists", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "spotify_playlists").apply { mkdirs() }
    fun all(): List<SpotifyPlaylist> = synchronized(lock) {
        // Migrate the initial development format without changing local album storage.
        val legacy = prefs.getString("spotify", "")?.takeIf { it.startsWith("[") }
        if (legacy != null) {
            val old = runCatching { JSONArray(legacy) }.getOrDefault(JSONArray())
            (0 until old.length()).forEach { runCatching { put(decode(old.getJSONObject(it))) } }
            prefs.edit().putString("spotify", System.nanoTime().toString()).apply()
        }
        directory.listFiles().orEmpty().filter { it.name.endsWith(".json") }.sortedBy { it.name }.mapNotNull {
            runCatching { decode(JSONObject(AtomicFile(it).openRead().bufferedReader().use { r -> r.readText() })) }.getOrNull()
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }
    fun put(playlist: SpotifyPlaylist) = synchronized(lock) {
        require(Regex("[A-Za-z0-9:_-]{1,100}").matches(playlist.id))
        val atomic = AtomicFile(File(directory, "${playlist.id}.json"))
        val stream = atomic.startWrite()
        try { stream.write(encode(playlist).toString().toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
        prefs.edit().putString("spotify", System.nanoTime().toString()).apply()
    }
    fun albums() = all().map { p -> KaraokePlaylist(p.albumName,
        p.songs.mapNotNull { it.video }.distinctBy { it.id }, p.id, p.summary) }
    fun choose(playlistId: String, songKey: String, video: KaraokeVideo): Boolean = synchronized(lock) {
        require(KaraokeVideo.ID.matches(video.id))
        val p = all().firstOrNull { it.id == playlistId } ?: return false
        if (p.songs.none { it.song.key == songKey }) return false
        put(p.copy(songs = p.songs.map { if (it.song.key == songKey)
            it.copy(video = video, state = "manual", checkedAt = System.currentTimeMillis()) else it }))
        true
    }
    private fun decode(p: JSONObject): SpotifyPlaylist {
        val rows = p.optJSONArray("songs") ?: JSONArray()
        return SpotifyPlaylist(p.getString("id"), p.getString("name"), p.getInt("total"),
            (0 until rows.length()).map { j ->
                val row = rows.getJSONObject(j); val artists = row.getJSONArray("artists")
                val video = row.optJSONObject("video")?.let {
                    KaraokeVideo(it.getString("id"), it.getString("title")).takeIf { v -> KaraokeVideo.ID.matches(v.id) }
                }
                SpotifyMatch(SpotifySong(row.optString("id"), row.getString("title"),
                    (0 until artists.length()).map { artists.getString(it) }), video,
                    row.optString("state", "pending"), row.optLong("checkedAt"))
            }, p.optString("problem").takeIf { it.isNotBlank() })
    }
    private fun encode(p: SpotifyPlaylist): JSONObject {
        val rows = JSONArray()
        p.songs.forEach { row ->
            rows.put(JSONObject().put("id", row.song.id).put("title", row.song.title)
                .put("artists", JSONArray(row.song.artists)).put("state", row.state).put("checkedAt", row.checkedAt)
                .apply { row.video?.let { put("video", JSONObject().put("id", it.id).put("title", it.title)) } })
        }
        return JSONObject().put("id", p.id).put("name", p.name).put("total", p.total)
            .put("songs", rows).put("problem", p.problem.orEmpty())
    }
    companion object { private val lock = Any() }
}
