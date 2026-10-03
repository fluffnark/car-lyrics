package com.doomslug.carlyrics

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
    }
}
