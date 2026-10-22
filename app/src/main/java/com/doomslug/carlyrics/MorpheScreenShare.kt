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
    private var homeVisible = false
    private var homeReturn: Pair<KaraokeVideo, Long>? = null
    private val history = PlaybackHistory(context)
    private var observedId: String? = null
    private var lastHistoryCheckpoint = 0L
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
    private var reconnectPoint: MorpheResumePoint? = null
    private var lastPoint: MorpheResumePoint? = null
    private val binderDead = rikka.shizuku.Shizuku.OnBinderDeadListener { main.post {
        if (!closed && nativeMode && video != null) {
            val controller = MorpheMediaAccess.controller(context)
            reconnectPoint = if (controller?.metadata != null) MorpheResumePoint.capture(video,
                videoToSave(video), timeline, controller.playbackState?.state == android.media.session.PlaybackState.STATE_PAUSED)
                else lastPoint ?: video?.let { MorpheResumePoint(it, 0, status != PlaybackStatus.PAUSED) }
            waitingForSetup = true
            report()
        }
    } }
    private val binderReceived = rikka.shizuku.Shizuku.OnBinderReceivedListener { main.post { if (!closed) report() } }
    private val sessionChanged: () -> Unit = { if (!closed && (video != null || homeVisible)) report() }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            if (nativeMode && !MorpheNativeDisplay.running()) ShizukuConnection.refresh(context)
            if (video != null || homeVisible) report()
            main.postDelayed(this, 700)
        }
    }
    init {
        rikka.shizuku.Shizuku.addBinderDeadListener(binderDead)
        rikka.shizuku.Shizuku.addBinderReceivedListener(binderReceived)
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

    override fun loadHome(more: Boolean, done: (String?, String?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 30) { done(null, "Native Home requires Android 11 or later."); return }
        if (closed || !nativeMode || !MorpheNativeDisplay.authorized()) {
            done(null, "Connect native Morphe in phone Setup to load Home."); return
        }
        if (!homeVisible) videoToSave(video)?.let {
            history.checkpoint(it, timeline); homeReturn = it to (timeline?.positionMs ?: 0)
        }
        homeVisible = true
        native.recommendations(more, done)
    }
    override fun showCurrentVideo(): Boolean {
        if (!homeVisible) return video != null || videoToSave(null) != null
        val entry = history.all().firstOrNull()
        val current = homeReturn?.first ?: entry?.video ?: return false
        playFrom(current, homeReturn?.second ?: entry?.positionMs ?: 0)
        return true
    }
    override fun select(video: KaraokeVideo) = playFrom(video, 0)
    override fun playFrom(video: KaraokeVideo, positionMs: Long) {
        if (closed || !KaraokeVideo.ID.matches(video.id)) return
        videoToSave(this.video)?.let { history.checkpoint(it, timeline) }
        homeVisible = false
        this.video = video
        history.opened(video, positionMs)
        lastPoint = MorpheResumePoint(video, positionMs, true)
        reconnectPoint = null
        recovery = null
        launchError = null
        waitingForSetup = !MorpheMediaAccess.enabled(context) ||
            (if (nativeMode) !MorpheNativeDisplay.authorized() else !MorpheCaptureGrant.isGranted)
        if (nativeMode && !waitingForSetup) {
            update(PlaybackStatus.LOADING)
            native.play(video, positionMs)
            return
        }
        if (statusDetail != null) { update(PlaybackStatus.ERROR); return }
        update(PlaybackStatus.LOADING)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.id}" + if (positionMs > 0) "&t=${positionMs / 1000}s" else "")).apply {
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
    override fun close() = closeAfter { }
    internal fun closeAfter(done: () -> Unit) {
        closed = true
        main.removeCallbacksAndMessages(null)
        rikka.shizuku.Shizuku.removeBinderDeadListener(binderDead)
        rikka.shizuku.Shizuku.removeBinderReceivedListener(binderReceived)
        MorpheCaptureGrant.removeObserver(sessionChanged)
        prefs.unregisterOnSharedPreferenceChangeListener(backendChanged)
        val native = nativeDisplay
        nativeDisplay = null
        if (native != null) native.close(done) else done()
        container?.let(MorpheCaptureGrant::detach)
        onStatus = null
    }
    private fun report() {
        val observed = videoToSave(video)
        val mediaStatus = MorpheMediaAccess.status(MorpheMediaAccess.controller(context)?.playbackState)
        if (observed != null && (mediaStatus == PlaybackStatus.PLAYING || mediaStatus == PlaybackStatus.PAUSED)) {
            if (observedId != observed.id && history.all().firstOrNull()?.video?.id != observed.id) history.opened(observed)
            val now = android.os.SystemClock.elapsedRealtime()
            if (observedId != observed.id || now - lastHistoryCheckpoint >= 5_000 || mediaStatus == PlaybackStatus.PAUSED && status != PlaybackStatus.PAUSED) {
                history.checkpoint(observed, timeline)
                lastHistoryCheckpoint = now
            }
            observedId = observed.id
        }
        if (recovery?.complete(context) == true) recovery = null
        // The song selected before setup should start once, as soon as consent and
        // controls are ready. Later status updates must never restart playback.
        if (waitingForSetup && MorpheMediaAccess.enabled(context) &&
            (if (nativeMode) MorpheNativeDisplay.authorized() else MorpheCaptureGrant.isGranted)) {
            val point = reconnectPoint
            reconnectPoint = null
            if (nativeMode && point != null) {
                waitingForSetup = false
                val controller = MorpheMediaAccess.controller(context)
                video = point.video
                recovery = MorpheRecovery(controller?.sessionToken, point.positionMs, point.resume)
                native.play(point.video, point.positionMs, recover = true)
            } else video?.let(::select)
            return
        }
        if (statusDetail != null) update(PlaybackStatus.ERROR)
        else {
            val controller = MorpheMediaAccess.controller(context)
            val state = controller?.playbackState?.state
            if (state == android.media.session.PlaybackState.STATE_PLAYING || state == android.media.session.PlaybackState.STATE_PAUSED)
                lastPoint = MorpheResumePoint.capture(video, videoToSave(video), timeline,
                    state == android.media.session.PlaybackState.STATE_PAUSED)
            update(MorpheMediaAccess.status(controller?.playbackState))
        }
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
