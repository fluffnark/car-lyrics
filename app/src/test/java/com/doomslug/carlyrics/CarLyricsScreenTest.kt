package com.doomslug.carlyrics

import android.app.Application
import androidx.car.app.OnDoneCallback
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarLyricsScreenTest {
    private val app get() = RuntimeEnvironment.getApplication() as Application
    private val done = object : OnDoneCallback {}
    private val videos = (1..10).map { KaraokeVideo("%011d".format(it), "Song $it (Karaoke Version)") }

    @Before fun clear() { app.getSharedPreferences("saved_videos", 0).edit().clear().commit() }

    @Test fun rotaryBrowseSelectPauseSaveAndReturn() {
        val context = TestCarContext.createCarContext(app)
        val player = FakePlayer()
        val screen = CarLyricsScreen(context, FakeCatalog(videos), player, SavedVideos(context))
        val browse = screen.onGetTemplate() as ListTemplate
        assertEquals("Sing King • Recent", browse.header!!.title.toString())
        val first = browse.singleList!!.items.first { (it as Row).title.toString().startsWith("Song") } as Row
        assertNotNull(first.image)
        assertTrue(first.onClickDelegate!!.isParkedOnly)
        first.onClickDelegate!!.sendClick(done)

        assertEquals(videos.first(), player.selected)
        val loading = screen.onGetTemplate() as MapWithContentTemplate
        assertEquals(3, loading.actionStrip!!.actions.size)
        player.onStatus?.invoke(PlaybackStatus.PLAYING)
        val playing = screen.onGetTemplate() as MapWithContentTemplate
        assertEquals("Pause", playing.actionStrip!!.actions[0].title.toString())
        playing.actionStrip!!.actions[0].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.pauses)
        val paused = screen.onGetTemplate() as MapWithContentTemplate
        assertEquals("Play", paused.actionStrip!!.actions[0].title.toString())
        paused.actionStrip!!.actions[1].onClickDelegate!!.sendClick(done)
        assertTrue(SavedVideos(context).contains(videos.first().id))
        paused.actionStrip!!.actions[2].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.hides)
        val recent = screen.onGetTemplate() as ListTemplate
        recent.header!!.endHeaderActions.single().onClickDelegate!!.sendClick(done)
        val saved = screen.onGetTemplate() as ListTemplate
        assertEquals("Saved karaoke songs", saved.header!!.title.toString())
        assertEquals(videos.first().title, (saved.singleList!!.items.first() as Row).title.toString())
    }

    @Test fun paginationAndPlaybackErrorOfferUsefulActions() {
        val context = TestCarContext.createCarContext(app)
        val player = FakePlayer()
        val screen = CarLyricsScreen(context, FakeCatalog(videos), player, SavedVideos(context))
        val firstPage = screen.onGetTemplate() as ListTemplate
        val more = firstPage.singleList!!.items.last() as Row
        assertTrue(more.title.toString().startsWith("More songs"))
        more.onClickDelegate!!.sendClick(done)
        val secondPage = screen.onGetTemplate() as ListTemplate
        assertEquals("Previous page", (secondPage.singleList!!.items.first() as Row).title.toString())
        val song = secondPage.singleList!!.items[1] as Row
        song.onClickDelegate!!.sendClick(done)
        assertEquals(videos[4], player.selected)
        player.onStatus?.invoke(PlaybackStatus.ERROR)
        val error = screen.onGetTemplate() as MapWithContentTemplate
        assertEquals("Retry", error.actionStrip!!.actions[0].title.toString())
        assertTrue(error.actionStrip!!.actions[0].onClickDelegate!!.isParkedOnly)
        error.actionStrip!!.actions[0].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.resumes)
    }

    @Test fun nextAndPreviousFollowTheSelectedPageAndWrap() {
        val context = TestCarContext.createCarContext(app)
        val player = FakePlayer()
        val screen = CarLyricsScreen(context, FakeCatalog(videos), player, SavedVideos(context))
        val first = (screen.onGetTemplate() as ListTemplate).singleList!!.items.first { (it as Row).title.toString().startsWith("Song") } as Row
        first.onClickDelegate!!.sendClick(done)
        val actions = ((screen.onGetTemplate() as MapWithContentTemplate).contentTemplate as androidx.car.app.model.PaneTemplate)
            .pane!!.actions
        actions[0].onClickDelegate!!.sendClick(done)
        assertEquals(videos.last(), player.selected)
        actions[1].onClickDelegate!!.sendClick(done)
        assertEquals(videos.first(), player.selected)
    }

    @Test fun hardwareBackReturnsFromPlayerToBrowse() {
        val context = TestCarContext.createCarContext(app)
        val player = FakePlayer()
        val screen = CarLyricsScreen(context, FakeCatalog(videos), player, SavedVideos(context))
        context.getCarService(ScreenManager::class.java).push(screen)
        val lifecycle = screen.lifecycle as LifecycleRegistry
        lifecycle.currentState = Lifecycle.State.CREATED
        lifecycle.currentState = Lifecycle.State.STARTED
        val row = (screen.onGetTemplate() as ListTemplate).singleList!!.items.first { (it as Row).title.toString().startsWith("Song") } as Row
        row.onClickDelegate!!.sendClick(done)
        assertTrue(screen.onGetTemplate() is MapWithContentTemplate)
        context.onBackPressedDispatcher.onBackPressed()
        assertTrue(screen.onGetTemplate() is ListTemplate)
        assertEquals(1, player.hides)
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }

    @Test fun browseExposesSearchAndCuratedCollections() {
        val context = TestCarContext.createCarContext(app)
        val screen = CarLyricsScreen(context, FakeCatalog(videos), FakePlayer(), SavedVideos(context))
        val browse = screen.onGetTemplate() as ListTemplate
        assertEquals("Search", browse.actionStrip!!.actions.single().title.toString())
        browse.actionStrip!!.actions.single().onClickDelegate!!.sendClick(done)
        val search = screen.onGetTemplate() as androidx.car.app.model.SearchTemplate
        assertEquals("Song, artist, album, or genre", search.searchHint)
    }

    private class FakeCatalog(override val videos: List<KaraokeVideo>) : VideoCatalog {
        override val loading = false
        override val error: String? = null
        override fun refresh(done: () -> Unit) = done()
    }

    private class FakePlayer : VideoPlayer {
        override var onStatus: ((PlaybackStatus) -> Unit)? = null
        var selected: KaraokeVideo? = null
        var pauses = 0
        var resumes = 0
        var hides = 0
        override fun select(video: KaraokeVideo) { selected = video }
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun hide() { hides++ }
        override fun close() {}
        override fun onSurfaceAvailable(container: SurfaceContainer) {}
        override fun onSurfaceDestroyed(container: SurfaceContainer) {}
    }
}
