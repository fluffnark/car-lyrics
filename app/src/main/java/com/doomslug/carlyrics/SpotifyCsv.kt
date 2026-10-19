package com.doomslug.carlyrics

import java.security.MessageDigest

/** RFC 4180-style CSV, including quoted commas/newlines and UTF-8 BOM exports. */
internal object SpotifyCsv {
    fun rows(text: String): List<List<String>> {
        require(text.length <= 8_000_000) { "CSV is too large (8 MB maximum)" }
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (c == '"') {
                if (quoted && i < text.length && text[i] == '"') { cell.append('"'); i++ }
                else quoted = !quoted
            } else if (!quoted && (c == ',' || c == '\n' || c == '\r')) {
                row.add(cell.toString()); cell.setLength(0)
                if (c != ',') {
                    if (c == '\r' && i < text.length && text[i] == '\n') i++
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = mutableListOf()
                }
            } else cell.append(c)
        }
        require(!quoted) { "CSV has an unfinished quoted field" }
        row.add(cell.toString()); if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }
    fun parse(text: String, fallbackName: String): List<SpotifyPlaylist> {
        val rows = rows(text.removePrefix("\uFEFF"))
        require(rows.size > 1) { "CSV has no songs" }
        val header = rows.first().map { it.lowercase().filter(Char::isLetterOrDigit) }
        fun column(vararg names: String) = header.indexOfFirst { it in names }
        val title = column("trackname", "songname", "title", "name")
        val artist = column("artistnames", "artistname", "artists", "artist")
        val playlist = column("playlistname", "playlist")
        val uri = column("trackuri", "spotifytrackuri", "trackid", "spotifyuri")
        require(title >= 0 && artist >= 0) { "CSV needs Track Name and Artist Name(s) columns" }
        val grouped = rows.drop(1).groupBy { it.getOrNull(playlist)?.trim()?.takeIf(String::isNotBlank) ?: fallbackName }
        return grouped.map { (name, items) ->
            val id = "csv:" + MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            val songs = items.mapNotNull { r ->
                val song = r.getOrNull(title)?.trim().orEmpty(); val artists = r.getOrNull(artist)?.split(';')?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
                if (song.isBlank() || artists.isEmpty()) null else SpotifyMatch(SpotifySong(r.getOrNull(uri).orEmpty().substringAfterLast(':'), song, artists))
            }
            SpotifyPlaylist(id, name, songs.size, songs)
        }
    }
}
