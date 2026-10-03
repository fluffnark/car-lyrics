package com.doomslug.carlyrics

import androidx.car.app.SurfaceContainer

/** Process-local session state. Consent intents are consumed by the service and never cached. */
object MorpheCaptureGrant {
    internal var service: MorpheProjectionService? = null
    internal var target: SurfaceContainer? = null
    var isGranted = false
        private set
    var message = "Start Morphe sharing on your phone"
        private set
    private val listeners = linkedSetOf<() -> Unit>()

    fun observe(listener: () -> Unit) { listeners.add(listener) }
    fun removeObserver(listener: () -> Unit) { listeners.remove(listener) }
    internal fun update(active: Boolean, detail: String) {
        isGranted = active
        message = detail
        listeners.toList().forEach { it() }
    }
    fun attach(container: SurfaceContainer) {
        target = container
        service?.attach(container)
    }
    fun detach(container: SurfaceContainer) {
        if (target?.surface == container.surface) {
            target = null
            service?.detach()
        }
    }
}
