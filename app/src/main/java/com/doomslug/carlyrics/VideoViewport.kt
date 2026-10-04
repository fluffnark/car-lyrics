package com.doomslug.carlyrics

import android.graphics.Rect

/** Fits the whole frame within the host's unobscured area, in Android surface coordinates. */
internal object VideoViewport {
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
