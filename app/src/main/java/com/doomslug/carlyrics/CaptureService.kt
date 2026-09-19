package com.doomslug.carlyrics

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock

class CaptureService : Service() {
    private val worker = HandlerThread("CarLyricsCapture").apply { start() }
    private val handler = Handler(worker.looper)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var stopping = false
    private var lastFrameAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        if (projection != null) return START_NOT_STICKY
        val resultData = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultData == null) { stopSelf(); return START_NOT_STICKY }

        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Car Lyrics is sharing the phone screen")
            .setContentText("Tap to return to Car Lyrics")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        try {
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, resultData)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
                setOnImageAvailableListener({ source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFrameAt < 200L) return@setOnImageAvailableListener
                        lastFrameAt = now
                        val plane = image.planes[0]
                        val paddedWidth = plane.rowStride / plane.pixelStride
                        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                        if (cropped !== padded) padded.recycle()
                        val scale = minOf(1f, 960f / maxOf(cropped.width, cropped.height))
                        val frame = if (scale < 1f) Bitmap.createScaledBitmap(cropped,
                            (cropped.width * scale).toInt(), (cropped.height * scale).toInt(), true) else cropped
                        if (frame !== cropped) cropped.recycle()
                        MirrorState.publish(frame)
                    } catch (_: RuntimeException) {
                        // App transitions can temporarily invalidate an acquired image.
                    } finally { image.close() }
                }, handler)
            }
            display = projection?.createVirtualDisplay("Car Lyrics", width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, handler)
            MirrorState.setCapturing(true)
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (!stopping) {
            stopping = true
            MirrorState.setCapturing(false)
            display?.release()
            reader?.close()
            projection?.stop()
            handler.removeCallbacksAndMessages(null)
            worker.quitSafely()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen sharing", NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val ACTION_START = "com.doomslug.carlyrics.START"
        const val ACTION_STOP = "com.doomslug.carlyrics.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL = "sharing"
    }
}
