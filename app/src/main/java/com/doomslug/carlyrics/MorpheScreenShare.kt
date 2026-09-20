package com.doomslug.carlyrics

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.view.Surface
import androidx.car.app.SurfaceContainer

/** Experimental phone-display mirror for Morphe. Requires explicit phone consent. */
class MorpheScreenShare(private val context: Context) : VideoPlayer {
    override var onStatus: ((PlaybackStatus) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var video: KaraokeVideo? = null
    private var closed = false

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        main.post {
            if (closed) return@post
            surface = container.surface
            width = container.width
            height = container.height
            dpi = container.dpi
            startCapture()
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        main.post {
            if (surface == container.surface) {
                stopCapture()
                surface = null
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) = Unit
    override fun onStableAreaChanged(stableArea: Rect) = Unit

    override fun select(video: KaraokeVideo) {
        this.video = video
        update(PlaybackStatus.LOADING)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}")).apply {
            setPackage("app.morphe.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { update(PlaybackStatus.ERROR); return }
        main.postDelayed({ if (!closed) startCapture() }, 1_500L)
    }

    override fun pause() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
        update(PlaybackStatus.PAUSED)
    }

    override fun resume() {
        sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
        update(PlaybackStatus.PLAYING)
    }

    override fun hide() { main.post { stopCapture(); update(PlaybackStatus.IDLE) } }
    override fun close() { closed = true; main.post { stopCapture(); onStatus = null } }

    private fun startCapture() {
        val target = surface
        if (target?.isValid != true || width <= 0 || height <= 0) return
        if (!MorpheCaptureGrant.isGranted) { update(PlaybackStatus.ERROR); return }
        val intent = Intent(context, MorpheProjectionService::class.java).apply {
            action = MorpheProjectionService.ACTION_START
            putExtra(MorpheProjectionService.EXTRA_RESULT_CODE, MorpheCaptureGrant.resultCode)
            putExtra(MorpheProjectionService.EXTRA_DATA, MorpheCaptureGrant.data)
            putExtra(MorpheProjectionService.EXTRA_SURFACE, target)
            putExtra(MorpheProjectionService.EXTRA_WIDTH, width)
            putExtra(MorpheProjectionService.EXTRA_HEIGHT, height)
            putExtra(MorpheProjectionService.EXTRA_DPI, dpi)
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            update(PlaybackStatus.PLAYING)
        }.onFailure { update(PlaybackStatus.ERROR) }
    }

    private fun stopCapture() {
        context.stopService(Intent(context, MorpheProjectionService::class.java))
    }

    private fun sendMediaKey(code: Int) {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code)
        audio.dispatchMediaKeyEvent(down)
        audio.dispatchMediaKeyEvent(up)
    }

    private fun update(next: PlaybackStatus) { onStatus?.invoke(next) }
}
