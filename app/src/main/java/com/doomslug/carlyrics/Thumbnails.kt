package com.doomslug.carlyrics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Fetches only the small thumbnails shown on the current car page. */
object Thumbnails {
    private val worker = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val unavailable = mutableSetOf<String>()
    private val pending = mutableMapOf<String, MutableSet<() -> Unit>>()

    fun get(id: String): Bitmap? = cache.get(id)

    /** Publish a newly loaded list once its images settle, avoiding redraws during rotary browsing. */
    fun requestBatch(videos: List<KaraokeVideo>, done: () -> Unit) {
        var delivered = false
        fun check() {
            if (!delivered && videos.none { it.id in pending }) { delivered = true; done() }
        }
        request(videos, ::check)
        check()
    }

    fun request(videos: List<KaraokeVideo>, changed: () -> Unit) {
        videos.forEach { video ->
            if (cache.get(video.id) != null || video.id in unavailable) return@forEach
            val listeners = pending[video.id]
            if (listeners != null) { listeners.add(changed); return@forEach }
            pending[video.id] = mutableSetOf(changed)
            worker.execute {
                val bitmap = runCatching {
                    val connection = URL("https://i.ytimg.com/vi/${video.id}/mqdefault.jpg").openConnection() as HttpURLConnection
                    connection.connectTimeout = 5_000
                    connection.readTimeout = 5_000
                    try {
                        connection.inputStream.use { input ->
                            val original = BitmapFactory.decodeStream(input) ?: error("No image")
                            val scaled = Bitmap.createScaledBitmap(original, 160, 90, true)
                            if (scaled !== original) original.recycle()
                            scaled
                        }
                    } finally { connection.disconnect() }
                }.getOrNull()
                main.post {
                    val listeners = pending.remove(video.id).orEmpty()
                    if (bitmap != null) cache.put(video.id, bitmap) else unavailable.add(video.id)
                    listeners.forEach { it() }
                }
            }
        }
    }
}
