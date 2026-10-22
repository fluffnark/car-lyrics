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
    private var compactHeader = false
    private var bound = false
    private var closed = false
    private var pending: Pair<KaraokeVideo, Long>? = null
    private var pendingHome = false
    private var homeRequested = false
    private var ready = false
    private var initializing = false
    private var failure: String? = null
    val detail: String? get() = when {
        !running() -> "Shizuku stopped. Open Shizuku on the phone and tap Start."
        !authorized() -> "Allow this Car Lyrics app in Shizuku on the phone"
        failure != null -> failure
        !ready -> "Starting native Morphe…"
        else -> null
    }
    private var epoch = 0
    private val binderDead = Shizuku.OnBinderDeadListener { main.post { disconnected() } }
    private val binderReceived = Shizuku.OnBinderReceivedListener { main.post {
        if (!closed) { prepare(); changed() }
    } }
    init {
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addBinderReceivedListener(binderReceived)
    }
    private fun disconnected() {
        if (closed) return
        clearBinding()
        remote = null
        ready = false
        bound = false
        initializing = false
        main.removeCallbacks(bindTimeout)
        failure = "Native player disconnected. Use Repair picture or restart Shizuku."
        changed()
    }
    private var connection: ServiceConnection? = null
    private var requestSerial = 0
    private fun newConnection(attempt: Int) = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (closed || attempt != epoch || connection !== this || binder == null) return
            remote = INativeMorphe.Stub.asInterface(binder)
            main.removeCallbacks(bindTimeout)
            if (pending != null || pendingHome) initializeDisplay()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            if (!closed && attempt == epoch && connection === this) disconnected()
        }
    }
    private fun clearBinding(removeService: Boolean = false) {
        val previous = connection
        connection = null
        bound = false
        epoch++
        main.removeCallbacks(bindTimeout)
        // remove=true stops the remote service but does not clear SDK listeners.
        // Clear client callbacks first, while still on the owning UI thread.
        if (previous != null) {
            runCatching { Shizuku.unbindUserService(args, previous, false) }
            if (removeService) runCatching { Shizuku.unbindUserService(args, previous, true) }
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
    fun setCompactHeader(enabled: Boolean) {
        compactHeader = enabled
        renderer?.setCompactHeader(enabled)
    }
    fun play(video: KaraokeVideo, positionMs: Long = 0, recover: Boolean = false) {
        if (closed) return
        requestSerial++
        pendingHome = false
        homeRequested = false
        pending = video to positionMs
        failure = null
        if (!authorized()) { changed(); return }
        if (remote?.asBinder()?.isBinderAlive == false) disconnected()
        if (recover && remote != null) { ready = false; initializeDisplay(); return }
        if (ready) { dispatchPending(); return }
        if (remote != null && !initializing) { initializeDisplay(); return }
        prepare()
        changed()
    }
    fun home() {
        if (closed) return
        requestSerial++
        homeRequested = true
        pending = null
        pendingHome = true
        failure = null
        if (!authorized()) { changed(); return }
        if (ready) dispatchPending()
        else if (remote != null && !initializing) initializeDisplay()
        else prepare()
    }
    fun recommendations(more: Boolean, done: (String?, String?) -> Unit) {
        if (!authorized()) { done(null, "Start Shizuku and allow Car Lyrics in Setup."); return }
        if (!homeRequested || !more) home()
        fun read(attempt: Int, emptyRetries: Int = 0) {
            if (closed) { done(null, "Home connection closed."); return }
            val service = remote
            if (!ready || service == null) {
                if (attempt >= 48) done(null, detail ?: "Morphe Home did not start. Retry.")
                else main.postDelayed({ read(attempt + 1) }, 250)
                return
            }
            worker.execute {
                val result = runCatching { service.recommendations(more && emptyRetries == 0) }
                main.post { result.onSuccess {
                    // A cold Morphe launch can expose its empty RecyclerView before the network feed arrives.
                    if (it.trim() == "[]" && emptyRetries < 3) main.postDelayed({ read(attempt, emptyRetries + 1) }, 600)
                    else done(it, null)
                }.onFailure { done(null, "Could not read Morphe Home. Retry or check Morphe setup.") } }
            }
        }
        read(0)
    }
    fun prepare() {
        if (closed || !authorized() || remote != null) return
        if (!bound) {
            try {
                // A server death can prevent unbind from clearing the SDK registry.
                runCatching { Shizuku.unbindUserService(args, connection ?: newConnection(epoch), false) }
                val listener = newConnection(++epoch)
                connection = listener
                bound = true
                Shizuku.bindUserService(args, listener)
                main.postDelayed(bindTimeout, 12_000)
            } catch (error: Exception) { bound = false; failed(error) }
        }
    }
    private val bindTimeout: Runnable = Runnable {
        if (!closed && remote == null) {
            clearBinding(removeService = true)
            failed(IllegalStateException("Shizuku connection timed out"))
        }
    }
    private fun initializeDisplay() {
        val service = remote ?: return
        if (initializing) return
        initializing = true
        val attempt = epoch
        val previousRenderer = renderer
        worker.execute {
            var output: MorpheVideoRenderer? = previousRenderer
            val started = android.os.SystemClock.elapsedRealtime()
            runCatching {
                if (output == null) output = MorpheVideoRenderer(1280, 720)
                service.create(output!!.input, lifetime)
            }.onSuccess { id -> main.post {
                    if (!closed && attempt == epoch && service === remote) {
                        renderer = output
                        output!!.setCompactHeader(compactHeader)
                        target?.let { output!!.attach(it, visible) }
                        Log.i(TAG, "native display=$id 1280x720@320 readyMs=${android.os.SystemClock.elapsedRealtime() - started}")
                        ready = true
                        initializing = false
                        failure = null
                        dispatchPending()
                        changed()
                    } else if (output !== previousRenderer) output?.close()
                } }
                .onFailure { error ->
                    if (output !== previousRenderer) output?.close()
                    main.post { if (attempt == epoch && service === remote) failed(error) }
                }
        }
    }
    private fun dispatchPending() {
        if (pendingHome) {
            val service = remote ?: return
            pendingHome = false
            worker.execute { runCatching { service.home() }.onFailure { error -> main.post { if (!closed) failed(error) } } }
            return
        }
        val request = pending ?: return
        val service = remote ?: return
        pending = null
        val serial = requestSerial
        val attempt = epoch
        worker.execute {
            val started = android.os.SystemClock.elapsedRealtime()
            runCatching { service.play(request.first.id, request.second) }
                .onSuccess { Log.i(TAG, "native video=${request.first.id} launchMs=${android.os.SystemClock.elapsedRealtime() - started}") }
                .onFailure { error -> main.post {
                    if (!closed && attempt == epoch && service === remote) {
                        if (requestSerial == serial && pending == null) pending = request
                        failed(error)
                    }
                } }
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
    fun close(done: () -> Unit = {}) {
        if (closed) { done(); return }
        closed = true
        clearBinding(removeService = true)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeBinderReceivedListener(binderReceived)
        main.removeCallbacks(bindTimeout)
        val service = remote
        worker.execute {
            runCatching { service?.release() }
            main.post {
                renderer?.close()
                renderer = null
                done()
            }
        }
        worker.shutdown()
    }
    companion object {
        private const val TAG = "CarLyricsNative"
        fun enabled(context: Context) = context.getSharedPreferences("car_lyrics", 0).getBoolean("native_morphe", false)
        fun running() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        fun authorized() = runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)
    }
}
