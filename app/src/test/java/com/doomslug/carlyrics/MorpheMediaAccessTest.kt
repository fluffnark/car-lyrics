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
    @Test fun favoritesFollowMorpheAutoplayMetadataInsteadOfThePreviousSelection() {
        val previous = KaraokeVideo("9Lxm0iSnKNc", "Previous video")
        val metadata = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Current video")
            .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID, "dqIil7XK3V4").build()
        assertEquals(KaraokeVideo("dqIil7XK3V4", "Current video"), MorpheMediaAccess.videoToSave(metadata, previous))
        val uri = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, "Current video")
            .putString(android.media.MediaMetadata.METADATA_KEY_MEDIA_URI, "https://www.youtube.com/watch?v=dqIil7XK3V4").build()
        assertEquals("dqIil7XK3V4", MorpheMediaAccess.videoToSave(uri, previous)!!.id)
    }

    @Test fun missingVideoIdCannotSaveAStaleSelection() {
        val selected = KaraokeVideo("9Lxm0iSnKNc", "Selected video")
        fun title(value: String) = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, value).build()
        assertNull(MorpheMediaAccess.videoToSave(null, selected))
        assertNull(MorpheMediaAccess.videoToSave(title("Autoplay video"), selected))
        assertNull(MorpheMediaAccess.videoToSave(title(""), selected))
        assertEquals(selected, MorpheMediaAccess.videoToSave(title("Selected video"), selected))
    }

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

    @Test fun timelineAdvancesAndSeekRespectsDurationAndCapability() {
        val app = RuntimeEnvironment.getApplication()
        val morphe = MediaSession(app, "morphe").controller
        val controller = shadowOf(morphe)
        controller.setPackageName(MorpheMediaAccess.PACKAGE)
        controller.setMetadata(android.media.MediaMetadata.Builder()
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 60_000).build())
        shadowOf(app.getSystemService(MediaSessionManager::class.java)).addController(morphe)
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(1))
        controller.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SEEK_TO)
            .setState(PlaybackState.STATE_PLAYING, 2000, 1f, android.os.SystemClock.elapsedRealtime()).build())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofSeconds(5))
        assertEquals(7000L, MorpheMediaAccess.timeline(app)!!.positionMs)
        val transport = shadowOf(morphe.transportControls)
        MorpheMediaAccess.seekTo(app, 99_000)
        assertEquals(60_000L, transport.seekToPositionMs)
        MorpheMediaAccess.seekTo(app, -100)
        assertEquals(0L, transport.seekToPositionMs)
        controller.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY)
            .setState(PlaybackState.STATE_PAUSED, 7000, 0f).build())
        assertFalse(MorpheMediaAccess.timeline(app)!!.seekable)
        MorpheMediaAccess.seekTo(app, 20_000)
        assertEquals(0L, transport.seekToPositionMs)
    }

    @Test fun recoveryWaitsForANewReadySessionBeforeRestoringPosition() {
        val app = RuntimeEnvironment.getApplication()
        val manager = shadowOf(app.getSystemService(MediaSessionManager::class.java))
        val old = MediaSession(app, "old").controller
        shadowOf(old).setPackageName(MorpheMediaAccess.PACKAGE)
        manager.addController(old)
        val recovery = MorpheRecovery(old.sessionToken, 108_000, false)
        assertFalse(recovery.complete(app))
        shadowOf(old).setPackageName("not.active.morphe")
        val current = MediaSession(app, "new").controller
        val shadow = shadowOf(current)
        shadow.setPackageName(MorpheMediaAccess.PACKAGE)
        manager.addController(current)
        assertFalse(recovery.complete(app))
        shadow.setMetadata(android.media.MediaMetadata.Builder()
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, 375_000).build())
        shadow.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_SEEK_TO)
            .setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        assertTrue(recovery.complete(app))
        assertEquals(108_000L, shadowOf(current.transportControls).seekToPositionMs)
        assertEquals(PlaybackState.ACTION_PAUSE, shadowOf(current.transportControls).lastPerformedAction)
    }
}
