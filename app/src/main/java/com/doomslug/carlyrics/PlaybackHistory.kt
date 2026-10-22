package com.doomslug.carlyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val video: KaraokeVideo, val openedAt: Long, val positionMs: Long = 0, val durationMs: Long = 0)

/** Local recent videos only. Morphe owns its account and server-side watch history. */
class PlaybackHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback_history", Context.MODE_PRIVATE)
    fun all(): List<HistoryEntry> = synchronized(lock) {
        val rows = runCatching { JSONArray(prefs.getString("videos", "[]")) }.getOrDefault(JSONArray())
        (0 until rows.length()).mapNotNull { index -> runCatching {
            val row = rows.getJSONObject(index)
            val video = KaraokeVideo(row.getString("id"), row.getString("title"))
            if (!KaraokeVideo.ID.matches(video.id) || video.title.isBlank()) null else HistoryEntry(video,
                row.optLong("openedAt"), row.optLong("positionMs").coerceAtLeast(0), row.optLong("durationMs").coerceAtLeast(0))
        }.getOrNull() }.distinctBy { it.video.id }.take(LIMIT)
    }
    fun opened(video: KaraokeVideo, positionMs: Long = 0, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!KaraokeVideo.ID.matches(video.id) || video.title.isBlank()) return@synchronized
        val previous = all()
        write(listOf(HistoryEntry(video, now, positionMs.coerceAtLeast(0), previous.firstOrNull { it.video.id == video.id }?.durationMs ?: 0)) + previous.filterNot { it.video.id == video.id })
    }
    /** Progress does not move an older paused video above a newly selected video. */
    fun checkpoint(video: KaraokeVideo, timeline: VideoTimeline?, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!KaraokeVideo.ID.matches(video.id) || video.title.isBlank()) return@synchronized
        val entries = all()
        val previous = entries.firstOrNull { it.video.id == video.id }
        val duration = timeline?.durationMs?.takeIf { it > 0 } ?: previous?.durationMs ?: 0
        val position = (timeline?.takeIf { it.durationMs > 0 || it.positionMs > 0 }?.positionMs ?: previous?.positionMs ?: 0)
            .coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        val updated = HistoryEntry(video, previous?.openedAt ?: now, position, duration)
        if (previous == null) write(listOf(updated) + entries)
        else if (previous != updated) write(entries.map { if (it.video.id == video.id) updated else it })
    }
    fun remove(id: String) = synchronized(lock) { write(all().filterNot { it.video.id == id }) }
    fun clear() = synchronized(lock) { write(emptyList()) }
    private fun write(entries: List<HistoryEntry>) {
        val rows = JSONArray()
        entries.take(LIMIT).forEach { row -> rows.put(JSONObject().put("id", row.video.id).put("title", row.video.title)
            .put("openedAt", row.openedAt).put("positionMs", row.positionMs).put("durationMs", row.durationMs)) }
        prefs.edit().putString("videos", rows.toString()).apply()
    }
    companion object { private val lock = Any(); const val LIMIT = 200 }
}
