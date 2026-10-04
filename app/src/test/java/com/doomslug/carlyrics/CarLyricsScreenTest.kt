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
@Config(sdk = [35])
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
        val playing = manager.top
        repeat(8) {
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Queue")
            returnToVideo()
            assertSame(playing, manager.top)
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Playlists")
            click("My karaoke mix")
            returnToVideo()
            assertSame(playing, manager.top)
            controls().actions[3].onClickDelegate!!.sendClick(done)
            click("Search YouTube")
            val search = manager.top.onGetTemplate() as SearchTemplate
            search.actionStrip!!.actions.single().onClickDelegate!!.sendClick(done)
            assertSame(playing, manager.top)
            assertEquals(2, manager.screenStack.size)
        }
        assertEquals(listOf(videos.first()), player.selections)
        assertEquals(0, player.hides)
        assertEquals(0, player.closes)
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
        // The test host reports a short list limit, so the timeline uses 40-second steps.
        assertEquals(160_000L, player.sought)
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
        controls().actions[0].onClickDelegate!!.sendClick(done)
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
        val playing = manager.top
        controls().actions[3].onClickDelegate!!.sendClick(done)
        returnToVideo()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val appManager = context.getCarService(androidx.car.app.AppManager::class.java) as androidx.car.app.testing.TestAppManager
        appManager.reset()
        player.detail = "Recovering picture"
        player.onStatus!!.invoke(PlaybackStatus.LOADING)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(appManager.templatesReturned.any { it.first === playing &&
            (it.second as? NavigationTemplate)?.navigationInfo is androidx.car.app.navigation.model.MessageInfo })
    }

    private class FakeCatalog(override val videos: List<KaraokeVideo>) : VideoCatalog {
        override val loading = false
        override val error = null
        override fun refresh(done: () -> Unit) = done()
    }
    private class FakePlayer : VideoPlayer {
        override val compactControls = true
        override var onStatus: ((PlaybackStatus) -> Unit)? = null
        override val timeline = VideoTimeline(20_000, 240_000, true)
        var detail: String? = null
        override val statusDetail get() = detail
        val selections = mutableListOf<KaraokeVideo>()
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
