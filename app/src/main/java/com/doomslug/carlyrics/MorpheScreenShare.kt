package com.doomslug.carlyrics

import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import androidx.car.app.SurfaceContainer

/** Morphe owns playback and login; the capture service owns its one live sharing session. */
class MorpheScreenShare(private val context: Context) : VideoPlayer {
    override var onStatus: ((PlaybackStatus) -> Unit)? = null
    override val compactControls = true
    override val statusDetail: String?
        get() = when {
            !MorpheCaptureGrant.isGranted -> MorpheCaptureGrant.message
            !MorpheMediaAccess.enabled(context) -> "Enable Morphe controls in the phone app"
            launchError != null -> launchError
            else -> null
        }
    private val main = Handler(Looper.getMainLooper())
    private var container: SurfaceContainer? = null
    private var video: KaraokeVideo? = null
    private var closed = false
    private var status = PlaybackStatus.IDLE
    private var launchError: String? = null
    private var lastDetail: String? = null
    private val sessionChanged: () -> Unit = { if (!closed && video != null) report() }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            if (video != null) report()
            main.postDelayed(this, 700)
        }
    }
    init {
        MorpheCaptureGrant.observe(sessionChanged)
        main.post(poll)
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) { main.post {
        if (closed) return@post
        this.container = container
        MorpheCaptureGrant.attach(container)
    } }
    override fun onSurfaceDestroyed(container: SurfaceContainer) { main.post {
        MorpheCaptureGrant.detach(container)
        if (this.container?.surface == container.surface) this.container = null
    } }

    override fun select(video: KaraokeVideo) {
        if (closed || !KaraokeVideo.ID.matches(video.id)) return
        this.video = video
        launchError = null
        if (statusDetail != null) { update(PlaybackStatus.ERROR); return }
        update(PlaybackStatus.LOADING)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}")).apply {
            setPackage(MorpheMediaAccess.PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // CarContext inherits the host's private display. Morphe must open on the phone.
        runCatching {
            context.applicationContext.startActivity(intent,
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle())
        }.onFailure {
            Log.e("CarLyricsMorphe", "Could not open Morphe", it)
            launchError = "Could not open Morphe. Retry or open it on your phone."
            update(PlaybackStatus.ERROR)
        }
    }
    override fun pause() {
        MorpheMediaAccess.controller(context)?.transportControls?.pause() ?: update(PlaybackStatus.ERROR)
    }
    override fun resume() {
        if (status == PlaybackStatus.ERROR) video?.let(::select)
        else if (statusDetail != null) update(PlaybackStatus.ERROR)
        else MorpheMediaAccess.controller(context)?.transportControls?.play() ?: video?.let(::select)
    }
    // Browsing must not revoke Android's one-use projection grant or interrupt Morphe audio.
    override fun hide() = Unit
    override fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        MorpheCaptureGrant.removeObserver(sessionChanged)
        container?.let(MorpheCaptureGrant::detach)
        onStatus = null
    }
    private fun report() {
        if (statusDetail != null) update(PlaybackStatus.ERROR)
        else update(MorpheMediaAccess.status(MorpheMediaAccess.controller(context)?.playbackState))
    }
    private fun update(next: PlaybackStatus) {
        if (next != status || lastDetail != statusDetail) {
            status = next
            lastDetail = statusDetail
            Log.i("CarLyricsMorphe", "playback=$next")
            onStatus?.invoke(next)
        }
    }
}
