package com.doomslug.carlyrics

import androidx.car.app.SurfaceContainer
import android.graphics.Rect

/** Process-local session state. Consent intents are consumed by the service and never cached. */
object MorpheCaptureGrant {
    internal var service: MorpheProjectionService? = null
    internal var target: SurfaceContainer? = null
    internal var visibleArea: Rect? = null
    internal var useCompactHeader = false
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
        if (target?.surface != container.surface || target?.width != container.width || target?.height != container.height) visibleArea = null
        target = container
        service?.attach(container)
    }
    fun updateVisibleArea(area: Rect) {
        visibleArea = Rect(area)
        service?.updateVisibleArea(area)
    }
    fun setCompactHeader(enabled: Boolean) {
        useCompactHeader = enabled
        service?.setCompactHeader(enabled)
    }
    fun detach(container: SurfaceContainer) {
        if (target?.surface == container.surface) {
            target = null
            visibleArea = null
            service?.detach()
        }
    }
}
