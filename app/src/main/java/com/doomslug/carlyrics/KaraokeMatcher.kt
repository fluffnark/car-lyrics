package com.doomslug.carlyrics

import java.text.Normalizer
import java.util.Locale

data class SpotifySong(val id: String, val title: String, val artists: List<String>) {
    val key: String get() = id.ifBlank { "local:${title}:${artists.joinToString()}" }
}

/** Deliberately conservative: a title alone is not enough to identify a recording. */
internal object KaraokeMatcher {
    /** Narrow a large provider index by its rarest title word before checking full identity. */
    class Index(videos: List<KaraokeVideo>) {
        private val words = mutableMapOf<String, MutableList<KaraokeVideo>>()
        init {
            videos.distinctBy { it.id }.forEach { video ->
                normalize(video.title).split(' ').distinct().forEach { word ->
                    words.getOrPut(word) { mutableListOf() }.add(video)
                }
            }
        }
        fun match(song: SpotifySong): KaraokeVideo? {
            val title = normalize(cleanTitle(song))
            if (title.isBlank()) return null
            val candidates = title.split(' ').map { words[it] ?: return null }.minByOrNull { it.size }.orEmpty()
            return KaraokeMatcher.match(song, candidates)
        }
    }
    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val karaoke = Regex("\\b(karaoke|karafun|sing king)\\b", RegexOption.IGNORE_CASE)
    private val unsafe = Regex("\\b(medley|mashup|reaction|tutorial|cover|live|acoustic|remix|nightcore|slowed|sped)\\b", RegexOption.IGNORE_CASE)
    private val decoration = Regex("\\b(karaoke|version|instrumental|lyrics|lyric|sing|king|karafun|official|video|audio|hd|hq)\\b")
    private val editionText = "(?:\\d{4}\\s*)?(?:remaster(?:ed)?(?:\\s*\\d{4})?|radio edit|single version|album version|original mix)"
    private val edition = Regex("\\s*[-–—]\\s*$editionText$", RegexOption.IGNORE_CASE)
    private val editionBracket = Regex("^$editionText$", RegexOption.IGNORE_CASE)
    private val feature = Regex("\\s*[\\(\\[](?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*[)\\]]|\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE)
    private val soundtrack = Regex("\\s*[-–—]\\s*from\\s+[\"“].+[\"”]$|\\s*[\\(\\[]from\\s+(?:[\"“][^)\\]]+[\"”]|the (?:series|motion picture|soundtrack) [^)\\]]+)[)\\]]", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace(Regex("['’‘]"), "").replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(whitespace, " ")

    private val bracket = Regex("\\([^)]*\\)|\\[[^]]*]")
    fun cleanTitle(song: SpotifySong): String = song.title.replace(feature, "").replace(soundtrack, "")
        .replace(edition, "")
        .replace(bracket) { m -> if (editionBracket.matches(m.value.drop(1).dropLast(1).trim())) "" else m.value }
        .trim()

    // Public previews sometimes put every artist in a single comma-separated subtitle.
    private fun artists(song: SpotifySong): List<String> = song.artists.flatMap { artist ->
        listOf(artist) + artist.split(Regex("[,;]\\s*|\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE))
    }.map(::normalize).filter(String::isNotBlank).distinct()

    fun recordingKey(song: SpotifySong): String = normalize(cleanTitle(song)) + "|" +
        artists(song).sorted().joinToString("|")

    fun queries(song: SpotifySong): List<String> {
        val artist = song.artists.firstOrNull().orEmpty().substringBefore(',').trim()
        val title = cleanTitle(song)
        return listOf("$artist $title karaoke", "$title $artist karafun",
            "$artist $title sing king karaoke", "$artist $title karaoke with lyrics")
            .map { it.trim().take(200) }.distinct()
    }

    fun match(song: SpotifySong, candidates: List<KaraokeVideo>): KaraokeVideo? {
        val title = normalize(cleanTitle(song))
        if (title.isBlank() || song.artists.isEmpty()) return null
        if (titles.size > 12_000) titles.clear()
        val artists = artists(song)
        if (artists.isEmpty()) return null
        val artistNames = artists.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val collaboration = Regex("\\b(?:$artistNames)(?: (?:and|with|x) (?:$artistNames))+\\b")
        fun removeIdentity(value: String): String {
            var rest = " $value ".replace(" $title ", " ").replace(collaboration, " ")
            artists.sortedByDescending { it.length }.forEach { rest = rest.replace(" $it ", " ") }
            return rest
        }
        fun containsPhrase(haystack: String, needle: String) = (" $haystack ").contains(" $needle ")
        return candidates.distinctBy { it.id }.mapNotNull { video ->
            if (!karaoke.containsMatchIn(video.title) || !KaraokeVideo.ID.matches(video.id)) return@mapNotNull null
            val original = titles.getOrPut(video.title) { normalize(video.title) }
            if (!containsPhrase(original, title) || artists.none { containsPhrase(original, it) }) return@mapNotNull null
            if (unsafe.containsMatchIn(removeIdentity(original))) return@mapNotNull null
            // Provider/year annotations are metadata only when the song and artist
            // can both be identified outside the annotation.
            val withoutMetadata = video.title.replace(bracket) { m ->
                val outside = normalize(video.title.replace(m.value, " "))
                val translatedLabel = m.value.any { it.code > 127 && it.isLetter() } &&
                    !Regex("[A-Za-z]").containsMatchIn(m.value)
                val editionLabel = editionBracket.matches(m.value.drop(1).dropLast(1).trim())
                if ((karaoke.containsMatchIn(m.value) || translatedLabel || editionLabel) && containsPhrase(outside, title) &&
                    artists.any { containsPhrase(outside, it) }) " " else m.value
            }
            val normalized = normalize(withoutMetadata)
            if (!containsPhrase(normalized, title)) return@mapNotNull null
            if (artists.none { containsPhrase(normalized, it) }) return@mapNotNull null
            val rest = removeIdentity(normalized).replace(Regex("\\b(with lyrics|in the style of|originally performed by|feat|ft|featuring)\\b"), " ")
                .replace(Regex("\\b(?:by )?(?:simplysing|spectral kpop|sing2piano|sing2guitar|sing king|karafun|zoom karaoke|sunfly karaoke|funbox karaoke)\\b"), " ")
                .replace(decoration, " ").replace(whitespace, " ").trim()
            if (rest.isNotEmpty()) return@mapNotNull null
            video to original.length
        }.minByOrNull { it.second }?.first
    }
}
