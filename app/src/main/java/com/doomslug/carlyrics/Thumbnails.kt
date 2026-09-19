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
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(3 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val unavailable = mutableSetOf<String>()
    private val pending = mutableSetOf<String>()

    fun get(id: String): Bitmap? = cache.get(id)

    fun request(videos: List<KaraokeVideo>, changed: () -> Unit) {
        videos.forEach { video ->
            if (cache.get(video.id) != null || video.id in unavailable || !pending.add(video.id)) return@forEach
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
                    pending.remove(video.id)
                    if (bitmap != null) cache.put(video.id, bitmap) else unavailable.add(video.id)
                    changed()
                }
            }
        }
    }
}
