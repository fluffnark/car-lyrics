package com.doomslug.carlyrics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Only video IDs and titles are stored. Video playback remains in YouTube's player. */
data class KaraokeVideo(val id: String, val title: String) {
    companion object { val ID = Regex("[A-Za-z0-9_-]{11}") }
}

data class KaraokeCollection(val kind: String, val title: String, val ids: List<String>)

interface VideoCatalog {
    val videos: List<KaraokeVideo>
    val loading: Boolean
    val error: String?
    fun refresh(done: () -> Unit)
}

internal object SingKingFeedParser {
    fun parse(input: InputStream): List<KaraokeVideo> {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")
        val result = LinkedHashMap<String, KaraokeVideo>()
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
                    val videoId = id
                    val videoTitle = title?.trim()
                    if (videoId != null && KaraokeVideo.ID.matches(videoId) && !videoTitle.isNullOrBlank()) {
                        result.putIfAbsent(videoId, KaraokeVideo(videoId, videoTitle))
                    }
                    inEntry = false
                }
            }
            parser.next()
        }
        return result.values.toList()
    }
}

/** Recent public channel uploads, kept on disk so a transient network failure retains browsing. */
object SingKingCatalog : VideoCatalog {
    private const val FEED = "https://www.youtube.com/feeds/videos.xml?channel_id=UCwTRjvjVge51X-ILJ4i22ew"
    private const val PREFS = "sing_king_catalog"
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val callbacks = mutableListOf<() -> Unit>()
    private var context: Context? = null
    override var videos: List<KaraokeVideo> = emptyList()
        private set
    override var loading = false
        private set
    override var error: String? = null
        private set
    var library: List<KaraokeVideo> = emptyList()
        private set
    var collections: List<KaraokeCollection> = emptyList()
        private set
    var lastUpdatedAt: Long = 0L
        private set

    fun initialize(appContext: Context) {
        if (context != null) return
        context = appContext.applicationContext
        val prefs = context!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastUpdatedAt = prefs.getLong("updated_at", 0L)
        val json = runCatching { JSONArray(prefs.getString("videos", "[]")) }.getOrDefault(JSONArray())
        videos = (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val title = item.optString("title")
            if (KaraokeVideo.ID.matches(id) && title.isNotBlank()) KaraokeVideo(id, title) else null
        }
        runCatching {
            val root = JSONObject(context!!.assets.open("sing_king_library.json").bufferedReader().use { it.readText() })
            val libraryJson = root.optJSONArray("songs") ?: JSONArray()
            library = (0 until libraryJson.length()).mapNotNull { index ->
                val item = libraryJson.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                val title = item.optString("title")
                if (KaraokeVideo.ID.matches(id) && title.isNotBlank()) KaraokeVideo(id, title) else null
            }
            val byId = library.associateBy { it.id }
            val groups = root.optJSONArray("collections") ?: JSONArray()
            collections = (0 until groups.length()).mapNotNull { index ->
                val group = groups.optJSONObject(index) ?: return@mapNotNull null
                val groupIds = group.optJSONArray("ids") ?: JSONArray()
                val ids = (0 until groupIds.length()).mapNotNull { i -> byId[groupIds.optString(i)]?.id }
                KaraokeCollection(group.optString("kind"), group.optString("title"), ids)
            }
        }
    }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = now - lastUpdatedAt > 6 * 60 * 60 * 1000L

    override fun refresh(done: () -> Unit) {
        callbacks.add(done)
        if (loading) return
        loading = true
        error = null
        worker.execute {
            val attempt = runCatching {
                val connection = URL(FEED).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "CarLyrics/0.3 (Android)")
                try { connection.inputStream.use(SingKingFeedParser::parse) }
                finally { connection.disconnect() }
            }
            main.post {
                attempt.onSuccess { fetched ->
                    if (fetched.isEmpty()) error = "Sing King returned no videos"
                    else {
                        videos = fetched
                        lastUpdatedAt = System.currentTimeMillis()
                        val json = JSONArray()
                        fetched.forEach { json.put(JSONObject().put("id", it.id).put("title", it.title)) }
                        context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                            ?.putString("videos", json.toString())?.putLong("updated_at", lastUpdatedAt)?.apply()
                    }
                }.onFailure { error = "Could not refresh Sing King. Check your connection." }
                loading = false
                callbacks.toList().also { callbacks.clear() }.forEach { it() }
            }
        }
    }
}
