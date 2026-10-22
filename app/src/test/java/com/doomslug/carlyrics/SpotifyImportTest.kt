package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SpotifyImportTest {
    @Test fun additionalProviderIndexMatchesSongsBeyondTheBrowseSnapshot() {
        val app = RuntimeEnvironment.getApplication()
        SingKingCatalog.initialize(app)
        val catalog = KaraokeMatchingCatalog.index(app)
        assertNotNull(catalog.match(SpotifySong("rush", "Tom Sawyer", listOf("Rush"))))
        assertNotNull(catalog.match(SpotifySong("flipturn", "August", listOf("flipturn"))))
        assertNull(catalog.match(SpotifySong("wrong", "Tom Sawyer", listOf("Unrelated Artist"))))
    }
    @Test fun matchesArtistAndSongButRejectsOriginalLiveAndLongerTitles() {
        val song = SpotifySong("one", "Hello", listOf("Adele"))
        val correct = KaraokeVideo("abcdefghijk", "Adele - Hello (Karaoke Version)")
        assertEquals(correct, KaraokeMatcher.match(song, listOf(
            KaraokeVideo("12345678901", "Lionel Richie - Hello (Karaoke Version)"),
            KaraokeVideo("12345678902", "Adele - Hello (Official Video)"),
            KaraokeVideo("12345678903", "Adele - Hello Again (Karaoke Version)"),
            KaraokeVideo("12345678904", "Adele - Hello Live (Karaoke Version)"), correct)))
        assertNull(KaraokeMatcher.match(song, listOf(KaraokeVideo("12345678905", "Adele Hello karaoke mashup"))))
        assertNull(KaraokeMatcher.match(song.copy(artists = emptyList()), listOf(correct)))
    }
    @Test fun normalizesAccentsAndRemasterWithoutConfusingOtherSongs() {
        assertNotNull(KaraokeMatcher.match(SpotifySong("a", "Think Twice - 2004 Remastered", listOf("Céline Dion")),
            listOf(KaraokeVideo("abcdefghijk", "Celine Dion - Think Twice (Karaoke Version)"))))
        assertNull(KaraokeMatcher.match(SpotifySong("a", "One", listOf("U2")),
            listOf(KaraokeVideo("abcdefghijk", "U2 - One Tree Hill (Karaoke Version)"))))
    }
    @Test fun followsAllPlaylistAndTrackPagesUsingNewAndLegacyFields() {
        val requests = mutableListOf<String>()
        val api = SpotifyLibrary { url -> requests.add(url); when {
            url.contains("offset=50") -> JSONObject("""{"items":[{"id":"2222222222222222222222","name":"Second","tracks":{"total":1}}],"next":null}""")
            url.contains("me/playlists") -> JSONObject("""{"items":[{"id":"1111111111111111111111","name":"First","items":{"total":2}}],"next":"https://api.spotify.com/v1/me/playlists?offset=50"}""")
            url.contains("offset=1") -> JSONObject("""{"items":[{"track":{"id":"b","name":"B","artists":[{"name":"Artist"}]}},{"item":{"type":"episode","name":"Skip"}},{"item":null}],"next":null}""")
            else -> JSONObject("""{"items":[{"item":{"id":"a","name":"A","type":"track","artists":[{"name":"Artist"}]}}],"next":"https://api.spotify.com/v1/playlists/1111111111111111111111/items?offset=1"}""")
        } }
        assertEquals(2, api.playlists().size)
        assertEquals(listOf("A", "B"), api.songs("1111111111111111111111").map { it.title })
        assertEquals(4, requests.size)
    }
    @Test fun repeatedPageIsAnErrorInsteadOfPartialSuccessOrInfiniteLoop() {
        val api = SpotifyLibrary { JSONObject("""{"items":[],"next":"https://api.spotify.com/v1/me/playlists?limit=50"}""") }
        assertThrows(IllegalStateException::class.java) { api.playlists() }
    }
    @Test fun publicPreviewParsingChecksIdentityAndIgnoresEpisodes() {
        val id = "1111111111111111111111"
        val html = """<script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{"data":{"entity":{"uri":"spotify:playlist:$id","trackList":[{"uri":"spotify:track:2222222222222222222222","title":"Song","subtitle":"Artist"},{"uri":"spotify:episode:3333333333333333333333","title":"Episode","subtitle":"Host"}]}}}}}}</script>"""
        assertEquals(listOf(SpotifySong("2222222222222222222222", "Song", listOf("Artist"))), SpotifyPublicPlaylist.parse(html, id))
        assertThrows(IllegalStateException::class.java) { SpotifyPublicPlaylist.parse(html, "4444444444444444444444") }
        assertThrows(IllegalStateException::class.java) { SpotifyPublicPlaylist.parse("Changed page", id) }
    }
    @Test fun importingDoesNotOverwriteLocalAlbumsOrDuplicateOnRefresh() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("karaoke_playlists", 0).edit().clear().commit()
        val local = PlaylistStore(app); val store = SpotifyImportStore(app)
        val video = KaraokeVideo("abcdefghijk", "Adele Hello Karaoke")
        local.add("Road trip", video)
        val p = SpotifyPlaylist("1111111111111111111111", "Road trip", 2,
            listOf(SpotifyMatch(SpotifySong("a", "Hello", listOf("Adele")), video, "matched"),
                SpotifyMatch(SpotifySong("b", "Unavailable", listOf("Artist")), state = "missing")))
        store.put(p); store.put(p.copy(name = "Renamed"))
        local.add("Other", video)
        assertEquals(1, store.all().size)
        assertEquals(1, store.all().single().matched)
        assertEquals(listOf(video), local.all().first { it.name == "Road trip" }.videos)
        assertEquals("Renamed", store.all().single().name)
        assertEquals(2, store.all().single().songs.size)
        assertEquals(1, local.all().count { it.spotifyId != null })
        val corrected = KaraokeVideo("12345678901", "Chosen karaoke")
        assertTrue(store.choose(p.id, "b", corrected))
        assertEquals(corrected, SpotifyImportStore(app).all().single().songs.last().video)
        assertEquals("manual", SpotifyImportStore(app).all().single().songs.last().state)
        assertFalse(store.choose(p.id, "unknown", corrected))
        store.put(p.copy(problem = "Restricted"))
        assertEquals(listOf(video), store.albums().single().videos)
    }
    @Test fun csvUnderstandsQuotedFieldsBomMultiPlaylistAndStableIdentity() {
        val text = "\uFEFFPlaylist Name,Track Name,Artist Name(s),Track URI\r\nRoad trip,\"Hello, again\",\"Artist;Guest\",spotify:track:abc\r\nOther,\"Line\nBreak\",Artist,spotify:track:def\r\n"
        val parsed = SpotifyCsv.parse(text, "File")
        assertEquals(2, parsed.size)
        assertEquals("Hello, again", parsed.first().songs.first().song.title)
        assertEquals(listOf("Artist", "Guest"), parsed.first().songs.first().song.artists)
        assertEquals(parsed.first().id, SpotifyCsv.parse(text, "Different filename").first().id)
        assertThrows(IllegalArgumentException::class.java) { SpotifyCsv.parse("name\nSong", "Bad") }
        assertThrows(IllegalArgumentException::class.java) { SpotifyCsv.rows("\"unfinished") }
    }
}
