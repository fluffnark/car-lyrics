package com.doomslug.carlyrics

import android.graphics.Rect

/** Fits the whole frame within the host's unobscured area, in Android surface coordinates. */
internal object VideoViewport {
    /** The POI player has one title row and no pane actions when playback is ready.
     * The host's visible rectangle reserves that row's entire column. The area
     * below this compact header is also usable. Keep the host's right/bottom
     * limits so a Coolwalk map or media card can never cover the video.
     */
    fun fitCompactHeader(surfaceWidth: Int, surfaceHeight: Int, frameWidth: Int, frameHeight: Int,
                         visible: Rect?, dpi: Int): Rect {
        val beside = fit(surfaceWidth, surfaceHeight, frameWidth, frameHeight, visible)
        if (visible == null || visible.isEmpty || visible.left <= 0 || visible.top <= 0 || beside.isEmpty) return beside
        val scale = dpi.coerceAtLeast(160) / 160f
        val belowHeader = Rect((12 * scale).toInt(), maxOf(visible.top, (112 * scale).toInt()), visible.right, visible.bottom)
        if (belowHeader.isEmpty) return beside
        val below = fit(surfaceWidth, surfaceHeight, frameWidth, frameHeight, belowHeader)
        return if (below.width().toLong() * below.height() > beside.width().toLong() * beside.height()) below else beside
    }

    fun fit(surfaceWidth: Int, surfaceHeight: Int, frameWidth: Int, frameHeight: Int, visible: Rect?): Rect {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return Rect()
        val bounds = Rect(0, 0, surfaceWidth, surfaceHeight)
        // The Car App API defines an empty visible area as unknown, not hidden.
        if (visible != null && !visible.isEmpty && !bounds.intersect(visible)) return Rect()
        val scale = minOf(bounds.width().toDouble() / frameWidth, bounds.height().toDouble() / frameHeight)
        val width = (frameWidth * scale).toInt().coerceAtLeast(1)
        val height = (frameHeight * scale).toInt().coerceAtLeast(1)
        val left = bounds.left + (bounds.width() - width) / 2
        val top = bounds.top + (bounds.height() - height) / 2
        return Rect(left, top, left + width, top + height)
    }
}
