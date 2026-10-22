package com.doomslug.carlyrics

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer

internal data class HomeCard(val title: String, val channel: String, val video: KaraokeVideo? = null)

/** Account recommendations come from the local Morphe UI, never a public substitute feed. */
internal object MorpheHomeFeed {
    var cards: List<HomeCard> = emptyList(); private set
    var loading = false; private set
    var error: String? = null; private set
    private val observers = mutableSetOf<() -> Unit>()
    private var phonePlayer: MorpheScreenShare? = null
    private var generation = 0
    fun observe(changed: () -> Unit) { observers += changed }
    fun unobserve(changed: () -> Unit) { observers -= changed }
    private fun changed() { observers.toList().forEach { it() } }

    // Transfer the same helper/display to the car; two hosts must never race to restart Morphe.
    fun playerForCar(context: Context): MorpheScreenShare = phonePlayer?.also { phonePlayer = null }
        ?: MorpheScreenShare(context)
    fun releasePhoneHost(done: () -> Unit) {
        val player = phonePlayer
        phonePlayer = null
        if (player != null) player.closeAfter(done) else done()
    }
    val phoneHostActive get() = phonePlayer != null

    fun load(context: Context, more: Boolean = false, force: Boolean = false) {
        if (loading || (!more && !force && cards.isNotEmpty())) return
        loading = true; error = null; changed()
        val ticket = ++generation
        val source = CarPlaybackLink.loadHome ?: { next: Boolean, done: (String?, String?) -> Unit ->
            val player = phonePlayer ?: MorpheScreenShare(context.applicationContext).also { phonePlayer = it }
            player.loadHome(next, done)
        }
        source(more) { json, failure ->
            if (ticket != generation) return@source
            val incoming = runCatching { parse(json ?: "[]") }.getOrDefault(emptyList())
            if (failure != null || incoming.isEmpty()) {
                loading = false
                error = failure ?: "No Home videos available. Open Morphe once, check sign-in, then retry."
                changed()
            } else {
                val previous = if (more) cards else emptyList()
                val known = previous.associateBy { key(it.title, it.channel) }
                val results = incoming.map { known[key(it.title, it.channel)] ?: it }.toMutableList()
                var remaining = results.count { it.video == null }
                fun finish() {
                    val next = (previous + results).associateBy { key(it.title, it.channel) }.values.toList()
                    Thumbnails.requestBatch(next.mapNotNull { it.video }) {
                        if (ticket == generation) {
                            cards = next
                            loading = false
                            error = if (results.any { it.video == null }) "Some video links could not be verified. Retry to check again." else null
                            changed()
                        }
                    }
                }
                if (remaining == 0) finish()
                else results.indices.filter { results[it].video == null }.forEach { index ->
                    val card = results[index]
                    YouTubeSearch.search("${card.title} ${card.channel}") { result ->
                        if (ticket != generation) return@search
                        val match = resolve(card, result.videos)
                        fun resolved(video: KaraokeVideo?) {
                            if (ticket != generation) return
                            results[index] = card.copy(video = video?.takeIf { clean(it.channel) == clean(card.channel) })
                            remaining--
                            if (remaining == 0) finish()
                        }
                        if (match == null) resolved(null) else YouTubeSearch.canonicalVideo(match, ::resolved)
                    }
                }
            }
        }
    }
    internal fun parse(json: String): List<HomeCard> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val value = array.optJSONObject(i) ?: return@mapNotNull null
            val title = value.optString("title").trim().take(300)
            val channel = value.optString("channel").trim().take(150)
            if (title.isBlank() || channel.isBlank()) null else HomeCard(title, channel)
        }.distinctBy { key(it.title, it.channel) }.take(12)
    }
    private fun clean(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(java.util.Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
    private fun key(title: String, channel: String) = "${clean(title)}:${clean(channel)}"
    internal fun resolve(card: HomeCard, videos: List<KaraokeVideo>): KaraokeVideo? = videos.firstOrNull {
        clean(it.title) == clean(card.title) && clean(it.channel) == clean(card.channel)
    }
}
