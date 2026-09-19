package com.doomslug.carlyrics

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedVideosTest {
    private val app get() = RuntimeEnvironment.getApplication() as Application
    @Before fun clear() { app.getSharedPreferences("saved_videos", 0).edit().clear().commit() }

    @Test fun savingAndRemovingPersistsAcrossStoreInstances() {
        val video = KaraokeVideo("abcdefghijk", "My karaoke song")
        assertTrue(SavedVideos(app).toggle(video))
        assertEquals(listOf(video), SavedVideos(app).all())
        assertTrue(SavedVideos(app).contains(video.id))
        assertFalse(SavedVideos(app).toggle(video))
        assertTrue(SavedVideos(app).all().isEmpty())
    }
}
