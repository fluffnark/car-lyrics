package com.doomslug.carlyrics

import java.text.Normalizer
import java.util.Locale

data class SpotifySong(val id: String, val title: String, val artists: List<String>) {
    val key: String get() = id.ifBlank { "local:${title}:${artists.joinToString()}" }
}

/** Deliberately conservative: a title alone is not enough to identify a recording. */
internal object KaraokeMatcher {
    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val karaoke = Regex("\\b(karaoke|karafun|sing king)\\b", RegexOption.IGNORE_CASE)
    private val unsafe = Regex("\\b(medley|mashup|reaction|tutorial|cover|live)\\b", RegexOption.IGNORE_CASE)
    private val decoration = Regex("\\b(karaoke|version|instrumental|lyrics|lyric|sing|king|karafun|official|video|audio|hd|hq)\\b")
    private val edition = Regex("\\s*[-–—]\\s*(?:\\d{4}\\s*)?(?:remaster(?:ed)?|radio edit|single version).*$", RegexOption.IGNORE_CASE)
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    fun match(song: SpotifySong, candidates: List<KaraokeVideo>): KaraokeVideo? {
        val title = normalize(song.title.replace(edition, "").replace(Regex("\\s*\\((?:feat\\.|ft\\.).*?\\)", RegexOption.IGNORE_CASE), ""))
        if (title.isBlank() || song.artists.isEmpty()) return null
        if (titles.size > 12_000) titles.clear()
        val artists = song.artists.map(::normalize).filter { it.isNotBlank() }
        fun containsPhrase(haystack: String, needle: String) = (" $haystack ").contains(" $needle ")
        return candidates.distinctBy { it.id }.mapNotNull { video ->
            if (!karaoke.containsMatchIn(video.title) || !KaraokeVideo.ID.matches(video.id)) return@mapNotNull null
            val normalized = titles.getOrPut(video.title) { normalize(video.title) }
            if (!containsPhrase(normalized, title)) return@mapNotNull null
            val artist = artists.firstOrNull { containsPhrase(normalized, it) } ?: return@mapNotNull null
            // A live rendition or cover must not silently replace the studio song.
            if (unsafe.findAll(normalized).any { !containsPhrase(normalize(song.title), it.value) }) return@mapNotNull null
            var rest = normalized.replace(title, " ").replace(artist, " ")
                .replace(Regex("\\b(with lyrics|in the style of|originally performed by)\\b"), " ").replace(decoration, " ")
            rest = rest.replace(Regex("\\s+"), " ").trim()
            // Extra words often identify a different song with a shared short title.
            if (rest.isNotEmpty()) return@mapNotNull null
            video to normalized.length
        }.minByOrNull { it.second }?.first
    }
}
