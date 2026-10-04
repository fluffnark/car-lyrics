package com.doomslug.carlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.car.app.SurfaceContainer

/** One projection and virtual display per consent, independent of car screen/template lifetime. */
class MorpheProjectionService : Service() {
    private var display: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    private var renderer: MorpheVideoRenderer? = null
    private var captureWidth = 0
    private var captureHeight = 0
    private val main = Handler(Looper.getMainLooper())
    private var failure: String? = null
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            failure = "Sharing stopped. Start Morphe sharing again on your phone."
            stopSelf()
        }
        override fun onCapturedContentResize(width: Int, height: Int) {
            Log.i(TAG, "capture content ${width}x$height")
            resizeCapture(width, height)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        // A host reconnect may attach again, but must never consume the consent twice.
        if (projection != null) return START_NOT_STICKY
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Morphe screen sharing", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_video).setContentTitle("Morphe sharing is ready")
            .setContentText("Choose a song in Car Lyrics. Tap Stop to end sharing.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop sharing", stop).build()).build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        runCatching {
            val capture = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent!!.getIntExtra(EXTRA_RESULT_CODE, 0), data)
                ?: error("Screen sharing was not approved")
            projection = capture
            capture.registerCallback(callback, main)
            val target = MorpheCaptureGrant.target
            val metrics = resources.displayMetrics
            val scale = minOf(1f, 1280f / maxOf(metrics.widthPixels, metrics.heightPixels))
            captureWidth = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
            captureHeight = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)
            val videoRenderer = MorpheVideoRenderer(captureWidth, captureHeight)
            renderer = videoRenderer
            display = capture.createVirtualDisplay("Car Lyrics Morphe mirror",
                captureWidth, captureHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, videoRenderer.input, null, main)
            target?.let { videoRenderer.attach(it, MorpheCaptureGrant.visibleArea) }
            MorpheCaptureGrant.service = this
            MorpheCaptureGrant.update(true, "Morphe sharing ready • choose a song on the car screen")
            Log.i(TAG, "projection started; attached=${target != null}")
        }.onFailure {
            Log.e(TAG, "Projection start failed", it)
            failure = "Could not start sharing. Approve a new Morphe sharing session."
            stopSelf()
        }
        return START_NOT_STICKY
    }

    internal fun attach(container: SurfaceContainer) {
        val surface = container.surface ?: return
        if (!surface.isValid || container.width <= 0 || container.height <= 0) return
        runCatching {
            renderer?.attach(container, MorpheCaptureGrant.visibleArea)
            Log.i(TAG, "attached car surface ${container.width}x${container.height}")
        }.onFailure {
            Log.e(TAG, "Attach failed", it)
            failure = "Car display disconnected. Restart Morphe sharing."
            stopSelf()
        }
    }

    internal fun detach() {
        renderer?.detach()
        Log.i(TAG, "car surface detached; projection retained")
    }

    internal fun updateVisibleArea(area: Rect) { renderer?.setVisibleArea(area) }

    override fun onDestroy() {
        if (MorpheCaptureGrant.service === this) MorpheCaptureGrant.service = null
        display?.release()
        display = null
        renderer?.close()
        renderer = null
        projection?.unregisterCallback(callback)
        projection?.stop()
        projection = null
        MorpheCaptureGrant.update(false, failure ?: "Sharing stopped. Start Morphe sharing on your phone.")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun resizeCapture(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val scale = minOf(1f, 1280f / maxOf(width, height))
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        if (w == captureWidth && h == captureHeight) return
        captureWidth = w
        captureHeight = h
        renderer?.resize(w, h)
        display?.resize(w, h, resources.displayMetrics.densityDpi)
    }

    companion object {
        const val ACTION_START = "com.doomslug.carlyrics.START_MORPHE_MIRROR"
        const val ACTION_STOP = "com.doomslug.carlyrics.STOP_MORPHE_MIRROR"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "projection_data"
        private const val CHANNEL = "morphe_capture"
        private const val NOTIFICATION_ID = 4108
        private const val TAG = "CarLyricsMorphe"
    }
}
