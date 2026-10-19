package com.doomslug.carlyrics

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Public embed fallback. It may be a preview, so unknown/truncated coverage is always labelled. */
internal object SpotifyPublicPlaylist {
    fun fetch(id: String): List<SpotifySong> {
        require(id.matches(Regex("[A-Za-z0-9]{22}")))
        val conn = URL("https://open.spotify.com/embed/playlist/$id").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 12_000; conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            check(conn.responseCode == 200) { "Public playlist unavailable" }
            val html = conn.inputStream.bufferedReader().use { reader ->
                val out = StringBuilder(); val buffer = CharArray(8192)
                while (true) { val n = reader.read(buffer); if (n < 0) break; out.append(buffer, 0, n); check(out.length <= 4_000_000) }
                out.toString()
            }
            return parse(html, id)
        } finally { conn.disconnect() }
    }
    fun parse(html: String, id: String): List<SpotifySong> {
        val script = Regex("<script[^>]*id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1) ?: error("Public playlist format changed")
        val entity = JSONObject(script).getJSONObject("props").getJSONObject("pageProps")
            .getJSONObject("state").getJSONObject("data").getJSONObject("entity")
        check(entity.optString("uri") == "spotify:playlist:$id") { "Public playlist identity changed" }
        val tracks = entity.getJSONArray("trackList")
        return (0 until tracks.length()).mapNotNull { i ->
            val row = tracks.optJSONObject(i) ?: return@mapNotNull null
            val uri = row.optString("uri")
            val title = row.optString("title"); val artist = row.optString("subtitle")
            if (!uri.matches(Regex("spotify:track:[A-Za-z0-9]{22}")) || title.isBlank() || artist.isBlank()) null
            else SpotifySong(uri.substringAfterLast(':'), title, listOf(artist))
        }
    }
}
