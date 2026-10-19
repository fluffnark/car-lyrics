package com.doomslug.carlyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistEditingTest {
    @Test fun emptyCreationRenameOrderAndDeletionSurviveReloadWithoutRecreatingStarterLists() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("karaoke_playlists", 0).edit().clear().commit()
        val store = PlaylistStore(app)
        assertTrue(store.create("Road trip")); assertFalse(store.create("road TRIP"))
        val a = KaraokeVideo("abcdefghijk", "Song A"); val b = KaraokeVideo("12345678901", "Song B")
        store.add("Road trip", a); store.add("Road trip", b); store.move("Road trip", b.id, -1)
        assertTrue(store.rename("Road trip", "Drive"))
        assertEquals(listOf(b, a), PlaylistStore(app).all().first { it.name == "Drive" }.videos)
        store.remove("Drive", b.id)
        assertEquals(listOf(a), store.all().first { it.name == "Drive" }.videos)
        store.delete("My karaoke mix"); store.delete("Drive")
        assertFalse(PlaylistStore(app).all().any { it.name in listOf("My karaoke mix", "Drive") })
        assertTrue(store.create("My karaoke mix"))
    }
    @Test fun acceptsProviderMetadataAndCollaboratorsButNeverLongerSongTitlesOrLiveCovers() {
        val song = SpotifySong("x", "August", listOf("flipturn"))
        val valid = KaraokeVideo("ufs_IpCt_Rg", "Flipturn - August (Funbox Karaoke, 2018)")
        assertEquals(valid, KaraokeMatcher.match(song, listOf(valid)))
        assertNull(KaraokeMatcher.match(song, listOf(valid.copy(title = "Flipturn - August Again (Funbox Karaoke, 2018)"))))
        assertNull(KaraokeMatcher.match(song, listOf(valid.copy(title = "Flipturn - August (Live Karaoke)"))))
        assertNull(KaraokeMatcher.match(song, listOf(valid.copy(title = "Flipturn - August (Cover Karaoke)"))))
        assertNotNull(KaraokeMatcher.match(SpotifySong("y", "Song (feat. Guest)", listOf("Artist", "Guest")),
            listOf(valid.copy(title = "Artist feat. Guest - Song (Karaoke Version by Provider)"))))
        assertNotNull(KaraokeMatcher.match(SpotifySong("z", "Hello (2015 Remaster)", listOf("Adele")),
            listOf(valid.copy(title = "Adele - Hello (Karaoke Version)"))))
    }
}
