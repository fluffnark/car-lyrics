package com.doomslug.carlyrics

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Small no-key search adapter for the public YouTube results page. */
object YouTubeSearch {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val resultPattern = Regex(
        "\\\"videoRenderer\\\":\\{\\\"videoId\\\":\\\"([A-Za-z0-9_-]{11})\\\".{0,2600}?\\\"title\\\":\\{\\\"runs\\\":\\[\\{\\\"text\\\":\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )

    fun search(query: String, done: (List<KaraokeVideo>) -> Unit) {
        val clean = query.trim()
        if (clean.length < 2) { done(emptyList()); return }
        worker.execute {
            val results = runCatching {
                val encoded = URLEncoder.encode(clean, "UTF-8")
                val connection = (URL("https://www.youtube.com/results?search_query=$encoded").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.8")
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val html = reader.readText()
                    val seen = LinkedHashSet<String>()
                    resultPattern.findAll(html).mapNotNull { match ->
                        val id = match.groupValues[1]
                        if (!seen.add(id)) return@mapNotNull null
                        val title = decode(match.groupValues[2]).trim()
                        if (title.isBlank()) null else KaraokeVideo(id, title)
                    }.toList().take(30)
                }
            }.getOrDefault(emptyList())
            main.post { done(results) }
        }
    }

    private fun decode(value: String): String = runCatching {
        JSONObject("{\"value\":\"$value\"}").getString("value")
    }.getOrDefault(value.replace("\\\"", "\"").replace("\\n", " "))
}
