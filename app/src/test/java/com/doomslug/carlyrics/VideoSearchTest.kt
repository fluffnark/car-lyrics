package com.doomslug.carlyrics

import android.os.Looper
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.TestCarContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoSearchTest {
    private val queen = KaraokeVideo("abcdefghijk", "Queen - Don't Stop Me Now (Karaoke Version)")
    private val remoteSong = KaraokeVideo("12345678901", "Don't Stop Me Now - Queen | Other Karaoke Provider")

    @Test fun spokenCommandsMatchArtistSongPunctuationAndAccents() {
        val phrase = VideoSearchQuery.phrase("Please find me karaoke for Don't Stop Me Now by Queen on YouTube")
        assertEquals("karaoke Don't Stop Me Now by Queen", phrase)
        assertEquals(listOf(queen), VideoSearchQuery.matches(listOf(queen), phrase))
        val beyonce = KaraokeVideo("11111111111", "Beyoncé - Halo (Karaoke Version)")
        assertEquals(listOf(beyonce), VideoSearchQuery.matches(listOf(beyonce), "halo by beyonce"))
        assertTrue(VideoSearchQuery.matches(listOf(queen), "???").isEmpty())
    }

    @Test fun aSubmittedVoicePhraseSearchesImmediatelyAndIgnoresOlderResults() {
        val requests = mutableListOf<Pair<String, (VideoSearchResult) -> Unit>>()
        val search = VideoSearchSession({ listOf(queen) }, VideoSearch { q, callback -> requests += q to callback }, Executor { it.run() }) {}
        search.update("Queen", submitted = true)
        search.update("Play Don't Stop Me Now by Queen", submitted = true)
        assertEquals("Don't Stop Me Now by Queen", requests.last().first)
        requests.last().second(VideoSearchResult(listOf(remoteSong)))
        requests.first().second(VideoSearchResult(listOf(KaraokeVideo("99999999999", "Stale video"))))
        assertEquals(listOf(remoteSong, queen), search.videos)
        assertFalse(search.loading)
        search.close()
    }

    @Test fun submitFlushesTypingDebounceOnceAndCloseRejectsPendingResponses() {
        var count = 0
        var reply: ((VideoSearchResult) -> Unit)? = null
        val search = VideoSearchSession({ emptyList() }, VideoSearch { _, callback -> count++; reply = callback }, Executor { it.run() }) {}
        search.update("Queen karaoke")
        assertEquals(0, count)
        search.update("Queen karaoke", submitted = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(1, count)
        search.close()
        reply!!(VideoSearchResult(listOf(remoteSong)))
        assertTrue(search.videos.isEmpty())
    }

    @Test fun partialSpeechDoesNotFilterTheLibraryOnTheUiCallbackAndLateLocalWorkIsIgnored() {
        val jobs = mutableListOf<Runnable>()
        val requests = mutableListOf<String>()
        val search = VideoSearchSession({ listOf(queen) },
            VideoSearch { q, _ -> requests += q }, Executor { jobs += it }) {}
        // Android Auto sends many interim transcripts, including temporary empty text.
        listOf("Queen", "", "Queen Bohemian", "", "Queen Bohemian Rhapsody karaoke").forEach {
            search.update(it)
        }
        assertTrue(jobs.isEmpty())
        assertTrue(requests.isEmpty())
        search.update("Queen Bohemian Rhapsody karaoke", submitted = true)
        assertEquals(listOf("Queen Bohemian Rhapsody karaoke"), requests)
        assertEquals(1, jobs.size)
        search.update("different song", submitted = true)
        jobs.first().run()
        assertTrue(search.videos.isEmpty())
        search.close()
    }

    @Test fun networkFailureRetainsMatchingLocalSongsAndCanBeRetried() {
        var reply: ((VideoSearchResult) -> Unit)? = null
        val search = VideoSearchSession({ listOf(queen) }, VideoSearch { _, callback -> reply = callback }, Executor { it.run() }) {}
        search.update("Queen", submitted = true)
        reply!!(VideoSearchResult(emptyList(), "Offline"))
        assertEquals(listOf(queen), search.videos)
        assertEquals("Offline", search.error)
        search.update("Queen", submitted = true, retry = true)
        assertTrue(search.loading)
        reply!!(VideoSearchResult(listOf(remoteSong)))
        assertNull(search.error)
        search.close()
    }

    @Test fun parserKeepsTitlesBoundToTheirOwnVideoAndDeduplicates() {
        val html = """<script>var ytInitialData = {"contents":{"items":[
          {"videoRenderer":{"videoId":"abcdefghijk","title":{"runs":[{"text":"Queen "},{"text":"& friends"}]}}},
          {"videoRenderer":{"videoId":"abcdefghijk","title":{"simpleText":"Duplicate"}}},
          {"videoRenderer":{"videoId":"bad-id","title":{"simpleText":"Invalid"}}},
          {"videoRenderer":{"videoId":"00000000000"}},
          {"videoRenderer":{"videoId":"12345678901","title":{"simpleText":"Other provider"}}}
        ]}};</script>"""
        assertEquals(listOf(KaraokeVideo("abcdefghijk", "Queen & friends"), KaraokeVideo("12345678901", "Other provider")), YouTubeSearchParser.parse(html))
    }

    @Test fun changedOrConsentPageIsAnErrorRatherThanFalseNoResults() {
        assertThrows(IllegalStateException::class.java) { YouTubeSearchParser.parse("<html>Consent page</html>") }
        assertEquals(emptyList<KaraokeVideo>(), YouTubeSearchParser.parse("var ytInitialData = {\"contents\":{}};"))
    }

    @Test fun hostSpeechSubmissionToRemoteResultToVideoSelectionWorks() {
        val context = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        val catalog = object : VideoCatalog {
            override val videos = listOf(queen)
            override val loading = false
            override val error: String? = null
            override fun refresh(done: () -> Unit) = done()
        }
        var selected: KaraokeVideo? = null
        val player = object : VideoPlayer {
            override var onStatus: ((PlaybackStatus) -> Unit)? = null
            override fun select(video: KaraokeVideo) { selected = video }
            override fun pause() {}
            override fun resume() {}
            override fun hide() {}
            override fun close() {}
        }
        var reply: ((VideoSearchResult) -> Unit)? = null
        val screen = CarLyricsScreen(context, catalog, player, SavedVideos(context), VideoSearch { _, callback -> reply = callback })
        val manager = context.getCarService(androidx.car.app.ScreenManager::class.java)
        context.lifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        manager.push(screen)
        val done = object : OnDoneCallback {}
        ((manager.top.onGetTemplate() as ListTemplate).singleList!!.items.first() as Row).onClickDelegate!!.sendClick(done)
        val search = manager.top.onGetTemplate() as SearchTemplate
        assertFalse(search.isShowKeyboardByDefault)
        search.searchCallbackDelegate.sendSearchSubmitted("Play Don't Stop Me Now by Queen", done)
        reply!!(VideoSearchResult(listOf(remoteSong)))
        // Real DHU host clears its old search field when its voice overlay closes.
        search.searchCallbackDelegate.sendSearchTextChanged("", done)
        val results = manager.top.onGetTemplate() as ListTemplate
        assertEquals("Play Don't Stop Me Now by Queen", results.header!!.title.toString())
        val result = results.singleList!!.items.first() as Row
        assertEquals(remoteSong.title, result.title.toString())
        result.onClickDelegate!!.sendClick(done)
        assertEquals(remoteSong, selected)
        assertTrue(manager.top.onGetTemplate() is MapWithContentTemplate)
    }
}
