package com.doomslug.carlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class KaraokePlaylist(val name: String, val videos: List<KaraokeVideo>)

/** Small local playlist store. The fixed starter lists keep creation knob-friendly in the car. */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("karaoke_playlists", Context.MODE_PRIVATE)
    private val starterNames = listOf("My karaoke mix", "Warm-up set", "Duets night")

    fun all(): List<KaraokePlaylist> {
        val root = runCatching { JSONArray(prefs.getString("lists", "[]")) }.getOrDefault(JSONArray())
        val stored = (0 until root.length()).mapNotNull { index ->
            val item = root.optJSONObject(index) ?: return@mapNotNull null
            KaraokePlaylist(item.optString("name"), readVideos(item.optJSONArray("videos") ?: JSONArray()))
        }.filter { it.name.isNotBlank() }
        return starterNames.map { name -> stored.firstOrNull { it.name == name } ?: KaraokePlaylist(name, emptyList()) }
            .plus(stored.filter { it.name !in starterNames })
    }

    fun contains(name: String, id: String): Boolean = all().firstOrNull { it.name == name }?.videos?.any { it.id == id } == true

    fun toggle(name: String, video: KaraokeVideo): Boolean {
        val lists = all().toMutableList()
        val index = lists.indexOfFirst { it.name == name }
        val current = lists.getOrNull(index)?.videos?.toMutableList() ?: mutableListOf()
        val existing = current.indexOfFirst { it.id == video.id }
        val added = existing < 0
        if (added) current.add(video) else current.removeAt(existing)
        if (index >= 0) lists[index] = KaraokePlaylist(name, current) else lists.add(KaraokePlaylist(name, current))
        write(lists)
        return added
    }

    private fun readVideos(json: JSONArray) = (0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        val title = item.optString("title")
        if (KaraokeVideo.ID.matches(id) && title.isNotBlank()) KaraokeVideo(id, title) else null
    }

    private fun write(lists: List<KaraokePlaylist>) {
        val root = JSONArray()
        lists.forEach { list ->
            val videos = JSONArray()
            list.videos.forEach { videos.put(JSONObject().put("id", it.id).put("title", it.title)) }
            root.put(JSONObject().put("name", list.name).put("videos", videos))
        }
        prefs.edit().putString("lists", root.toString()).apply()
    }
}

class QueueStore(context: Context) {
    private val prefs = context.getSharedPreferences("karaoke_queue", Context.MODE_PRIVATE)

    fun all(): List<KaraokeVideo> {
        val root = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        return (0 until root.length()).mapNotNull { index ->
            val item = root.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val title = item.optString("title")
            if (KaraokeVideo.ID.matches(id) && title.isNotBlank()) KaraokeVideo(id, title) else null
        }.distinctBy { it.id }
    }

    /** Queue is an add operation. A second tap must not silently remove a song. */
    fun add(video: KaraokeVideo): Boolean {
        if (!KaraokeVideo.ID.matches(video.id) || video.title.isBlank()) return false
        val items = all()
        if (items.any { it.id == video.id }) return false
        write(items + video)
        return true
    }

    fun remove(id: String) { write(all().filterNot { it.id == id }) }

    fun move(id: String, delta: Int) {
        val items = all().toMutableList()
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = (index + delta).coerceIn(items.indices)
        if (index == target) return
        items.add(target, items.removeAt(index))
        write(items)
    }

    fun toggle(video: KaraokeVideo): Boolean {
        val updated = all().toMutableList()
        val existing = updated.indexOfFirst { it.id == video.id }
        val added = existing < 0
        if (added) updated.add(video) else updated.removeAt(existing)
        write(updated)
        return added
    }

    private fun write(items: List<KaraokeVideo>) {
        val json = JSONArray()
        items.forEach { json.put(JSONObject().put("id", it.id).put("title", it.title)) }
        prefs.edit().putString("items", json.toString()).apply()
    }
}
