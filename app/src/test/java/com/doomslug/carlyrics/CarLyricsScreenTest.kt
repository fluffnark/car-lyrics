package com.doomslug.carlyrics

import android.app.Application
import androidx.car.app.OnDoneCallback
import androidx.car.app.ScreenManager
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [CarLyricsScreenTest.MazdaConstraints::class])
class CarLyricsScreenTest {
    private val app get() = RuntimeEnvironment.getApplication() as Application
    private val done = object : OnDoneCallback {}
    private val videos = (1..10).map { KaraokeVideo("%011d".format(it), "Song $it (Karaoke Version)") }
    private lateinit var context: TestCarContext
    private lateinit var manager: ScreenManager
    private lateinit var player: FakePlayer

    @Before fun setup() {
        listOf("saved_videos", "karaoke_playlists", "karaoke_queue").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        context = TestCarContext.createCarContext(app)
        manager = context.getCarService(ScreenManager::class.java)
        context.lifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        player = FakePlayer()
    }
    private fun start(wide: Boolean = false) {
        manager.push(CarLyricsScreen(context, FakeCatalog(videos), player, SavedVideos(context), fullscreenHost = wide))
    }
    private fun list() = manager.top.onGetTemplate() as ListTemplate
    private fun click(prefix: String) {
        val row = list().singleList!!.items.map { it as Row }.first { it.title.toString().startsWith(prefix) }
        assertFalse(row.onClickDelegate!!.isParkedOnly)
        row.onClickDelegate!!.sendClick(done)
    }
    private fun returnToVideo() {
        list().header!!.endHeaderActions.first { it.title.toString() == "Now playing" }.onClickDelegate!!.sendClick(done)
    }
    private fun controls() = when (val t = manager.top.onGetTemplate()) {
        is NavigationTemplate -> t.actionStrip!!
        is MapWithContentTemplate -> t.actionStrip!!
        else -> error("Not player")
    }

    @Test fun returnFromEveryMenuKeepsTheSameVideoAndPosition() {
        start(true)
        click("Song 1")
        repeat(8) {
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Queue")
            returnToVideo()
            assertTrue(manager.top.onGetTemplate() is NavigationTemplate)
            assertTrue(manager.screenStack.size <= 4)
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Playlists")
            click("My karaoke mix")
            returnToVideo()
            assertTrue(manager.top.onGetTemplate() is NavigationTemplate)
            assertTrue(manager.screenStack.size <= 4)
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Search YouTube")
            val search = manager.top.onGetTemplate() as SearchTemplate
            search.actionStrip!!.actions.single().onClickDelegate!!.sendClick(done)
            assertTrue(manager.top.onGetTemplate() is NavigationTemplate)
            assertTrue(manager.screenStack.size <= 4)
            assertTrue(manager.screenStack.size <= 3)
        }
        assertEquals(listOf(videos.first()), player.selections)
        assertEquals(0, player.hides)
        assertEquals(0, player.closes)
        assertEquals(1, player.prepares)
    }

    @Test fun backReturnsTheOriginalTemplateAndMenuContent() {
        start()
        click("Playlists")
        val playlists = manager.top
        click("My karaoke mix")
        manager.pop()
        assertSame(playlists, manager.top)
        assertEquals("Your playlists", list().header!!.title.toString())
        manager.pop()
        assertEquals("Sing King • Recent", list().header!!.title.toString())
    }

    @Test fun seekUsesKnobSelectionAndReturnDoesNotReloadVideo() {
        start(true)
        click("Song 1")
        val playing = manager.top
        val full = playing.onGetTemplate() as NavigationTemplate
        full.mapActionStrip!!.actions[0].onClickDelegate!!.sendClick(done)
        val timeline = list().singleList!!
        assertTrue(timeline.items.size > 4)
        timeline.onSelectedDelegate!!.sendSelected(4, done)
        // A full-size list can offer 10-second positions.
        assertEquals(40_000L, player.sought)
        returnToVideo()
        assertSame(playing, manager.top)
        assertEquals(1, player.selections.size)
        (manager.top.onGetTemplate() as NavigationTemplate).mapActionStrip!!.actions[1].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.repairs)
    }

    @Test fun compactControlsFollowRealPlaybackStateAndWrapQueue() {
        start(true)
        click("Song 1")
        player.onStatus!!.invoke(PlaybackStatus.PLAYING)
        assertTrue(controls().actions.all { !it.onClickDelegate!!.isParkedOnly })
        controls().actions[1].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.pauses)
        player.onStatus!!.invoke(PlaybackStatus.PAUSED)
        controls().actions[1].onClickDelegate!!.sendClick(done)
        assertEquals(1, player.resumes)
        controls().actions[3].onClickDelegate!!.sendClick(done)
        click("Previous video")
        assertEquals(videos.last(), player.selections.last())
        controls().actions[2].onClickDelegate!!.sendClick(done)
        assertEquals(videos.first(), player.selections.last())
    }

    @Test fun setupErrorAndRecoveryKeepPlayerTemplateTypeStable() {
        start(true)
        player.detail = "Start sharing on your phone"
        click("Song 1")
        assertTrue(manager.top.onGetTemplate() is NavigationTemplate)
        controls().actions[3].onClickDelegate!!.sendClick(done)
        player.detail = null
        returnToVideo()
        assertTrue(manager.top.onGetTemplate() is NavigationTemplate)
    }

    @Test fun returningFromMenuRoutesLiveStatusToThePlayer() {
        start(true)
        click("Song 1")
        controls().actions[3].onClickDelegate!!.sendClick(done)
        returnToVideo()
        val playing = manager.top
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val appManager = context.getCarService(androidx.car.app.AppManager::class.java) as androidx.car.app.testing.TestAppManager
        appManager.reset()
        player.detail = "Recovering picture"
        player.onStatus!!.invoke(PlaybackStatus.LOADING)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(appManager.templatesReturned.any { it.first === playing &&
            (it.second as? NavigationTemplate)?.navigationInfo is androidx.car.app.navigation.model.MessageInfo })
    }

    @Test fun phoneQueueChangesReachTheCarAndNextUsesTheFreshOrder() {
        val store = QueueStore(app)
        store.add(videos[0]); store.add(videos[1])
        start()
        click("Queue")
        assertEquals(2, list().singleList!!.items.size)
        store.add(videos[2])
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300))
        assertEquals(3, list().singleList!!.items.size)
        click("Song 2")
        assertEquals(videos[1], player.selections.last())
        store.move(videos[2].id, -1)
        controls().actions[2].onClickDelegate!!.sendClick(done)
        assertEquals(videos[0], player.selections.last())
        assertTrue(CarPlaybackLink.playVideo!!.invoke(videos[2]))
        assertEquals(videos[2], player.selections.last())
        assertTrue(manager.screenStack.size <= 3)
    }

    @Test fun bufferingDoesNotRedrawMenusAndMicWorksFromNestedLists() {
        start()
        click("Song 1")
        controls().actions[3].onClickDelegate!!.sendClick(done)
        click("Playlists"); click("My karaoke mix")
        val menu = manager.top
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val appManager = context.getCarService(androidx.car.app.AppManager::class.java) as androidx.car.app.testing.TestAppManager
        appManager.reset()
        player.onStatus!!.invoke(PlaybackStatus.LOADING)
        player.onStatus!!.invoke(PlaybackStatus.PLAYING)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(appManager.templatesReturned.none { it.first === menu })
        list().header!!.endHeaderActions.last().onClickDelegate!!.sendClick(done)
        // The host must see the root Back template before the new search push.
        assertTrue(manager.top.onGetTemplate() is ListTemplate)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(manager.top.onGetTemplate() is SearchTemplate)
        assertEquals(2, manager.screenStack.size)
    }

    @Test fun poiPlayerStatusAndSongChangesPreservePaneRefreshIdentity() {
        start()
        click("Song 1")
        fun pane() = ((manager.top.onGetTemplate() as MapWithContentTemplate).contentTemplate as androidx.car.app.model.PaneTemplate).pane!!
        val originalTitles = pane().rows.map { it.title.toString() }
        repeat(8) {
            player.onStatus!!.invoke(PlaybackStatus.LOADING)
            assertEquals(originalTitles, pane().rows.map { it.title.toString() })
            player.onStatus!!.invoke(PlaybackStatus.PLAYING)
            assertEquals(originalTitles, pane().rows.map { it.title.toString() })
            controls().actions[2].onClickDelegate!!.sendClick(done)
            assertEquals(originalTitles, pane().rows.map { it.title.toString() })
        }
        controls().actions[0].onClickDelegate!!.sendClick(done)
        assertEquals("Sing King • Recent", list().header!!.title.toString())
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(manager.top.onGetTemplate() is SearchTemplate)
        assertEquals(2, manager.screenStack.size)
    }

    @Test fun playerFavoriteTogglesWithoutRestartingAndAppearsInFavorites() {
        start()
        click("Song 1")
        fun favorite() = (manager.top.onGetTemplate() as MapWithContentTemplate).mapController!!.mapActionStrip!!.actions[2]
        val original = favorite().icon
        favorite().onClickDelegate!!.sendClick(done)
        assertTrue(SavedVideos(app).contains(videos.first().id))
        assertNotEquals(original, favorite().icon)
        assertEquals(1, player.selections.size)
        controls().actions[3].onClickDelegate!!.sendClick(done)
        click("Favorites")
        click("Song 1")
        favorite().onClickDelegate!!.sendClick(done)
        assertFalse(SavedVideos(app).contains(videos.first().id))
        val map = (manager.top.onGetTemplate() as MapWithContentTemplate).mapController!!
        assertEquals(3, map.mapActionStrip!!.actions.size)
        assertTrue(player.hasCompactHeader)
        val pane = ((manager.top.onGetTemplate() as MapWithContentTemplate).contentTemplate as androidx.car.app.model.PaneTemplate).pane!!
        assertTrue(pane.rows.single().texts.isEmpty())
    }

    @org.robolectric.annotation.Implements(androidx.car.app.constraints.ConstraintManager::class)
    class MazdaConstraints {
        @org.robolectric.annotation.Implementation
        fun getContentLimit(type: Int): Int = 100
    }

    private class FakeCatalog(override val videos: List<KaraokeVideo>) : VideoCatalog {
        override val loading = false
        override val error = null
        override fun refresh(done: () -> Unit) = done()
    }
    private class FakePlayer : VideoPlayer {
        override val compactControls = true
        var hasCompactHeader = false
        override fun setCompactHeader(enabled: Boolean) { hasCompactHeader = enabled }
        override var onStatus: ((PlaybackStatus) -> Unit)? = null
        override val timeline = VideoTimeline(20_000, 240_000, true)
        var detail: String? = null
        override val statusDetail get() = detail
        val selections = mutableListOf<KaraokeVideo>()
        var prepares = 0
        override fun prepare() { prepares++ }
        var pauses = 0; var resumes = 0; var hides = 0; var closes = 0; var repairs = 0
        var sought = -1L
        override fun select(video: KaraokeVideo) { selections += video }
        override fun pause() { pauses++ }
        override fun resume() { resumes++ }
        override fun hide() { hides++ }
        override fun close() { closes++ }
        override fun seekTo(positionMs: Long) { sought = positionMs }
        override fun recoverVideo() { repairs++ }
    }
}
