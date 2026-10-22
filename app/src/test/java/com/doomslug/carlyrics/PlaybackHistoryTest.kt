package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackHistoryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Test fun progressAndRecencySurviveReloadWithoutDuplicatesOrPausedVideoReordering() {
        val store = PlaybackHistory(app); store.clear()
        val a = KaraokeVideo("abcdefghijk", "First"); val b = KaraokeVideo("12345678901", "Second")
        store.opened(a, now = 10); store.opened(b, now = 20)
        store.checkpoint(a, VideoTimeline(42_000, 180_000, true), now = 30)
        store.checkpoint(a, VideoTimeline(0, 0, false), now = 31)
        val fresh = PlaybackHistory(app).all()
        assertEquals(listOf(b, a), fresh.map { it.video })
        assertEquals(42_000L, fresh.last().positionMs)
        assertEquals(10L, fresh.last().openedAt)
        store.opened(a, 42_000, now = 40)
        assertEquals(listOf(a, b), store.all().map { it.video })
        assertEquals(2, store.all().size)
    }
    @Test fun boundsValidatesAndAllowsPrivateLocalHistoryRemoval() {
        val store = PlaybackHistory(app); store.clear()
        repeat(220) { store.opened(KaraokeVideo("%011d".format(it), "Video $it"), now = it.toLong()) }
        store.opened(KaraokeVideo("bad", "Invalid")); store.opened(KaraokeVideo("abcdefghijk", ""))
        assertEquals(PlaybackHistory.LIMIT, store.all().size)
        val current = store.all().first().video
        store.checkpoint(current, VideoTimeline(999, 100, true))
        assertEquals(100L, store.all().first().positionMs)
        store.remove(current.id); assertFalse(PlaybackHistory(app).all().any { it.video.id == current.id })
        store.clear(); assertTrue(PlaybackHistory(app).all().isEmpty())
    }
}
