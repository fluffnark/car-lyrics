package com.doomslug.carlyrics

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneNavigationTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun setup() {
        listOf("karaoke_playlists", "karaoke_queue", "playback_history").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        java.io.File(app.filesDir, "spotify_playlists").deleteRecursively()
        app.getSharedPreferences("sing_king_catalog", 0).edit().putLong("updated_at", System.currentTimeMillis())
            .putString("videos", """[{"id":"abcdefghijk","title":"A karaoke song"}]""").commit()
        CarPlaybackLink.playVideo = null; CarPlaybackLink.showPlayer = null; CarPlaybackLink.currentVideo = null; CarPlaybackLink.resumeVideo = null
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun layout(activity: MainActivity) {
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }
    private fun click(activity: MainActivity, text: String) {
        views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == text }.performClick()
        layout(activity)
    }
    @Test fun playlistAndSongPositionsSurviveTabsPlaybackAndActivityRecreation() {
        val songs = (1..35).map { KaraokeVideo("%011d".format(it), "Song $it") }
        val store = PlaylistStore(app)
        repeat(20) { store.create("Mix $it", songs) }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        try {
            var activity = controller.get(); layout(activity)
            click(activity, "Playlists")
            var scroll = views(activity.window.decorView).filterIsInstance<ScrollView>().single()
            scroll.scrollTo(0, 800)
            click(activity, "Search"); click(activity, "Playlists")
            assertEquals(800, scroll.scrollY)
            val title = views(activity.window.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Mix 10" }
            (title.parent.parent as View).performClick(); layout(activity)
            scroll.scrollTo(0, 600)
            click(activity, "Search"); click(activity, "Playlists")
            assertEquals(600, scroll.scrollY)
            controller.recreate(); activity = controller.get(); layout(activity)
            scroll = views(activity.window.decorView).filterIsInstance<ScrollView>().single()
            assertEquals(600, scroll.scrollY)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "Mix 10" })
            click(activity, "‹  Your playlists")
            assertEquals(800, scroll.scrollY)
        } finally { controller.pause().stop().destroy() }
    }
    @Test fun homeUsesMorpheAccountAndCurrentVideoWorksWithoutCarSession() {
        val video = KaraokeVideo("abcdefghijk", "Last song")
        PlaybackHistory(app).opened(video, 42_000)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible()
        try {
            val activity = controller.get(); layout(activity)
            click(activity, "Home")
            assertNull(shadowOf(activity).nextStartedActivity)
            assertTrue(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "For you" })
            views(activity.window.decorView).first { it.contentDescription == "Play current video" }.performClick()
            val current = shadowOf(activity).nextStartedActivity
            assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=42s", current.dataString)
            assertEquals(MorpheMediaAccess.PACKAGE, current.`package`)
        } finally { controller.pause().stop().destroy() }
    }
}
