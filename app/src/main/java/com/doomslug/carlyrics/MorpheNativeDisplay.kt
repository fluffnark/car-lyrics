package com.doomslug.carlyrics

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.car.app.SurfaceContainer
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

/** The capture input remains 16:9 while AA replaces, hides, or resizes its output. */
internal class MorpheNativeDisplay(private val context: Context, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val lifetime = Binder()
    private val args = Shizuku.UserServiceArgs(ComponentName(context, NativeMorpheService::class.java))
        .daemon(false).processNameSuffix("native_morphe").version(BuildConfig.VERSION_CODE)
    private var remote: INativeMorphe? = null
    private var renderer: MorpheVideoRenderer? = null
    private var target: SurfaceContainer? = null
    private var visible: Rect? = null
    private var bound = false
    private var closed = false
    private var pending: Pair<KaraokeVideo, Long>? = null
    private var ready = false
    private var initializing = false
    private var failure: String? = null
    val detail: String? get() = when {
        !authorized() -> "Enable native Morphe in the phone app once"
        failure != null -> failure
        !ready -> "Starting native Morphe…"
        else -> null
    }
    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (closed || binder == null) return
            remote = INativeMorphe.Stub.asInterface(binder)
            main.removeCallbacks(bindTimeout)
            initializeDisplay()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            ready = false
            bound = false
            initializing = false
            failure = "Native player disconnected. Use Repair picture or restart Shizuku."
            changed()
        }
    }
    fun attach(container: SurfaceContainer) {
        if (target?.surface != container.surface || target?.width != container.width || target?.height != container.height) visible = null
        target = container
        renderer?.attach(container, visible)
    }
    fun detach(container: SurfaceContainer) {
        if (target?.surface == container.surface) { target = null; visible = null; renderer?.detach() }
    }
    fun visibleArea(area: Rect) { visible = Rect(area); renderer?.setVisibleArea(area) }
    fun play(video: KaraokeVideo, positionMs: Long = 0, recover: Boolean = false) {
        if (closed) return
        pending = video to positionMs
        failure = null
        if (!authorized()) { changed(); return }
        if (recover && remote != null) { ready = false; initializeDisplay(); return }
        if (ready) { dispatchPending(); return }
        if (remote != null && !initializing) { initializeDisplay(); return }
        if (!bound) {
            try {
                bound = true
                Shizuku.bindUserService(args, connection)
                main.postDelayed(bindTimeout, 12_000)
            } catch (error: Exception) { bound = false; failed(error) }
        }
        changed()
    }
    private val bindTimeout: Runnable = Runnable {
        if (!closed && remote == null) {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            bound = false
            failed(IllegalStateException("Shizuku connection timed out"))
        }
    }
    private fun initializeDisplay() {
        val service = remote ?: return
        if (initializing) return
        initializing = true
        try { if (renderer == null) renderer = MorpheVideoRenderer(1280, 720) }
        catch (error: Exception) { initializing = false; failed(error); return }
        val output = renderer!!
        target?.let { output.attach(it, visible) }
        worker.execute {
            runCatching { service.create(output.input, lifetime) }
                .onSuccess { id -> main.post {
                    if (!closed) {
                        Log.i(TAG, "native display=$id 1280x720")
                        ready = true
                        initializing = false
                        failure = null
                        dispatchPending()
                        changed()
                    }
                } }
                .onFailure { error -> main.post { failed(error) } }
        }
    }
    private fun dispatchPending() {
        val request = pending ?: return
        val service = remote ?: return
        pending = null
        worker.execute {
            runCatching { service.play(request.first.id, request.second) }
                .onSuccess { Log.i(TAG, "native video=${request.first.id}") }
                .onFailure { error -> main.post { failed(error) } }
        }
    }
    private fun failed(error: Throwable) {
        if (closed) return
        Log.e(TAG, "Native display failed", error)
        failure = "Native player could not open. Use Repair picture or check Shizuku on the phone."
        ready = false
        initializing = false
        changed()
    }
    fun close() {
        closed = true
        main.removeCallbacks(bindTimeout)
        val service = remote
        worker.execute {
            runCatching { service?.release() }
            main.post {
                if (bound) runCatching { Shizuku.unbindUserService(args, connection, true) }
                renderer?.close()
                renderer = null
            }
        }
        worker.shutdown()
    }
    companion object {
        private const val TAG = "CarLyricsNative"
        fun enabled(context: Context) = context.getSharedPreferences("car_lyrics", 0).getBoolean("native_morphe", false)
        fun authorized() = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    }
}
