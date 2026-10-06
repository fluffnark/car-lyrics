package com.doomslug.carlyrics

import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
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
    override val timeline get() = MorpheMediaAccess.timeline(context)
    override fun seekTo(positionMs: Long) { MorpheMediaAccess.seekTo(context, positionMs) }
    override fun recoverVideo() {
        // Reopen the same URL at the current position without consuming capture consent again.
        val selected = video ?: return
        val playing = videoToSave(selected)
        val current = playing ?: selected
        val time = timeline
        // Do not apply an autoplay video's position to the previously selected URL.
        val position = if (playing != null && time != null && time.positionMs < time.durationMs) time.positionMs else 0
        video = current
        if (nativeMode) {
            val controller = MorpheMediaAccess.controller(context)
            recovery = MorpheRecovery(controller?.sessionToken, position,
                controller?.playbackState?.state != android.media.session.PlaybackState.STATE_PAUSED)
            native.play(current, position, recover = true)
            return
        }
        val seconds = position / 1000
        runCatching {
            context.applicationContext.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.youtube.com/watch?v=${current.id}&t=${seconds}s"))
                .setPackage(MorpheMediaAccess.PACKAGE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle())
        }.onFailure { launchError = "Could not reopen Morphe. Open it on your phone."; update(PlaybackStatus.ERROR) }
    }
    override val statusDetail: String?
        get() = when {
            !MorpheMediaAccess.enabled(context) -> "Enable Morphe controls in the phone app"
            nativeMode -> native.detail
            !MorpheCaptureGrant.isGranted -> MorpheCaptureGrant.message
            launchError != null -> launchError
            else -> null
        }
    private val main = Handler(Looper.getMainLooper())
    private var container: SurfaceContainer? = null
    private var video: KaraokeVideo? = null
    private var closed = false
    private var status = PlaybackStatus.IDLE
    private var launchError: String? = null
    private var waitingForSetup = false
    private var recovery: MorpheRecovery? = null
    private var lastDetail: String? = null
    private var lastMetadataTitle: String? = null
    private val prefs = context.getSharedPreferences("car_lyrics", 0)
    private val nativeMode get() = MorpheNativeDisplay.enabled(context)
    private var nativeDisplay: MorpheNativeDisplay? = null
    private val native get() = nativeDisplay ?: MorpheNativeDisplay(context) { if (!closed) report() }.also { nativeDisplay = it }
    private val backendChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "native_morphe" && !closed) {
            container?.let {
                if (nativeMode) { MorpheCaptureGrant.detach(it); native.attach(it) }
                else { nativeDisplay?.detach(it); MorpheCaptureGrant.attach(it) }
            }
            if (!nativeMode) { nativeDisplay?.close(); nativeDisplay = null }
            video?.let(::select)
        }
    }
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
        prefs.registerOnSharedPreferenceChangeListener(backendChanged)
        main.post(poll)
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) { main.post {
        if (closed) return@post
        Log.i("CarLyricsMorphe", "car surface=${container.width}x${container.height}@${container.dpi}")
        this.container = container
        if (nativeMode) native.attach(container) else MorpheCaptureGrant.attach(container)
    } }
    override fun onSurfaceDestroyed(container: SurfaceContainer) { main.post {
        MorpheCaptureGrant.detach(container)
        nativeDisplay?.detach(container)
        if (this.container?.surface == container.surface) this.container = null
    } }
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        val area = Rect(visibleArea)
        main.post {
            if (!closed) {
                Log.i("CarLyricsMorphe", "visible car area=$area")
                if (nativeMode) native.visibleArea(area) else MorpheCaptureGrant.updateVisibleArea(area)
            }
        }
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        Log.i("CarLyricsMorphe", "stable car area=$stableArea")
    }

    override fun prepare() {
        if (!closed && nativeMode && MorpheNativeDisplay.authorized()) native.prepare()
    }

    override fun setCompactHeader(enabled: Boolean) {
        if (nativeMode) native.setCompactHeader(enabled)
        else MorpheCaptureGrant.setCompactHeader(enabled)
    }

    override fun videoToSave(selected: KaraokeVideo?): KaraokeVideo? =
        MorpheMediaAccess.videoToSave(MorpheMediaAccess.controller(context)?.metadata, selected)

    override fun select(video: KaraokeVideo) {
        if (closed || !KaraokeVideo.ID.matches(video.id)) return
        this.video = video
        recovery = null
        launchError = null
        waitingForSetup = !MorpheMediaAccess.enabled(context) ||
            (if (nativeMode) !MorpheNativeDisplay.authorized() else !MorpheCaptureGrant.isGranted)
        if (nativeMode && !waitingForSetup) {
            update(PlaybackStatus.LOADING)
            native.play(video)
            return
        }
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
        recovery?.resume = false
        MorpheMediaAccess.controller(context)?.transportControls?.pause() ?: update(PlaybackStatus.ERROR)
    }
    override fun resume() {
        recovery?.resume = true
        if (status == PlaybackStatus.ERROR) video?.let(::select)
        else if (statusDetail != null) update(PlaybackStatus.ERROR)
        else MorpheMediaAccess.controller(context)?.transportControls?.play() ?: video?.let(::select)
    }
    // Browsing must not revoke Android's one-use projection grant or interrupt Morphe audio.
    override fun hide() { waitingForSetup = false }
    override fun close() {
        closed = true
        main.removeCallbacksAndMessages(null)
        MorpheCaptureGrant.removeObserver(sessionChanged)
        prefs.unregisterOnSharedPreferenceChangeListener(backendChanged)
        nativeDisplay?.close()
        nativeDisplay = null
        container?.let(MorpheCaptureGrant::detach)
        onStatus = null
    }
    private fun report() {
        if (recovery?.complete(context) == true) recovery = null
        // The song selected before setup should start once, as soon as consent and
        // controls are ready. Later status updates must never restart playback.
        if (waitingForSetup && MorpheMediaAccess.enabled(context) &&
            (if (nativeMode) MorpheNativeDisplay.authorized() else MorpheCaptureGrant.isGranted)) {
            video?.let(::select)
            return
        }
        if (statusDetail != null) update(PlaybackStatus.ERROR)
        else update(MorpheMediaAccess.status(MorpheMediaAccess.controller(context)?.playbackState))
    }
    private fun update(next: PlaybackStatus) {
        val title = MorpheMediaAccess.controller(context)?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        if (next != status || lastDetail != statusDetail || title != lastMetadataTitle) {
            status = next
            lastDetail = statusDetail
            lastMetadataTitle = title
            Log.i("CarLyricsMorphe", "playback=$next")
            onStatus?.invoke(next)
        }
    }
}
