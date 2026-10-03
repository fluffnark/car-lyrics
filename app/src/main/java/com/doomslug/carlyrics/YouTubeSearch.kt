package com.doomslug.carlyrics

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class VideoSearchResult(val videos: List<KaraokeVideo>, val error: String? = null)

fun interface VideoSearch {
    /** Deliver the result on the main thread. */
    fun search(query: String, done: (VideoSearchResult) -> Unit)
}

/** Public search results only. Playback and authentication remain in Morphe. */
object YouTubeSearch : VideoSearch {
    private val worker = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    override fun search(query: String, done: (VideoSearchResult) -> Unit) {
        val clean = query.trim().take(200)
        if (clean.isBlank()) { done(VideoSearchResult(emptyList())); return }
        worker.execute {
            val result = runCatching {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val connection = (URL("https://www.youtube.com/results?search_query=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    // Mobile pages now encode their initial data as a JS string. Request
                    // the desktop response and parse its JSON, not a cross-video regex.
                    setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131 Safari/537.36")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.8")
                }
                try {
                    check(connection.responseCode == 200)
                    val html = connection.inputStream.bufferedReader().use { reader ->
                        val out = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            out.append(buffer, 0, count)
                            check(out.length <= 4_000_000) { "Search response too large" }
                        }
                        out.toString()
                    }
                    VideoSearchResult(YouTubeSearchParser.parse(html))
                } finally { connection.disconnect() }
            }.getOrElse {
                VideoSearchResult(emptyList(), "YouTube search unavailable. Check your connection and retry.")
            }
            main.post { done(result) }
        }
    }
}

internal object YouTubeSearchParser {
    private val initialData = Regex("(?:var\\s+ytInitialData|window\\[\"ytInitialData\"\\]|window\\['ytInitialData'\\])\\s*=\\s*")

    fun parse(html: String): List<KaraokeVideo> {
        val match = initialData.find(html) ?: error("Search data missing")
        val root = JSONTokener(html.substring(match.range.last + 1)).nextValue() as? JSONObject
            ?: error("Search format changed")
        check(root.has("contents")) { "Search content missing" }
        val videos = LinkedHashMap<String, KaraokeVideo>()
        fun visit(value: Any?, depth: Int = 0) {
            if (depth > 60 || videos.size >= 30) return
            when (value) {
                is JSONObject -> {
                    val renderer = value.optJSONObject("videoRenderer") ?: value.optJSONObject("compactVideoRenderer")
                    if (renderer != null) {
                        val id = renderer.optString("videoId")
                        val title = renderer.optJSONObject("title")
                        val runs = title?.optJSONArray("runs")
                        val text = title?.optString("simpleText").orEmpty().ifBlank {
                            (0 until (runs?.length() ?: 0)).joinToString("") { runs!!.optJSONObject(it)?.optString("text").orEmpty() }
                        }.trim()
                        if (KaraokeVideo.ID.matches(id) && text.isNotBlank()) videos.putIfAbsent(id, KaraokeVideo(id, text))
                    }
                    value.keys().forEach { visit(value.opt(it), depth + 1) }
                }
                is JSONArray -> (0 until value.length()).forEach { visit(value.opt(it), depth + 1) }
            }
        }
        visit(root.opt("contents"))
        return videos.values.toList()
    }
}
