package com.doomslug.carlyrics

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Spotify's paginated API is injectable so paging, permissions and interrupted refreshes are tested. */
internal class SpotifyLibrary(private val get: (String) -> JSONObject) {
    fun pages(url: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val visited = mutableSetOf<String>()
        var next: String? = url
        while (next != null) {
            check(visited.add(next)) { "Spotify returned a repeated page. Retry later." }
            val page = get(next)
            val items = page.getJSONArray("items")
            (0 until items.length()).forEach { items.optJSONObject(it)?.let(result::add) }
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return result
    }
    fun playlists() = pages("https://api.spotify.com/v1/me/playlists?limit=50").mapNotNull { p ->
        val id = p.optString("id")
        if (!Regex("[A-Za-z0-9]{22}").matches(id)) null else SpotifyPlaylist(id,
            p.optString("name").ifBlank { "Untitled playlist" },
            (p.optJSONObject("items") ?: p.optJSONObject("tracks"))?.optInt("total") ?: 0)
    }.distinctBy { it.id }
    fun songs(id: String): List<SpotifySong> = pages("https://api.spotify.com/v1/playlists/$id/items?limit=50").mapNotNull { row ->
        val item = row.optJSONObject("item") ?: row.optJSONObject("track") ?: return@mapNotNull null
        if (item.optString("type", "track") != "track" || item.optString("name").isBlank()) return@mapNotNull null
        val artists = item.optJSONArray("artists") ?: JSONArray()
        SpotifySong(item.optString("id").takeUnless { it == "null" }.orEmpty(), item.getString("name"),
            (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) })
    }
}

/** Restricted public previews must never erase previously imported songs or manual choices. */
internal fun mergeSpotifyPreview(metadata: SpotifyPlaylist, songs: List<SpotifySong>, previous: SpotifyPlaylist?): SpotifyPlaylist {
    val old = previous?.songs.orEmpty().associateBy { it.song.key }
    val read = songs.map { song -> old[song.key]?.copy(song = song) ?: SpotifyMatch(song) }
    val readKeys = songs.map { it.key }.toSet()
    val complete = metadata.total > 0 && songs.size >= metadata.total
    val known = if (complete) read else read + previous?.songs.orEmpty().filter { it.song.key !in readKeys }
    val total = maxOf(metadata.total, known.size)
    return metadata.copy(total = total, songs = known, problem = if (complete) null else
        "Public preview · ${songs.size} read · ${known.size} known${if (metadata.total > 0) " of $total" else " (total unknown)"} tracks. Import CSV for the full list.")
}

internal object SpotifyImporter {
    private const val READ_PENDING = "Not read yet. Refresh or resume to continue."
    private const val MATCH_POLICY = 3
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val observers = mutableSetOf<() -> Unit>()
    @Volatile var running = false; private set
    @Volatile private var cancelled = false
    @Volatile var status = ""; private set
    fun observe(callback: () -> Unit) { observers.add(callback) }
    fun unobserve(callback: () -> Unit) { observers.remove(callback) }
    private fun update(value: String) { status = value; main.post { observers.toList().forEach { it() } } }
    fun stop() { cancelled = true; update("Stopping… Saved matches will be kept.") }
    fun start(context: Context, refresh: Boolean = true, imported: List<SpotifyPlaylist>? = null, retryMissing: Boolean = false) {
        if (running) return
        running = true; cancelled = false
        val app = context.applicationContext
        update("Reading playlists…")
        worker.execute {
            val store = SpotifyImportStore(app)
            try {
                val policy = app.getSharedPreferences("spotify_matching", Context.MODE_PRIVATE)
                val recheck = retryMissing || policy.getInt("policy", 0) < MATCH_POLICY
                SingKingCatalog.initialize(app)
                val api = SpotifyLibrary { SpotifyAccount.get(app, it) }
                val inventory = imported ?: if (refresh) api.playlists() else store.all()
                val oldLists = store.all().associateBy { it.id }
                if (refresh && imported == null) inventory.forEach { metadata ->
                    store.put(oldLists[metadata.id]?.copy(name = metadata.name) ?: metadata.copy(problem = READ_PENDING))
                }
                // Inventory first, then match. A large first list cannot hide all later playlists.
                for ((number, metadata) in inventory.withIndex()) {
                    if (cancelled) break
                    val old = oldLists[metadata.id]
                    if (imported != null) {
                        val previous = old?.songs.orEmpty().associateBy { it.song.key }
                        store.put(metadata.copy(songs = metadata.songs.map { previous[it.song.key]?.copy(song = it.song) ?: it }))
                    } else if (refresh || metadata.problem == READ_PENDING) {
                        update("Reading playlist ${number + 1}/${inventory.size} · ${metadata.name}")
                        val fetched = try {
                            val songs = api.songs(metadata.id)
                            val previous = old?.songs.orEmpty().associateBy { it.song.key }
                            metadata.copy(total = maxOf(metadata.total, songs.size), songs = songs.map { song ->
                                previous[song.key]?.copy(song = song) ?: SpotifyMatch(song)
                            }, problem = null)
                        } catch (e: SpotifyHttpError) {
                            if (e.status != 403) throw e
                            val public = runCatching { SpotifyPublicPlaylist.fetch(metadata.id) }.getOrDefault(emptyList())
                            if (public.isEmpty()) (old ?: metadata).copy(name = metadata.name,
                                total = maxOf(old?.total ?: 0, metadata.total), problem = e.message)
                            else mergeSpotifyPreview(metadata, public, old)
                        }
                        store.put(fetched)
                    }
                }
                var lists = store.all()
                val cache = lists.flatMap { it.songs }.filter { it.video != null }.sortedBy { it.state == "manual" }
                    .associateBy { it.song.key }.toMutableMap()
                val recordings = lists.flatMap { it.songs }.filter { it.video != null && it.state != "manual" }
                    .associateBy { KaraokeMatcher.recordingKey(it.song) }.toMutableMap()
                val checkedThisRun = mutableMapOf<String, SpotifyMatch>()
                val library = KaraokeMatchingCatalog.index(app)
                val lookup = KaraokeLookup()
                // Give the whole library its local matches before slower network searches.
                for ((number, p) in lists.withIndex()) {
                    if (cancelled) break
                    update("Checking saved library · ${number + 1}/${lists.size} · ${p.name}")
                    val updated = p.copy(songs = p.songs.map { row ->
                        if (row.video != null) row else {
                            val video = cache[row.song.key]?.video ?: recordings[KaraokeMatcher.recordingKey(row.song)]?.video
                                ?: library.match(row.song)
                            if (video == null) row else row.copy(video = video, state = "matched", checkedAt = System.currentTimeMillis()).also { cache[row.song.key] = it }
                        }
                    })
                    if (updated != p) store.put(updated)
                }
                lists = store.all()
                cache.putAll(lists.flatMap { it.songs }.filter { it.video != null || it.state == "missing" }
                    .sortedBy { if (it.state == "manual") 2 else if (it.video != null) 1 else 0 }.associateBy { it.song.key })
                var consecutiveErrors = 0
                for ((number, p) in lists.withIndex()) {
                    if (cancelled) break
                    var playlist = p
                    if (playlist.problem != null && playlist.songs.isEmpty()) continue
                    for (index in playlist.songs.indices.sortedBy { playlist.songs[it].state == "error" }) {
                        if (cancelled) break
                        val row = playlist.songs[index]
                        if (row.video != null || (row.state == "missing" && !recheck && System.currentTimeMillis() - row.checkedAt < 7 * 86_400_000L)) continue
                        update("${number + 1}/${lists.size} · ${playlist.name}\n${index + 1}/${playlist.songs.size} · ${row.song.title}")
                        val recordingKey = KaraokeMatcher.recordingKey(row.song)
                        val cached = cache[row.song.key]?.takeIf { it.video != null } ?: recordings[recordingKey]
                            ?: checkedThisRun[recordingKey] ?: cache[row.song.key]
                        val matched = if (cached != null && (cached.video != null ||
                                (cached.state == "missing" && (checkedThisRun.containsKey(recordingKey) || !recheck) &&
                                    System.currentTimeMillis() - cached.checkedAt < 7 * 86_400_000L))) cached.copy(song = row.song) else {
                            val local = library.match(row.song)
                            val remote = if (local == null) lookup.search(row.song) { cancelled }
                                else VideoSearchResult(listOf(local))
                            if (cancelled) break
                            if (remote.error != null) {
                                playlist = playlist.copy(songs = playlist.songs.toMutableList().apply {
                                    set(index, row.copy(state = "error", checkedAt = System.currentTimeMillis()))
                                })
                                store.put(playlist)
                                consecutiveErrors++
                                if (consecutiveErrors >= 3 || remote.error.contains("HTTP 429") || remote.error.contains("HTTP 403"))
                                    throw IllegalStateException("${remote.error} Saved progress is safe; tap Resume.")
                                continue
                            }
                            consecutiveErrors = 0
                            val video = local ?: KaraokeMatcher.match(row.song, remote.videos)
                            SpotifyMatch(row.song, video, if (video == null) "missing" else "matched", System.currentTimeMillis())
                        }
                        cache[row.song.key] = matched
                        consecutiveErrors = 0
                        checkedThisRun[recordingKey] = matched
                        if (matched.video != null && matched.state != "manual") recordings[recordingKey] = matched
                        playlist = playlist.copy(songs = playlist.songs.toMutableList().apply { set(index, matched) })
                        store.put(playlist)
                    }
                }
                val all = store.all()
                if (!cancelled) policy.edit().putInt("policy", MATCH_POLICY).apply()
                update(if (cancelled) "Paused · ${all.sumOf { it.matched }} karaoke matches saved" else
                    "${all.size} playlists · ${all.sumOf { it.matched }} karaoke matches\n${all.count { it.problem != null }} playlists need attention · ${all.sumOf { p -> p.songs.count { it.state == "error" } }} checks need retry")
            } catch (e: Exception) { update(if (e is java.io.IOException) "Network interrupted. Saved progress is safe; tap Resume to retry."
                else e.message?.take(200) ?: "Import paused. Tap Resume to retry.") }
            finally { running = false; main.post { observers.toList().forEach { it() } } }
        }
    }
}
