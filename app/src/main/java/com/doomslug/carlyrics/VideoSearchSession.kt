package com.doomslug.carlyrics

import android.os.Handler
import android.os.Looper
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Shared typed/speech-text search, with local fallback and stale-request protection. */
class VideoSearchSession(
    private val localVideos: () -> List<KaraokeVideo>,
    private val remote: VideoSearch = YouTubeSearch,
    private val localWorker: Executor = localSearchWorker,
    private val changed: () -> Unit,
) {
    var options = VideoSearchOptions(); private set
    var query = ""; private set
    var videos = emptyList<KaraokeVideo>(); private set
    var loading = false; private set
    var error: String? = null; private set
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var pending: Runnable? = null
    private var initialized = false
    private var closed = false

    fun update(text: String, submitted: Boolean = false, retry: Boolean = false, options: VideoSearchOptions = this.options) {
        if (closed) return
        val next = text.trim().take(200)
        if (initialized && next == query && options == this.options && !retry) {
            if (submitted) pending?.let { main.removeCallbacks(it); it.run() }
            return
        }
        cancel()
        initialized = true
        query = next
        this.options = options
        error = null
        val phrase = VideoSearchQuery.phrase(next)
        val remotePhrase = options.remoteQuery(phrase)
        videos = if (phrase.isBlank()) options.localCandidates(localVideos()).distinctBy { it.id }.take(30) else emptyList()
        loading = phrase.isNotBlank()
        changed()
        if (!loading) return
        val request = generation
        val task = Runnable {
            pending = null
            val candidates = localVideos()
            var local = emptyList<KaraokeVideo>()
            var remoteResult: VideoSearchResult? = null
            fun publish() {
                if (closed || request != generation) return
                videos = (remoteResult?.videos.orEmpty() + local).distinctBy { it.id }.take(30)
                loading = !options.libraryOnly && remoteResult == null
                error = remoteResult?.error
                changed()
            }
            localWorker.execute {
                val matches = VideoSearchQuery.matches(options.localCandidates(candidates), phrase).take(30)
                val deliver = Runnable { local = matches; publish() }
                if (Looper.myLooper() == Looper.getMainLooper()) deliver.run() else main.post(deliver)
            }
            if (!options.libraryOnly) remote.search(remotePhrase) { result -> remoteResult = result; publish() }
        }
        pending = task
        if (submitted) task.run() else main.postDelayed(task, 450)
    }

    fun cancel() {
        generation++
        pending?.let(main::removeCallbacks)
        pending = null
        loading = false
        initialized = false
    }

    fun close() { cancel(); closed = true }

    companion object {
        private val localSearchWorker = Executors.newSingleThreadExecutor()
    }
}

data class VideoSearchOptions(
    val karaokeOnly: Boolean = false,
    val provider: String = "",
    val libraryOnly: Boolean = false,
) {
    fun remoteQuery(phrase: String) = listOf(phrase,
        if (karaokeOnly && !phrase.contains("karaoke", ignoreCase = true)) "karaoke" else "",
        provider).filter { it.isNotBlank() }.joinToString(" ")
    fun localCandidates(videos: List<KaraokeVideo>) = when {
        provider.equals("Sing King", true) -> {
            val bundled = SingKingCatalog.library.asSequence().plus(SingKingCatalog.videos).map { it.id }.toHashSet()
            videos.filter { it.id in bundled || it.title.contains(provider, true) }
        }
        provider.isNotBlank() -> videos.filter { it.title.contains(provider, true) }
        else -> videos
    }
}

internal object VideoSearchQuery {
    private val marks = Regex("\\p{M}+")
    private val apostrophes = Regex("['’]")
    private val punctuation = Regex("[^\\p{L}\\p{N}]+")
    private val ignored = setOf("karaoke", "version", "by", "please")
    fun phrase(text: String): String = text.trim().take(200)
        .replace(Regex("^(?:please\\s+)?(?:play|find|search(?:\\s+for)?|look\\s+for)(?:\\s+me)?\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^(?:a\\s+)?karaoke(?:\\s+version)?\\s+(?:of|for)\\s+", RegexOption.IGNORE_CASE), "karaoke ")
        .replace(Regex("\\s+on\\s+(?:youtube|morphe|car lyrics)[.!?]*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ").trim()

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(marks, "").lowercase(Locale.ROOT)
        .replace(apostrophes, "").replace("&", " and ")
        .replace(punctuation, " ").trim()

    fun matches(videos: List<KaraokeVideo>, phrase: String): List<KaraokeVideo> {
        val unique = videos.distinctBy { it.id }
        if (phrase.isBlank()) return unique
        val normalized = normalize(phrase)
        val all = normalized.split(' ').filter { it.isNotBlank() }
        if (all.isEmpty()) return emptyList()
        val tokens = all.filter { it !in ignored }.ifEmpty { all }
        return unique.map { it to normalize(it.title) }
            .filter { (_, title) -> title.split(' ').let { words -> tokens.all { token -> words.any { it.startsWith(token) } } } }
            .sortedBy { (_, title) -> (if (title.contains(normalized)) 0 else 1000) + title.length }
            .map { it.first }
    }
}
