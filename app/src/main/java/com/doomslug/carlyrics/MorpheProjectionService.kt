package com.doomslug.carlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Surface

/** Owns the Android screen capture while Morphe is mirrored to the car surface. */
class MorpheProjectionService : Service() {
    private var display: android.hardware.display.VirtualDisplay? = null
    private var projection: android.media.projection.MediaProjection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val data = intent?.parcelable<Intent>(EXTRA_DATA) ?: return START_NOT_STICKY
        val surface = intent.parcelable<Surface>(EXTRA_SURFACE) ?: return START_NOT_STICKY
        val width = intent.getIntExtra(EXTRA_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 0)
        val dpi = intent.getIntExtra(EXTRA_DPI, 0)
        if (!surface.isValid || width <= 0 || height <= 0 || dpi <= 0) return START_NOT_STICKY
        createChannel()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_video)
            .setContentTitle("Car Lyrics")
            .setContentText("Mirroring Morphe to the car display")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
        runCatching {
            display?.release()
            projection?.stop()
            projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(intent.getIntExtra(EXTRA_RESULT_CODE, 0), data)
            display = projection?.createVirtualDisplay(
                "Car Lyrics Morphe mirror", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null
            )
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        display?.release()
        projection?.stop()
        display = null
        projection = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Car Lyrics screen share", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private inline fun <reified T> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, T::class.java)
        else @Suppress("DEPRECATION") getParcelableExtra(key)

    companion object {
        const val ACTION_START = "com.doomslug.carlyrics.START_MORPHE_MIRROR"
        const val ACTION_STOP = "com.doomslug.carlyrics.STOP_MORPHE_MIRROR"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        const val EXTRA_SURFACE = "surface"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_DPI = "dpi"
        private const val CHANNEL = "morphe_capture"
        private const val NOTIFICATION_ID = 4108
    }
}
