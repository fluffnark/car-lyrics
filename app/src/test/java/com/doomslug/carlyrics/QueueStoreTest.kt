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
}
