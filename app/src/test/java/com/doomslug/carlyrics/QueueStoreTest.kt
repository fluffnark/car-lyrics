package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueStoreTest {
    @Test fun repeatedAddsDoNotRemoveSongsAndReorderingPersists() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("karaoke_queue", 0).edit().clear().commit()
        val store = QueueStore(app)
        val a = KaraokeVideo("abcdefghijk", "Song A")
        val b = KaraokeVideo("12345678901", "Song B")
        assertTrue(store.add(a)); assertFalse(store.add(a)); assertTrue(store.add(b))
        assertEquals(listOf(a, b), QueueStore(app).all())
        store.move(b.id, -1)
        assertEquals(listOf(b, a), QueueStore(app).all())
        store.remove(b.id)
        assertEquals(listOf(a), QueueStore(app).all())
        assertTrue(store.add(b))
        assertFalse(store.add(KaraokeVideo("bad", "Invalid")))
    }
    @Test fun playNextAdoptsCurrentSongAndMovesExistingSongWithoutDuplicates() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("karaoke_queue", 0).edit().clear().commit()
        val store = QueueStore(app)
        val current = KaraokeVideo("abcdefghijk", "Current from search")
        val next = KaraokeVideo("12345678901", "Next")
        val later = KaraokeVideo("99999999999", "Later")
        store.add(later); store.add(next)
        store.playNext(next, current)
        assertEquals(listOf(current, next, later), QueueStore(app).all())
        store.playNext(next, current)
        assertEquals(listOf(current, next, later), store.all())
        store.playNext(current, current)
        assertEquals(listOf(current, next, later), store.all())
        store.playNext(later, null)
        assertEquals(listOf(later, current, next), store.all())
    }

    @Test fun namedAlbumsArePersistentAndRepeatedAddsNeverRemoveSongs() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("karaoke_playlists", 0).edit().clear().commit()
        val store = PlaylistStore(app)
        val video = KaraokeVideo("abcdefghijk", "Song")
        assertTrue(store.add("  Road trip  ", video))
        assertFalse(store.add("Road trip", video))
        assertFalse(store.add(" ", video))
        assertFalse(store.add("Broken", KaraokeVideo("bad", "Invalid")))
        assertEquals(listOf(video), PlaylistStore(app).all().first { it.name == "Road trip" }.videos)
        assertEquals(4, store.all().size)
    }
}
