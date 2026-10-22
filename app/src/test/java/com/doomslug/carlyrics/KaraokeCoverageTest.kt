package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test

class KaraokeCoverageTest {
    private fun video(title: String) = KaraokeVideo("abcdefghijk", title)

    @Test fun recognizesObservedProviderAndCombinedArtistFormats() {
        val dodie = SpotifySong("a", "Would You Be So Kind", listOf("dodie"))
        val simplySing = video("SimplySing Karaoke - Dodie: Would You Be So Kind?")
        assertEquals(simplySing, KaraokeMatcher.match(dodie, listOf(simplySing)))
        val lifehouse = SpotifySong("b", "Between The Raindrops", listOf("Lifehouse,\u00a0Natasha Bedingfield"))
        val duet = video("Lifehouse Ft. Natasha Bedingfield - Between The Raindrops (Karaoke Version)")
        assertEquals(duet, KaraokeMatcher.match(lifehouse, listOf(duet)))
        assertNull(KaraokeMatcher.match(lifehouse, listOf(video("Other Artist - Between The Raindrops (Karaoke Version)"))))
        val underPressure = SpotifySong("c", "Under Pressure", listOf("Queen", "David Bowie"))
        assertNotNull(KaraokeMatcher.match(underPressure, listOf(video("Queen & David Bowie - Under Pressure (Karaoke Version)"))))
    }

    @Test fun indexedMatchingUsesTheSameIdentityChecksAsDirectMatching() {
        val song = SpotifySong("a", "Hello", listOf("Adele"))
        val videos = listOf(video("Adele - Hello (Karaoke Version)"),
            KaraokeVideo("12345678901", "Adele - Hello Again (Karaoke Version)"))
        assertEquals(KaraokeMatcher.match(song, videos), KaraokeMatcher.Index(videos).match(song))
        assertNull(KaraokeMatcher.Index(videos).match(song.copy(title = "Unknown Song")))
    }

    @Test fun partialPreviewPreservesOldRowsAndManualChoicesWhileFullRefreshCanRemoveRows() {
        val a = SpotifySong("a", "Hello", listOf("Adele"))
        val b = SpotifySong("b", "One", listOf("U2"))
        val chosen = SpotifyMatch(a, video("Chosen version"), "manual", 123)
        val previous = SpotifyPlaylist("id", "Old", 3, listOf(chosen, SpotifyMatch(b)))
        val merged = mergeSpotifyPreview(previous.copy(name = "Renamed"), listOf(a), previous)
        assertEquals(listOf(chosen, SpotifyMatch(b)), merged.songs)
        assertEquals("Renamed", merged.name)
        assertEquals(3, merged.total)
        assertTrue(merged.problem!!.contains("2 known of 3"))
        val complete = mergeSpotifyPreview(previous.copy(total = 1), listOf(a), previous)
        assertEquals(listOf(chosen), complete.songs)
        assertNull(complete.problem)
        val unknown = mergeSpotifyPreview(previous.copy(total = 0), listOf(a), previous)
        assertNotNull(unknown.problem)
        assertEquals(2, unknown.songs.size)
    }

    @Test fun recognizesTranslatedLabelsOnlyWithSongAndArtistOutsideThem() {
        val song = SpotifySong("a", "Side Effects", listOf("Stray Kids"))
        assertNotNull(KaraokeMatcher.match(song, listOf(video("Stray Kids (스트레이 키즈) - Side Effects (부작용) Karaoke/Instrumental with Lyrics | Spectral KPOP"))))
        assertNull(KaraokeMatcher.match(song, listOf(video("Other - Side Effects (스트레이 키즈) Karaoke"))))
        assertNull(KaraokeMatcher.match(song, listOf(video("Stray Kids - Side Effects Again (부작용) Karaoke"))))
    }

    @Test fun cleansEditionAndFeatureMetadataButPreservesDistinctVersionsAndSubtitles() {
        val base = SpotifySong("a", "Song", listOf("Artist", "Guest"))
        for (title in listOf("Song (feat. Guest)", "Song [feat Guest]", "Song feat. Guest", "Song - Remastered 2011",
            "Song (2011 Remaster)", "Song - Radio Edit", "Song (Album Version)", "Song - From \"Movie\"")) {
            assertEquals(title, "Song", KaraokeMatcher.cleanTitle(base.copy(title = title)))
        }
        for (title in listOf("Song (Live)", "Song - Acoustic", "Song (Remix)", "Song (Part Two)", "Song - From The Heart")) {
            assertEquals(title, title, KaraokeMatcher.cleanTitle(base.copy(title = title)))
        }
        assertNotNull(KaraokeMatcher.match(base.copy(title = "I'm Here"), listOf(video("Artist - Im Here (Karaoke Version)"))))
        assertNull(KaraokeMatcher.match(base, listOf(video("Artist - Song Again (Karaoke Version)"))))
        assertNull(KaraokeMatcher.match(base, listOf(video("Artist - Song And (Karaoke Version)"))))
        assertNull(KaraokeMatcher.match(base, listOf(video("Artist - Song (Live Karaoke)"))))
        assertNotNull(KaraokeMatcher.match(base, listOf(video("Artist - Song (2015 Remaster) (Karaoke Version)"))))
        assertNotNull(KaraokeMatcher.match(base.copy(title = "Lightning Crashes", artists = listOf("Live")),
            listOf(video("Live - Lightning Crashes (Karaoke Version)"))))
        assertNull(KaraokeMatcher.match(base.copy(title = "Cover Me"), listOf(video("Artist - Cover Me (Cover Karaoke)"))))
    }

    @Test fun reusesRecordingIdentityAcrossSpotifyIdsButKeepsArtistsAndVersionsDistinct() {
        val song = SpotifySong("album", "Hello - 2015 Remaster", listOf("Adele"))
        assertEquals(KaraokeMatcher.recordingKey(song), KaraokeMatcher.recordingKey(song.copy(id = "single", title = "Hello")))
        assertNotEquals(KaraokeMatcher.recordingKey(song), KaraokeMatcher.recordingKey(song.copy(artists = listOf("Lionel Richie"))))
        assertNotEquals(KaraokeMatcher.recordingKey(song), KaraokeMatcher.recordingKey(song.copy(title = "Hello (Live)")))
    }

    @Test fun triesAllFallbacksAndStopsWhenConfident() {
        val song = SpotifySong("a", "Hello", listOf("Adele"))
        val queries = mutableListOf<String>()
        val match = video("Adele - Hello (Karaoke Version)")
        val lookup = KaraokeLookup(fetch = { query ->
            queries += query
            VideoSearchResult(if (queries.size == 3) listOf(match) else emptyList())
        }, pause = {})
        assertEquals(match, KaraokeMatcher.match(song, lookup.search(song).videos))
        assertEquals(KaraokeMatcher.queries(song).take(3), queries)
    }

    @Test fun aFailedQueryCanRecoverAndPartialSearchesStayRetryable() {
        val song = SpotifySong("a", "Hello", listOf("Adele"))
        val queries = KaraokeMatcher.queries(song)
        val match = video("Adele - Hello Karaoke")
        val recovered = KaraokeLookup(fetch = { query ->
            if (query == queries.first()) VideoSearchResult(emptyList(), "connection timed out") else VideoSearchResult(listOf(match))
        }, pause = {}).search(song)
        assertNull(recovered.error)
        assertEquals(match, recovered.videos.single())
        val incomplete = KaraokeLookup(fetch = { query ->
            if (query == queries.last()) VideoSearchResult(emptyList(), "connection timed out") else VideoSearchResult(listOf(video("Other Song Karaoke")))
        }, pause = {}).search(song)
        assertNotNull(incomplete.error)
        assertEquals(1, incomplete.videos.size)
    }

    @Test fun rateLimitsAndCancellationDoNotContinueSendingRequests() {
        val song = SpotifySong("a", "Hello", listOf("Adele"))
        var requests = 0
        val lookup = KaraokeLookup(fetch = { requests++; VideoSearchResult(emptyList(), "HTTP 429") }, pause = {})
        assertEquals("HTTP 429", lookup.search(song).error)
        assertEquals(1, requests)
        assertNotNull(lookup.search(song) { true }.error)
        assertEquals(1, requests)
    }
}
