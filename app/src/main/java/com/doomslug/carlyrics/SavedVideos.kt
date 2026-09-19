package com.doomslug.carlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small local list for videos that may disappear from the channel's recent feed. */
class SavedVideos(context: Context) {
    private val prefs = context.getSharedPreferences("saved_videos", Context.MODE_PRIVATE)

    fun all(): List<KaraokeVideo> {
        val json = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val title = item.optString("title")
            if (KaraokeVideo.ID.matches(id) && title.isNotBlank()) KaraokeVideo(id, title) else null
        }
    }

    fun contains(id: String): Boolean = all().any { it.id == id }

    fun toggle(video: KaraokeVideo): Boolean {
        val updated = all().toMutableList()
        val existing = updated.indexOfFirst { it.id == video.id }
        val saved = existing < 0
        if (saved) updated.add(0, video) else updated.removeAt(existing)
        val json = JSONArray()
        updated.forEach { json.put(JSONObject().put("id", it.id).put("title", it.title)) }
        prefs.edit().putString("items", json.toString()).apply()
        return saved
    }
}
