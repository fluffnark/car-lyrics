package com.doomslug.carlyrics

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

class MirrorSurface : SurfaceCallback {
    private val worker = HandlerThread("CarLyricsSurface").apply { start() }
    private val handler = Handler(worker.looper)
    @Volatile private var surface: Surface? = null
    @Volatile private var safeArea = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 34f }
    private val render = Runnable {
        val target = surface?.takeIf { it.isValid } ?: return@Runnable
        var canvas: Canvas? = null
        try {
            canvas = target.lockCanvas(null)
            canvas.drawColor(Color.BLACK)
            val bounds = if (safeArea.isEmpty) Rect(0, 0, canvas.width, canvas.height) else safeArea
            if (MirrorState.carAuthorized && !MirrorState.moving && MirrorState.capturing) {
                MirrorState.withFrame { frame ->
                    if (frame != null && !frame.isRecycled) {
                        val scale = minOf(bounds.width().toFloat() / frame.width, bounds.height().toFloat() / frame.height)
                        val width = (frame.width * scale).toInt()
                        val height = (frame.height * scale).toInt()
                        val left = bounds.centerX() - width / 2
                        val top = bounds.centerY() - height / 2
                        canvas.drawBitmap(frame, null, Rect(left, top, left + width, top + height), paint)
                    } else canvas.drawText("Waiting for phone video", bounds.left + 24f, bounds.centerY().toFloat(), text)
                }
            } else {
                val message = when {
                    MirrorState.moving -> "Video hidden while moving"
                    !MirrorState.capturing -> "Start sharing on your phone"
                    else -> "Select Show video while parked"
                }
                canvas.drawText(message, bounds.left + 24f, bounds.centerY().toFloat(), text)
            }
        } catch (_: RuntimeException) {
            // The host may replace this surface during a frame.
        } finally { if (canvas != null) runCatching { target.unlockCanvasAndPost(canvas) } }
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) { surface = container.surface; redraw() }
    override fun onSurfaceDestroyed(container: SurfaceContainer) { handler.removeCallbacks(render); surface = null }
    override fun onVisibleAreaChanged(visibleArea: Rect) { safeArea = Rect(visibleArea); redraw() }
    override fun onStableAreaChanged(stableArea: Rect) { safeArea = Rect(stableArea); redraw() }
    fun redraw() { handler.removeCallbacks(render); handler.post(render) }
    fun close() { handler.removeCallbacksAndMessages(null); surface = null; worker.quitSafely() }
}
