package com.doomslug.carlyrics

import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MorpheMediaAccessTest {
    @Test fun controlsSelectMorpheWhenAnotherPlayerIsAlsoActive() {
        val app = RuntimeEnvironment.getApplication()
        val manager = shadowOf(app.getSystemService(MediaSessionManager::class.java))
        val spotify = MediaSession(app, "spotify").controller
        shadowOf(spotify).setPackageName("com.spotify.music")
        manager.addController(spotify)
        assertNull(MorpheMediaAccess.controller(app))
        val morphe = MediaSession(app, "morphe").controller
        shadowOf(morphe).setPackageName(MorpheMediaAccess.PACKAGE)
        manager.addController(morphe)
        assertSame(morphe, MorpheMediaAccess.controller(app))
    }

    @Test fun absentOrBufferingSessionDoesNotClaimPlayback() {
        assertEquals(PlaybackStatus.LOADING, MorpheMediaAccess.status(null))
        assertEquals(PlaybackStatus.LOADING, MorpheMediaAccess.status(PlaybackState.Builder()
            .setState(PlaybackState.STATE_BUFFERING, 0, 1f).build()))
        assertEquals(PlaybackStatus.PAUSED, MorpheMediaAccess.status(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PAUSED, 2000, 0f).build()))
        assertEquals(PlaybackStatus.PLAYING, MorpheMediaAccess.status(PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, 2000, 1f).build()))
    }
}
