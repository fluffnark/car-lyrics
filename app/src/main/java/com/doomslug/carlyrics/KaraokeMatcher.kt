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
    private val unsafe = Regex("\\b(medley|mashup|reaction|tutorial|cover|live|acoustic|remix|nightcore|slowed|sped)\\b", RegexOption.IGNORE_CASE)
    private val decoration = Regex("\\b(karaoke|version|instrumental|lyrics|lyric|sing|king|karafun|official|video|audio|hd|hq)\\b")
    private val edition = Regex("\\s*[-–—]\\s*(?:\\d{4}\\s*)?(?:remaster(?:ed)?|radio edit|single version).*$", RegexOption.IGNORE_CASE)
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private val bracket = Regex("\\([^)]*\\)|\\[[^]]*]")
    fun cleanTitle(song: SpotifySong): String = song.title.replace(edition, "")
        .replace(bracket) { m -> if (Regex("^(?:\\d{4}\\s*)?remaster(?:ed)?(?:\\s*\\d{4})?$", RegexOption.IGNORE_CASE)
            .matches(m.value.drop(1).dropLast(1).trim())) "" else m.value }
        .replace(Regex("\\s*\\((?:feat\\.|ft\\.).*?\\)", RegexOption.IGNORE_CASE), "").trim()

    fun queries(song: SpotifySong): List<String> = listOf(
        "${song.artists.firstOrNull().orEmpty()} ${cleanTitle(song)} karaoke",
        "${cleanTitle(song)} ${song.artists.firstOrNull().orEmpty()} karafun"
    ).map { it.trim().take(200) }.distinct()

    fun match(song: SpotifySong, candidates: List<KaraokeVideo>): KaraokeVideo? {
        val title = normalize(cleanTitle(song))
        if (title.isBlank() || song.artists.isEmpty()) return null
        if (titles.size > 12_000) titles.clear()
        val artists = song.artists.map(::normalize).filter { it.isNotBlank() }
        fun containsPhrase(haystack: String, needle: String) = (" $haystack ").contains(" $needle ")
        return candidates.distinctBy { it.id }.mapNotNull { video ->
            if (!karaoke.containsMatchIn(video.title) || !KaraokeVideo.ID.matches(video.id)) return@mapNotNull null
            val original = titles.getOrPut(video.title) { normalize(video.title) }
            if (!containsPhrase(original, title) || artists.none { containsPhrase(original, it) }) return@mapNotNull null
            if (unsafe.findAll(original).any { !containsPhrase(normalize(song.title), it.value) }) return@mapNotNull null
            // Provider/year annotations are metadata only when the song and artist
            // can both be identified outside the annotation.
            val withoutMetadata = video.title.replace(bracket) { m ->
                val outside = normalize(video.title.replace(m.value, " "))
                if (karaoke.containsMatchIn(m.value) && containsPhrase(outside, title) &&
                    artists.any { containsPhrase(outside, it) }) " " else m.value
            }
            val normalized = normalize(withoutMetadata)
            if (!containsPhrase(normalized, title)) return@mapNotNull null
            if (artists.none { containsPhrase(normalized, it) }) return@mapNotNull null
            var rest = " $normalized ".replace(" $title ", " ")
            artists.sortedByDescending { it.length }.forEach { rest = rest.replace(" $it ", " ") }
            rest = rest.replace(Regex("\\b(with lyrics|in the style of|originally performed by|feat|ft|featuring)\\b"), " ")
                .replace(decoration, " ").replace(Regex("\\s+"), " ").trim()
            if (rest.isNotEmpty()) return@mapNotNull null
            video to original.length
        }.minByOrNull { it.second }?.first
    }
}
