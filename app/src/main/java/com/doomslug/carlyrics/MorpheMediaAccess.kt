package com.doomslug.carlyrics

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.os.SystemClock
import android.service.notification.NotificationListenerService

/** Android grants media-session access through notification access; no notification content is read. */
class MorpheMediaAccess : NotificationListenerService() {
    companion object {
        const val PACKAGE = "app.morphe.android.youtube"
        fun component(context: Context) = ComponentName(context, MorpheMediaAccess::class.java)
        fun enabled(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context))
        fun controller(context: Context): MediaController? = runCatching {
            context.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(component(context)).firstOrNull { it.packageName == PACKAGE }
        }.getOrNull()
        fun status(state: PlaybackState?): PlaybackStatus = when (state?.state) {
            PlaybackState.STATE_PLAYING -> PlaybackStatus.PLAYING
            PlaybackState.STATE_PAUSED -> PlaybackStatus.PAUSED
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> PlaybackStatus.LOADING
            PlaybackState.STATE_ERROR -> PlaybackStatus.ERROR
            PlaybackState.STATE_STOPPED -> PlaybackStatus.ENDED
            else -> PlaybackStatus.LOADING
        }
        fun timeline(context: Context): VideoTimeline? {
            val controller = controller(context) ?: return null
            val state = controller.playbackState ?: return null
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
            val elapsed = if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0)
                ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong() else 0L
            return VideoTimeline((state.position + elapsed).coerceIn(0, duration.coerceAtLeast(0)), duration,
                state.actions and PlaybackState.ACTION_SEEK_TO != 0L)
        }
        fun seekTo(context: Context, positionMs: Long) {
            val timeline = timeline(context) ?: return
            if (timeline.seekable && timeline.durationMs > 0)
                controller(context)?.transportControls?.seekTo(positionMs.coerceIn(0, timeline.durationMs))
        }
    }
}

/** URL start times can be ignored on a cold Morphe launch. Restore once the new
 * process exposes a usable media session; never seek the old or a future song. */
internal class MorpheRecovery(
    private val oldToken: MediaSession.Token?,
    private val positionMs: Long,
    var resume: Boolean,
) {
    private val deadline = SystemClock.elapsedRealtime() + 20_000
    fun complete(context: Context): Boolean {
        if (SystemClock.elapsedRealtime() > deadline) return true
        val controller = MorpheMediaAccess.controller(context) ?: return false
        if (controller.sessionToken == oldToken) return false
        val state = controller.playbackState?.state
        if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_PAUSED) return false
        val time = MorpheMediaAccess.timeline(context) ?: return false
        if (!time.seekable || time.durationMs <= 0) return false
        controller.transportControls.seekTo(positionMs.coerceIn(0, time.durationMs))
        if (resume && state == PlaybackState.STATE_PAUSED) controller.transportControls.play()
        if (!resume) controller.transportControls.pause()
        return true
    }
}
