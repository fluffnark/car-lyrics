package com.doomslug.carlyrics

/** Try independent searches without losing successful results when a later request fails. */
internal class KaraokeLookup(
    private val fetch: (String) -> VideoSearchResult = YouTubeSearch::fetch,
    private val pause: (Long) -> Unit = Thread::sleep
) {
    fun search(song: SpotifySong, cancelled: () -> Boolean = { false }): VideoSearchResult {
        val candidates = LinkedHashMap<String, KaraokeVideo>()
        var error: String? = null
        for ((index, query) in KaraokeMatcher.queries(song).withIndex()) {
            if (cancelled()) return VideoSearchResult(candidates.values.toList(), "Matching paused. Tap Resume to continue.")
            pause(if (index == 0) 400 else 700)
            var result = fetch(query)
            repeat(2) {
                if (result.error != null && !blocked(result.error) && !cancelled()) {
                    pause(2_000)
                    result = fetch(query)
                }
            }
            result.videos.forEach { candidates.putIfAbsent(it.id, it) }
            val videos = candidates.values.toList()
            if (KaraokeMatcher.match(song, videos) != null) return VideoSearchResult(videos)
            if (result.error != null) {
                error = result.error
                if (blocked(error)) return VideoSearchResult(videos, error)
            }
        }
        // An incomplete search remains retryable, even if some queries succeeded.
        return VideoSearchResult(candidates.values.toList(), error)
    }

    private fun blocked(error: String?) = error?.contains("HTTP 429") == true || error?.contains("HTTP 403") == true
}
