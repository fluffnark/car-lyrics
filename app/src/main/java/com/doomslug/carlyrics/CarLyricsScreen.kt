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
    private val initialQuery: String = "",
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
    private enum class Mode { BROWSE, SEARCH, SEARCH_RESULTS, COLLECTIONS, QUEUE, PLAYLISTS, PLAYLIST, PLAYER, SEEK }
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
    private val search = VideoSearchSession(
        localVideos = {
            if (catalog === SingKingCatalog) SingKingCatalog.library + catalog.videos + saved.all() + queueStore.all()
            else catalog.videos
        },
        remote = remoteSearch,
        changed = {
            Log.i("CarLyricsSearch", "results changed mode=$mode lifecycle=${lifecycle.currentState}")
            if (mode == Mode.SEARCH || mode == Mode.SEARCH_RESULTS) invalidate()
        },
    )
    init {
        lifecycle.addObserver(this)
        if (ownsSession) {
            session.owner = this
            CarPlaybackLink.showPlayer = {
                if (session.hasVideo) {
                    returnToPlayer()
                    runCatching { carContext.startCarApp(android.content.Intent(carContext, CarLyricsService::class.java)) }
                }
                session.hasVideo
            }
        }
        if (mode == Mode.PLAYER) marker = PLAYER_MARKER
        if (mode == Mode.SEARCH_RESULTS) search.update(initialQuery, submitted = true)
    }

    override fun onStart(owner: LifecycleOwner) {
        session.active = this
        if (!session.surfaceRegistered) {
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(player)
            session.surfaceRegistered = true
        }
        if (catalog === SingKingCatalog) {
            SingKingCatalog.initialize(carContext)
            if (catalog.videos.isEmpty() || SingKingCatalog.isStale()) refresh()
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
        if (ownsSession) { CarPlaybackLink.showPlayer = null; player.close() }
    }

    override fun onGetTemplate(): Template {
        // Register once in onStart. Registering on every status/template update
        // causes the host to resend its surface and repeatedly restart playback.
        return when (mode) {
        Mode.BROWSE -> browseTemplate()
        Mode.SEARCH -> searchTemplate()
        Mode.SEARCH_RESULTS -> searchResultsTemplate()
        Mode.COLLECTIONS -> collectionsTemplate()
        Mode.QUEUE -> queueTemplate()
        Mode.PLAYLISTS -> playlistsTemplate()
        Mode.PLAYLIST -> playlistTemplate()
        Mode.PLAYER -> playerTemplate()
        Mode.SEEK -> seekTemplate()
        }
    }

    private fun browseTemplate(): Template {
        val videos = currentVideos()
        val list = ItemList.Builder()
        if (session.hasVideo) {
            list.addItem(Row.Builder().setTitle("Seek current video").addText("Turn the knob to choose a time")
                .setOnClickListener { open(Mode.SEEK) }.build())
            list.addItem(Row.Builder().setTitle("Repair picture").addText("Reopen the current video at this position")
                .setOnClickListener { player.recoverVideo(); returnToPlayer() }.build())
        }
        list.addItem(Row.Builder().setTitle("Search YouTube")
            .addText("Say a song or artist • all providers")
            .setOnClickListener { openSearch() }.build())
        if (videos.isEmpty()) {
            list.addItem(Row.Builder().setTitle(when {
                source == Source.SAVED -> "No saved songs yet"
                catalog.loading -> "Loading Sing King videos…"
                catalog.error != null -> catalog.error!!
                else -> "No recent videos available"
            }).addText(if (source == Source.SAVED) "Save a song from the player" else "Connect to the internet and retry").build())
            if (source == Source.RECENT && !catalog.loading) list.addItem(Row.Builder().setTitle("Retry")
                .setOnClickListener { refresh() }.build())
        } else {
            if (activeCollection == null && source == Source.RECENT) {
                list.addItem(Row.Builder().setTitle("Queue • ${queueStore.all().size} songs")
                    .addText("Build a one-drive karaoke set")
                    .setOnClickListener { open(Mode.QUEUE) }.build())
                list.addItem(Row.Builder().setTitle("Playlists")
                    .addText("My mix, warm-up, and duets")
                    .setOnClickListener { open(Mode.PLAYLISTS) }.build())
                list.addItem(Row.Builder().setTitle("Genres & albums")
                    .addText("Browse curated Sing King collections")
                    .setOnClickListener { open(Mode.COLLECTIONS) }.build())
            }
            val visible = videos.take(contentLimit() - if (session.hasVideo) 7 else 5)
            visible.forEachIndexed { offset, video ->
                val thumbnail = Thumbnails.get(video.id)
                val icon = if (thumbnail != null) IconCompat.createWithBitmap(thumbnail)
                    else IconCompat.createWithResource(carContext, R.drawable.ic_video)
                list.addItem(Row.Builder()
                    .setTitle(video.title.take(72))
                    .addText(if (source == Source.SAVED) "Saved • Sing King" else "Sing King • Karaoke")
                    .setImage(CarIcon.Builder(icon).build(), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener { select(videos, offset) }.build())
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Thumbnails.request(visible) { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && mode == Mode.BROWSE) invalidate() }
            }
            if (visible.size < videos.size) list.addItem(Row.Builder().setTitle("Find more songs")
                .addText("Search the complete library by song or artist")
                .setOnClickListener { openSearch() }.build())
        }
        val switch = Action.Builder()
            .setTitle(if (source == Source.RECENT) "Saved" else "Recent")
            .setIcon(icon(if (source == Source.RECENT) R.drawable.ic_saved else R.drawable.ic_browse))
            .setOnClickListener {
                if (source == Source.SAVED) finish()
                else open(Mode.BROWSE, source = Source.SAVED)
            }.build()
        val header = header(activeCollection?.title ?: if (source == Source.RECENT) "Sing King • Recent" else "Saved karaoke songs")
            .addEndHeaderAction(switch).build()
        return ListTemplate.Builder().setHeader(header).setSingleList(list.build()).build()
    }

    private fun searchItems(): ItemList {
        val matches = search.videos.toList()
        val limit = carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).coerceIn(1, 30)
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

    private fun searchResultsTemplate(): Template {
        val header = header(search.query.take(64)).build()
        val builder = ListTemplate.Builder().setHeader(header)
        if (search.loading && search.videos.isEmpty()) builder.setLoading(true)
        else builder.setSingleList(searchItems())
        return builder.build()
    }

    private fun searchTemplate(): Template {
        val builder = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                // Some hosts clear their input after closing speech recognition.
                // Once submitted, the results screen owns the query.
                if (mode != Mode.SEARCH) return
                search.update(searchText)
            }
            override fun onSearchSubmitted(searchText: String) {
                if (mode != Mode.SEARCH || searchText.isBlank()) return
                open(Mode.SEARCH_RESULTS, query = searchText)
            }
        }).setSearchHint("Song, artist, album, or genre")
            .setInitialSearchText(search.query)
            .setShowKeyboardByDefault(false)
            .setHeaderAction(Action.BACK)
        if (session.hasVideo) builder.setActionStrip(ActionStrip.Builder().addAction(nowPlayingAction()).build())
        if (search.loading && search.videos.isEmpty()) builder.setLoading(true)
        else builder.setItemList(searchItems())
        return builder.build()
    }

    private fun collectionsTemplate(): Template {
        val list = ItemList.Builder()
        SingKingCatalog.collections.forEach { collection ->
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
                .setOnClickListener { select(items, items.indexOf(video)) }.build())
        }
        return ListTemplate.Builder().setHeader(header("Up next • ${items.size}").build())
            .setSingleList(list.build()).build()
    }

    private fun playlistsTemplate(): Template {
        val list = ItemList.Builder()
        playlists.all().forEach { playlist ->
            list.addItem(Row.Builder().setTitle(playlist.name).addText("${playlist.videos.size} karaoke songs")
                .setOnClickListener { open(Mode.PLAYLIST, playlist = playlist) }.build())
        }
        return ListTemplate.Builder().setHeader(header("Your playlists").build())
            .setSingleList(list.build()).build()
    }

    private fun playlistTemplate(): Template {
        val playlist = activePlaylist ?: return playlistsTemplate()
        val list = ItemList.Builder().setNoItemsMessage("Add songs with Mix in the phone app")
        playlist.videos.take(contentLimit()).forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("${playlist.name} • karaoke")
                .setOnClickListener { select(playlist.videos, playlist.videos.indexOf(video)) }.build())
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
            .setTitle(current?.title?.take(72) ?: "Sing King")
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
            val compactPane = Pane.Builder().addRow(Row.Builder().setTitle(
                if (player.statusDetail != null) state else "Morphe • $state"
            ).build())
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
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_previous)).setOnClickListener { step(-1) }.build())
                .addAction(compactPlayback)
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_next)).setOnClickListener { step(1) }.build())
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_browse))
                    .setOnClickListener { browse() }.build()).build()
            // Only the explicit local experiment declares NAVIGATION. Its empty
            // navigation overlay leaves video unobscured; the host owns the rail
            // and when the knob-accessible transport buttons hide/reappear.
            val tools = ActionStrip.Builder()
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_seek)).setOnClickListener { open(Mode.SEEK) }.build())
                .addAction(Action.Builder().setIcon(icon(R.drawable.ic_refresh)).setOnClickListener { player.recoverVideo() }.build()).build()
            if (fullscreenHost) {
                val builder = NavigationTemplate.Builder().setActionStrip(controls).setMapActionStrip(tools)
                player.statusDetail?.let {
                    builder.setNavigationInfo(androidx.car.app.navigation.model.MessageInfo.Builder(it).build())
                }
                return builder.build()
            }
            return MapWithContentTemplate.Builder()
                .setContentTemplate(PaneTemplate.Builder(compactPane.build()).build())
                .setActionStrip(controls).build()
        }
        val savedAction = Action.Builder()
            .setTitle(if (current != null && playlists.contains("My karaoke mix", current.id)) "In mix" else "Add to mix")
            .setIcon(icon(R.drawable.ic_saved))
            .setOnClickListener {
                current?.let { video ->
                    saved.toggle(video)
                    playlists.toggle("My karaoke mix", video)
                }
                invalidate()
            }.build()
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

    private fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()
    private fun currentVideos(): List<KaraokeVideo> {
        if (activeCollection != null) {
            val byId = SingKingCatalog.library.associateBy { it.id }
            return activeCollection!!.ids.mapNotNull { byId[it] }
        }
        return if (source == Source.RECENT) catalog.videos else saved.all()
    }

    private fun openSearch() {
        // Global search starts a short sub-flow, even when reached from a deep collection.
        if (manager.screenStack.any { it.marker == PLAYER_MARKER }) manager.popTo(PLAYER_MARKER)
        else manager.popToRoot()
        open(Mode.SEARCH)
    }

    private fun open(next: Mode, source: Source = Source.RECENT,
                     collection: KaraokeCollection? = null, playlist: KaraokePlaylist? = null,
                     query: String = "") {
        manager.push(CarLyricsScreen(carContext, catalog, session, saved, remoteSearch, fullscreenHost,
            next, source, collection, playlist, query))
    }

    private fun returnToPlayer() {
        if (!session.hasVideo) return
        if (manager.screenStack.any { it.marker == PLAYER_MARKER }) manager.popTo(PLAYER_MARKER)
        else {
            manager.popToRoot()
            open(Mode.PLAYER)
        }
        manager.top.invalidate()
    }

    private fun nowPlayingAction() = Action.Builder().setTitle("Now playing")
        .setIcon(icon(R.drawable.ic_video)).setOnClickListener { returnToPlayer() }.build()

    private fun header(title: String) = Header.Builder().setTitle(title)
        .setStartHeaderAction(if (ownsSession) Action.APP_ICON else Action.BACK).apply {
            if (session.hasVideo) addEndHeaderAction(nowPlayingAction())
        }

    private fun contentLimit() = carContext.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).coerceIn(8, 100)

    private fun select(videos: List<KaraokeVideo>, index: Int) {
        val video = videos.getOrNull(index) ?: return
        queue = videos.toList()
        selected = index
        playback = PlaybackStatus.LOADING
        player.select(video)
        returnToPlayer()
    }

    private fun step(delta: Int) {
        if (queue.isEmpty()) return
        selected = (selected + delta + queue.size) % queue.size
        playback = PlaybackStatus.LOADING
        player.select(queue[selected])
        invalidate()
    }

    private fun browse() { open(Mode.BROWSE) }

    private fun seekTemplate(): Template {
        val timeline = player.timeline
        if (timeline == null || !timeline.seekable || timeline.durationMs <= 0) {
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

    private companion object { const val PLAYER_MARKER = "current_player" }
}

/** One playback session survives all menus and their lifecycle changes. */
private class CarPlaybackSession(val player: VideoPlayer) {
    var queue = emptyList<KaraokeVideo>()
    var selected = -1
    var status = PlaybackStatus.IDLE
    var owner: Screen? = null
    var active: Screen? = null
    var surfaceRegistered = false
    val hasVideo get() = selected in queue.indices
    init { player.onStatus = { status = it; active?.invalidate() } }
}
