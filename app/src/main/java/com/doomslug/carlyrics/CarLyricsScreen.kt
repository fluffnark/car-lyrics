package com.doomslug.carlyrics

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import android.util.Log

/** Host-rendered rows and actions support the Mazda Commander knob. */
class CarLyricsScreen private constructor(
    context: CarContext,
    private val catalog: VideoCatalog,
    private val session: CarPlaybackSession,
    private val saved: SavedVideos,
    private val remoteSearch: VideoSearch,
    private val fullscreenHost: Boolean,
    private val mode: Mode = Mode.BROWSE,
    private val source: Source = Source.RECENT,
    private val activeCollection: KaraokeCollection? = null,
    private val activePlaylist: KaraokePlaylist? = null,
) : Screen(context), DefaultLifecycleObserver {
    constructor(
        context: CarContext,
        catalog: VideoCatalog = SingKingCatalog,
        player: VideoPlayer = MorpheScreenShare(context),
        saved: SavedVideos = SavedVideos(context),
        remoteSearch: VideoSearch = YouTubeSearch,
        fullscreenHost: Boolean = BuildConfig.FULLSCREEN_HOST,
    ) : this(context, catalog, CarPlaybackSession(player), saved, remoteSearch, fullscreenHost)

    private enum class Source { RECENT, SAVED }
    private enum class Mode { BROWSE, SEARCH, COLLECTIONS, QUEUE, PLAYLISTS, PLAYLIST, PLAYER, SEEK }
    private val player get() = session.player
    private var queue: List<KaraokeVideo>
        get() = session.queue
        set(value) { session.queue = value }
    private var selected: Int
        get() = session.selected
        set(value) { session.selected = value }
    private var playback: PlaybackStatus
        get() = session.status
        set(value) { session.status = value }
    private val ownsSession = session.owner == null
    private val manager get() = carContext.getCarService(ScreenManager::class.java)
    private val playlists = PlaylistStore(context)
    private val queueStore = QueueStore(context)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var editingSearch = false
    private val queuePreferences = context.getSharedPreferences("karaoke_queue", 0)
    private val playlistPreferences = context.getSharedPreferences("karaoke_playlists", 0)
    private val queueChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "items") session.active?.libraryChanged(queueChanged = true)
    }
    private val playlistsChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "lists") session.active?.libraryChanged(queueChanged = false)
    }
    private val refreshLibrary = Runnable { invalidate() }
    private val search = VideoSearchSession(
        localVideos = {
            if (catalog === SingKingCatalog) SingKingCatalog.library + catalog.videos + saved.all() + queueStore.all()
            else catalog.videos
        },
        remote = remoteSearch,
        changed = {
            Log.i("CarLyricsSearch", "results changed mode=$mode lifecycle=${lifecycle.currentState}")
            if (mode == Mode.SEARCH && !editingSearch) invalidate()
        },
    )
    init {
        lifecycle.addObserver(this)
        if (ownsSession) {
            session.owner = this
            queuePreferences.registerOnSharedPreferenceChangeListener(queueChanged)
            playlistPreferences.registerOnSharedPreferenceChangeListener(playlistsChanged)
            CarPlaybackLink.playVideo = { video ->
                val queued = queueStore.all()
                val index = queued.indexOfFirst { it.id == video.id }
                if (index >= 0) select(queued, index, QUEUE_SOURCE)
                else select(listOf(video), 0)
                true
            }
            CarPlaybackLink.showPlayer = {
                if (session.hasVideo) {
                    returnToPlayer()
                    runCatching { carContext.startCarApp(android.content.Intent(carContext, CarLyricsService::class.java)) }
                }
                session.hasVideo
            }
        }
        if (mode == Mode.PLAYER) marker = PLAYER_MARKER
    }

    override fun onStart(owner: LifecycleOwner) {
        session.active = this
        if (!session.surfaceRegistered) {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(player)
            session.surfaceRegistered = true
            player.prepare()
        }
        if (catalog === SingKingCatalog) {
            SingKingCatalog.initialize(carContext)
            if (ownsSession && (catalog.videos.isEmpty() || SingKingCatalog.isStale())) refresh()
        } else if (catalog.videos.isEmpty() && !catalog.loading) refresh()
        invalidate()
    }

    override fun onStop(owner: LifecycleOwner) {
        // Menus and another AA app do not end or reset the current playback session.
        if (session.active === this) session.active = null
    }

    override fun onResume(owner: LifecycleOwner) {
        // Screens underneath a menu can remain STARTED. Returning to them only
        // resumes them, so status updates must follow the resumed screen too.
        session.active = this
        invalidate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        search.close()
        main.removeCallbacksAndMessages(null)
        if (ownsSession) {
            queuePreferences.unregisterOnSharedPreferenceChangeListener(queueChanged)
            playlistPreferences.unregisterOnSharedPreferenceChangeListener(playlistsChanged)
            CarPlaybackLink.showPlayer = null
            CarPlaybackLink.playVideo = null
            player.close()
        }
    }

    override fun onGetTemplate(): Template {
        // Register once in onStart. Registering on every status/template update
        // causes the host to resend its surface and repeatedly restart playback.
        val template = when (mode) {
        Mode.BROWSE -> browseTemplate()
        Mode.SEARCH -> searchTemplate()
        Mode.COLLECTIONS -> collectionsTemplate()
        Mode.QUEUE -> queueTemplate()
        Mode.PLAYLISTS -> playlistsTemplate()
        Mode.PLAYLIST -> playlistTemplate()
        Mode.PLAYER -> playerTemplate()
        Mode.SEEK -> seekTemplate()
        }
        // A Back operation is recognized only when the host receives the root's
        // old template ID. Do not pop and push within the same callback: that
        // skips the Back template and leaves the host's task quota unchanged.
        if (ownsSession) session.afterRoot?.let { next ->
            session.afterRoot = null
            main.post { if (manager.top === this && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) next() }
        }
        return template
    }

    private fun browseTemplate(): Template {
        val videos = currentVideos()
        val rows = mutableListOf<Row>()
        fun menu(title: String, detail: String, click: () -> Unit) {
            rows += Row.Builder().setTitle(title).addText(detail).setOnClickListener(click).build()
        }
        menu("Search YouTube", "Say a song or artist • all providers") { openSearch() }
        if (activeCollection == null && source == Source.RECENT) {
            menu("Queue", "${queueStore.all().size} songs • added from your phone") { open(Mode.QUEUE) }
            menu("Playlists", "My mix, warm-up, and duets") { open(Mode.PLAYLISTS) }
            menu("Genres & albums", "Browse curated Sing King collections") { open(Mode.COLLECTIONS) }
            menu("Favorites", "Your saved karaoke videos") { open(Mode.BROWSE, source = Source.SAVED) }
        }
        if (session.hasVideo && activeCollection == null && source == Source.RECENT) {
            menu("Previous video", "Go back in the current playing list") { step(-1); returnToPlayer() }
            menu("Seek current video", "Turn the knob to choose a time") { open(Mode.SEEK) }
            menu("Repair picture", "Reopen the current video at this position") { player.recoverVideo(); returnToPlayer() }
        }
        if (videos.isEmpty()) {
            rows += Row.Builder().setTitle(when {
                source == Source.SAVED -> "No saved songs yet"
                catalog.loading -> "Loading Sing King videos…"
                catalog.error != null -> catalog.error!!
                else -> "No recent videos available"
            }).addText("Use Search to find a song from any provider").build()
            if (source == Source.RECENT && !catalog.loading) menu("Retry", "Refresh recent videos") { refresh() }
        } else {
            val remaining = (contentLimit() - rows.size).coerceAtLeast(0)
            val visible = videos.take((remaining - if (videos.size > remaining) 1 else 0).coerceAtLeast(0))
            // Stable icons: downloading/replacing dozens of thumbnails during a
            // knob scroll repeatedly rebuilt the list and could thrash the cache.
            visible.forEachIndexed { index, video ->
                rows += Row.Builder().setTitle(video.title.take(72))
                    .addText(if (source == Source.SAVED) "Saved karaoke" else "Sing King • Karaoke")
                    .setImage(icon(R.drawable.ic_video), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener { select(videos, index) }.build()
            }
            if (visible.size < videos.size && remaining > 0)
                menu("Find more songs", "Search the complete library") { openSearch() }
        }
        val list = ItemList.Builder()
        rows.take(contentLimit()).forEach(list::addItem)
        return ListTemplate.Builder()
            .setHeader(header(activeCollection?.title ?: if (source == Source.RECENT) "Sing King • Recent" else "Favorites").build())
            .setSingleList(list.build()).build()
    }

    private fun searchItems(): ItemList {
        val matches = search.videos.toList()
        val limit = contentLimit().coerceAtMost(30)
        val list = ItemList.Builder().setNoItemsMessage(if (search.query.isBlank())
            "Use the microphone to say a song, artist, or provider" else "No results. Try the song and artist, or another provider.")
        if (search.error != null) list.addItem(Row.Builder().setTitle("Retry YouTube search")
            .addText(search.error!!).setOnClickListener {
                search.update(search.query, submitted = true, retry = true)
            }.build())
        matches.take(limit - if (search.error != null) 1 else 0).forEachIndexed { index, video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("YouTube • select to play")
                .setOnClickListener { select(matches, index) }.build())
        }
        return list.build()
    }

    private fun searchTemplate(): Template {
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                // Interim speech is owned by the host. Rebuilding its text field
                // here can interrupt recognition after the first/last word.
                if (searchText.isNotBlank() && searchText != search.query) editingSearch = true
            }
            override fun onSearchSubmitted(searchText: String) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || searchText.isBlank()) return
                editingSearch = false
                Log.i("CarLyricsSearch", "submitted chars=${searchText.length}")
                search.update(searchText, submitted = true)
            }
        }).setSearchHint("Say a song, artist, or provider")
            .setShowKeyboardByDefault(false)
            .setHeaderAction(Action.BACK)
        // Do not echo partial text through setInitialSearchText. Results stay in
        // SearchTemplate, whose content changes are documented as refreshes.
        if (session.hasVideo) builder.setActionStrip(ActionStrip.Builder().addAction(nowPlayingAction()).build())
        if (search.loading && search.videos.isEmpty()) builder.setLoading(true)
        else builder.setItemList(searchItems())
        return builder.build()
    }

    private fun collectionsTemplate(): Template {
        val list = ItemList.Builder()
        SingKingCatalog.collections.take(contentLimit()).forEach { collection ->
            list.addItem(Row.Builder().setTitle(collection.title)
                .addText("${collection.kind.replaceFirstChar { it.uppercase() }} • ${collection.ids.size} songs")
                .setOnClickListener { open(Mode.BROWSE, collection = collection) }.build())
        }
        return ListTemplate.Builder().setHeader(header("Genres & albums").build())
            .setSingleList(list.build()).build()
    }

    private fun queueTemplate(): Template {
        val items = queueStore.all()
        val list = ItemList.Builder().setNoItemsMessage("Add songs with Queue in the phone app")
        items.take(contentLimit()).forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("Queued")
                .setOnClickListener { select(queueStore.all(), queueStore.all().indexOfFirst { it.id == video.id }, QUEUE_SOURCE) }.build())
        }
        return ListTemplate.Builder().setHeader(header("Up next").build())
            .setSingleList(list.build()).build()
    }

    private fun playlistsTemplate(): Template {
        val list = ItemList.Builder()
        playlists.all().take(contentLimit()).forEach { playlist ->
            list.addItem(Row.Builder().setTitle(playlist.name).addText("${playlist.videos.size} karaoke songs")
                .setOnClickListener { open(Mode.PLAYLIST, playlist = playlist) }.build())
        }
        return ListTemplate.Builder().setHeader(header("Your playlists").build())
            .setSingleList(list.build()).build()
    }

    private fun playlistTemplate(): Template {
        val playlist = playlists.all().firstOrNull { it.name == activePlaylist?.name } ?: return playlistsTemplate()
        val list = ItemList.Builder().setNoItemsMessage("Add songs with Mix in the phone app")
        playlist.videos.take(contentLimit()).forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("${playlist.name} • karaoke")
                .setOnClickListener { select(playlist.videos, playlist.videos.indexOf(video), playlist.name) }.build())
        }
        return ListTemplate.Builder().setHeader(header(playlist.name).build())
            .setSingleList(list.build()).build()
    }

    private fun playerTemplate(): Template {
        val current = queue.getOrNull(selected)
        val state = player.statusDetail ?: when (playback) {
            PlaybackStatus.IDLE -> "Ready"
            PlaybackStatus.LOADING -> "Loading video…"
            PlaybackStatus.PLAYING -> "Playing • ${selected + 1} of ${queue.size}"
            PlaybackStatus.PAUSED -> "Paused • ${selected + 1} of ${queue.size}"
            PlaybackStatus.ENDED -> "Finished • choose Next"
            PlaybackStatus.ERROR -> "Video unavailable • Retry or choose Next"
        }
        val pane = Pane.Builder().addRow(Row.Builder()
            .setTitle("Now playing")
            .addText(current?.title?.take(72) ?: "Sing King")
            .addText(state).build())
        pane.addAction(Action.Builder().setTitle("Previous")
            .setOnClickListener { step(-1) }.build())
        pane.addAction(Action.Builder().setTitle("Next")
            .setOnClickListener { step(1) }.build())
        val playbackAction = when (playback) {
            PlaybackStatus.LOADING, PlaybackStatus.PLAYING -> Action.Builder()
                .setTitle("Pause").setIcon(icon(R.drawable.ic_pause))
                .setOnClickListener { player.pause() }.build()
            else -> Action.Builder()
                .setTitle(if (playback == PlaybackStatus.ERROR) "Retry" else "Play")
                .setIcon(icon(R.drawable.ic_play))
                .setOnClickListener { player.resume() }.build()
        }
        if (player.compactControls) {
            val pausable = playback == PlaybackStatus.PLAYING || playback == PlaybackStatus.LOADING
            val compactPlayback = Action.Builder().setIcon(icon(
                if (pausable) R.drawable.ic_pause else R.drawable.ic_play
            )).setOnClickListener { if (pausable) player.pause() else player.resume() }.build()
            // Pane refresh rules require stable row titles. Putting Loading /
            // Playing / Paused in the title consumes a task step on each change.
            val compactHeader = player.statusDetail == null
            player.setCompactHeader(compactHeader && !fullscreenHost)
            val statusRow = Row.Builder().setTitle("Morphe")
            if (!compactHeader) statusRow.addText(state)
            val compactPane = Pane.Builder().addRow(statusRow.build())
            if (player.statusDetail != null) compactPane.addAction(Action.Builder().setTitle("Phone setup")
                .setOnClickListener {
                    runCatching {
                        carContext.applicationContext.startActivity(android.content.Intent(carContext, MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            android.app.ActivityOptions.makeBasic().setLaunchDisplayId(android.view.Display.DEFAULT_DISPLAY).toBundle())
                    }.onFailure {
                        Log.w("CarLyricsPlayer", "Phone setup could not open", it)
                        androidx.car.app.CarToast.makeText(carContext, "Open Car Lyrics on your phone to start sharing", androidx.car.app.CarToast.LENGTH_LONG).show()
                    }
                }.build())
            val controls = ActionStrip.Builder()
                .addAction(searchAction())
                .addAction(compactPlayback)
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_next)).setOnClickListener { step(1) }.build())
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_browse))
                    .setOnClickListener { browse() }.build()).build()
            // Only the explicit local experiment declares NAVIGATION. Its empty
            // navigation overlay leaves video unobscured; the host owns the rail
            // and when the knob-accessible transport buttons hide/reappear.
            val tools = ActionStrip.Builder()
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_seek)).setOnClickListener { open(Mode.SEEK) }.build())
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_refresh)).setOnClickListener { player.recoverVideo() }.build())
                .addAction(favoriteAction(current)).build()
            if (fullscreenHost) {
                val builder = NavigationTemplate.Builder().setActionStrip(controls).setMapActionStrip(tools)
                player.statusDetail?.let {
                    builder.setNavigationInfo(androidx.car.app.navigation.model.MessageInfo.Builder(it).build())
                }
                return builder.build()
            }
            return MapWithContentTemplate.Builder()
                .setMapController(androidx.car.app.navigation.model.MapController.Builder().setMapActionStrip(tools).build())
                .setContentTemplate(PaneTemplate.Builder(compactPane.build()).build())
                .setActionStrip(controls).build()
        }
        val savedAction = favoriteAction(current)
        val queueAction = Action.Builder()
            .setTitle(if (current != null && queueStore.all().any { it.id == current.id }) "Queued" else "Queue")
            .setOnClickListener { current?.let { queueStore.toggle(it) }; invalidate() }.build()
        val strip = ActionStrip.Builder()
            .addAction(playbackAction)
            .addAction(queueAction)
            .addAction(savedAction)
            .addAction(Action.Builder().setTitle("Browse").setIcon(icon(R.drawable.ic_browse))
                .setOnClickListener { browse() }.build()).build()
        return MapWithContentTemplate.Builder()
            .setContentTemplate(PaneTemplate.Builder(pane.build()).build())
            .setActionStrip(strip).build()
    }

    private fun favoriteAction(video: KaraokeVideo?): Action {
        val playing = player.videoToSave(video)
        return Action.Builder()
        .setIcon(icon(if (playing != null && saved.contains(playing.id)) R.drawable.ic_saved else R.drawable.ic_favorite_outline))
        .setOnClickListener {
            val current = player.videoToSave(video)
            if (current == null) {
                androidx.car.app.CarToast.makeText(carContext,
                    "Select this video in Car Lyrics to save it", androidx.car.app.CarToast.LENGTH_LONG).show()
            } else current.let {
                val added = saved.toggle(it)
                androidx.car.app.CarToast.makeText(carContext,
                    if (added) "Saved to Favorites" else "Removed from Favorites",
                    androidx.car.app.CarToast.LENGTH_SHORT).show()
                invalidate()
            }
        }.build()
    }

    private fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()
    private fun currentVideos(): List<KaraokeVideo> {
        if (activeCollection != null) {
            val byId = SingKingCatalog.library.associateBy { it.id }
            return activeCollection!!.ids.mapNotNull { byId[it] }
        }
        return if (source == Source.RECENT) catalog.videos else saved.all()
    }

    private fun openSearch() {
        if (mode == Mode.SEARCH && manager.top === this) return
        if (manager.top === session.owner) open(Mode.SEARCH)
        else {
            session.afterRoot = { (session.owner as CarLyricsScreen).open(Mode.SEARCH) }
            manager.popToRoot()
        }
    }

    private fun open(next: Mode, source: Source = Source.RECENT,
                     collection: KaraokeCollection? = null, playlist: KaraokePlaylist? = null) {
        manager.push(CarLyricsScreen(carContext, catalog, session, saved, remoteSearch, fullscreenHost,
            next, source, collection, playlist))
    }

    private fun returnToPlayer() {
        if (!session.hasVideo) return
        if (manager.screenStack.any { it.marker == PLAYER_MARKER }) manager.popTo(PLAYER_MARKER)
        else open(Mode.PLAYER)
        manager.top.invalidate()
    }

    private fun nowPlayingAction() = Action.Builder().setTitle("Now playing")
        .setIcon(icon(R.drawable.ic_video)).setOnClickListener { returnToPlayer() }.build()

    private fun searchAction() = Action.Builder().setIcon(icon(R.drawable.ic_mic))
        .setOnClickListener { openSearch() }.build()

    private fun header(title: String) = Header.Builder().setTitle(title)
        .setStartHeaderAction(if (ownsSession) Action.APP_ICON else Action.BACK).apply {
            if (session.hasVideo) addEndHeaderAction(nowPlayingAction())
            addEndHeaderAction(searchAction())
        }

    private fun contentLimit() = carContext.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).let { if (it > 0) it.coerceAtMost(100) else 100 }

    private fun select(videos: List<KaraokeVideo>, index: Int, listSource: String? = null) {
        val video = videos.getOrNull(index) ?: return
        queue = videos.toList()
        session.listSource = listSource
        selected = index
        playback = PlaybackStatus.LOADING
        player.select(video)
        returnToPlayer()
    }

    private fun step(delta: Int) {
        if (queue.isEmpty()) return
        val currentId = queue.getOrNull(selected)?.id
        val fresh = when (session.listSource) {
            null -> queue
            QUEUE_SOURCE -> queueStore.all()
            else -> playlists.all().firstOrNull { it.name == session.listSource }?.videos.orEmpty()
        }
        if (fresh.isEmpty()) return
        val currentIndex = fresh.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex >= 0) currentIndex + delta
            else selected + if (delta > 0) 0 else -1
        queue = fresh
        selected = (nextIndex + queue.size) % queue.size
        playback = PlaybackStatus.LOADING
        player.select(queue[selected])
        invalidate()
    }

    private fun browse() { manager.popToRoot() }

    internal fun playbackChanged() {
        // A buffering transition must never redraw a menu under the knob or
        // replace the search field while the host is listening.
        if (mode == Mode.PLAYER) invalidate()
    }

    private fun libraryChanged(queueChanged: Boolean) {
        val relevant = if (queueChanged) mode == Mode.QUEUE || mode == Mode.BROWSE
            else mode == Mode.PLAYLIST || mode == Mode.PLAYLISTS
        if (relevant) {
            main.removeCallbacks(refreshLibrary)
            main.postDelayed(refreshLibrary, 250)
        }
    }

    private fun seekTemplate(): Template {
        val timeline = player.timeline
        if (timeline == null || !timeline.seekable || timeline.durationMs <= 0 || contentLimit() < 2) {
            return ListTemplate.Builder().setHeader(header("Seek video").build())
                .setSingleList(ItemList.Builder().addItem(Row.Builder().setTitle("Timeline not ready")
                    .addText("Wait for Morphe to load the video, then reopen Seek.").build()).build()).build()
        }
        val stepMs = maxOf(10_000L, ((timeline.durationMs / (contentLimit() - 1) + 9_999) / 10_000) * 10_000)
        val positions = (0..(timeline.durationMs / stepMs).toInt()).map { it * stepMs }
        val list = ItemList.Builder().setSelectedIndex((timeline.positionMs / stepMs).toInt().coerceIn(positions.indices))
            .setOnSelectedListener { index -> player.seekTo(positions[index]) }
        positions.forEach { position ->
            list.addItem(Row.Builder().setTitle(formatTime(position))
                .addText("of ${formatTime(timeline.durationMs)} • press knob to seek").build())
        }
        return ListTemplate.Builder().setHeader(header("Seek • turn, then press").build()).setSingleList(list.build()).build()
    }

    private fun formatTime(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

    private fun refresh() {
        catalog.refresh { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) invalidate() }
        invalidate()
    }

    private companion object {
        const val PLAYER_MARKER = "current_player"
        const val QUEUE_SOURCE = "@queue"
    }
}

/** One playback session survives all menus and their lifecycle changes. */
private class CarPlaybackSession(val player: VideoPlayer) {
    var queue = emptyList<KaraokeVideo>()
    var selected = -1
    var listSource: String? = null
    var status = PlaybackStatus.IDLE
    var owner: Screen? = null
    var active: CarLyricsScreen? = null
    var surfaceRegistered = false
    var afterRoot: (() -> Unit)? = null
    val hasVideo get() = selected in queue.indices
    init { player.onStatus = { status = it; active?.playbackChanged() } }
}
