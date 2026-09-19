package com.doomslug.carlyrics

import android.graphics.Bitmap
import java.util.concurrent.CopyOnWriteArraySet

/** Process-local bridge between the phone capture service and the car surface. */
object MirrorState {
    private val lock = Any()
    private var frame: Bitmap? = null
    private val frameListeners = CopyOnWriteArraySet<() -> Unit>()
    private val stateListeners = CopyOnWriteArraySet<() -> Unit>()
    @Volatile var capturing = false
        private set
    @Volatile var carAuthorized = false
    @Volatile var moving = false

    fun addFrameListener(listener: () -> Unit) { frameListeners.add(listener); listener() }
    fun removeFrameListener(listener: () -> Unit) { frameListeners.remove(listener) }
    fun addStateListener(listener: () -> Unit) { stateListeners.add(listener); listener() }
    fun removeStateListener(listener: () -> Unit) { stateListeners.remove(listener) }

    fun setCapturing(value: Boolean) {
        capturing = value
        if (!value) synchronized(lock) { frame?.recycle(); frame = null }
        stateListeners.forEach { it() }
    }

    fun publish(value: Bitmap) {
        synchronized(lock) { frame?.recycle(); frame = value }
        frameListeners.forEach { it() }
    }

    fun withFrame(block: (Bitmap?) -> Unit) = synchronized(lock) { block(frame) }
}
