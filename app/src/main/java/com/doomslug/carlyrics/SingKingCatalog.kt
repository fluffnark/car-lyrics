package com.doomslug.carlyrics

import android.os.Handler
import android.os.Looper
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** The channel's public Atom feed contains recent uploads and needs no user login. */
object SingKingCatalog {
    data class Video(val id: String, val title: String)
    private const val FEED = "https://www.youtube.com/feeds/videos.xml?channel_id=UCwTRjvjVge51X-ILJ4i22ew"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var videos: List<Video> = emptyList()
        private set
    @Volatile var loading = false
        private set
    @Volatile var error: String? = null
        private set

    fun refresh(done: () -> Unit) {
        if (loading) return
        loading = true
        error = null
        worker.execute {
            try {
                val connection = URL(FEED).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "CarLyrics/0.2 (Android)")
                try {
                    connection.inputStream.use { input ->
                        val parser = Xml.newPullParser()
                        parser.setInput(input, "UTF-8")
                        val result = mutableListOf<Video>()
                        var inEntry = false
                        var id: String? = null
                        var title: String? = null
                        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                            when (parser.eventType) {
                                XmlPullParser.START_TAG -> when (parser.name) {
                                    "entry" -> { inEntry = true; id = null; title = null }
                                    "videoId" -> if (inEntry) id = parser.nextText()
                                    "title" -> if (inEntry) title = parser.nextText()
                                }
                                XmlPullParser.END_TAG -> if (parser.name == "entry") {
                                    if (id?.matches(Regex("[A-Za-z0-9_-]{11}")) == true && !title.isNullOrBlank())
                                        result.add(Video(id!!, title!!))
                                    inEntry = false
                                }
                            }
                            parser.next()
                        }
                        if (result.isEmpty()) error = "Sing King returned no videos"
                        else videos = result
                    }
                } finally { connection.disconnect() }
            } catch (e: Exception) { error = e.message ?: "Could not load Sing King" }
            finally { loading = false; main.post(done) }
        }
    }
}
