package com.doomslug.carlyrics

import android.content.Context
import org.json.JSONObject

/** Additional public provider titles for Spotify matching; media is never downloaded. */
internal object KaraokeMatchingCatalog {
    @Volatile private var saved: KaraokeMatcher.Index? = null
    fun index(context: Context): KaraokeMatcher.Index = saved ?: synchronized(this) {
        saved ?: run {
            val root = context.assets.open("karaoke_matching_library.json").bufferedReader().use { JSONObject(it.readText()) }
            val rows = root.getJSONArray("songs")
            val videos = (0 until rows.length()).mapNotNull { i ->
                val row = rows.getJSONObject(i)
                KaraokeVideo(row.getString("id"), row.getString("title"))
                    .takeIf { KaraokeVideo.ID.matches(it.id) && it.title.isNotBlank() }
            }
            KaraokeMatcher.Index(videos + SingKingCatalog.library + SingKingCatalog.videos).also { saved = it }
        }
    }
}
