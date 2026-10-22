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
    private val initialPlaylistPage: Int = 0,
) : Screen(context), DefaultLifecycleObserver {
    constructor(
        context: CarContext,
        catalog: VideoCatalog = SingKingCatalog,
        player: VideoPlayer = MorpheHomeFeed.playerForCar(context),
        saved: SavedVideos = SavedVideos(context),
        remoteSearch: VideoSearch = YouTubeSearch,
        fullscreenHost: Boolean = BuildConfig.FULLSCREEN_HOST,
    ) : this(context, catalog, CarPlaybackSession(player), saved, remoteSearch, fullscreenHost)

    private enum class Source { RECENT, SAVED, HISTORY }
    private enum class Mode { BROWSE, SEARCH, COLLECTIONS, QUEUE, PLAYLISTS, PLAYLIST, PLAYER, SEEK, HOME }
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
    private val history = PlaybackHistory(context)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var editingSearch = false
    private var playlistPage = initialPlaylistPage
    private var homePage = 0
    private val homeChanged: () -> Unit = { if (mode == Mode.HOME) invalidate() }
    private val queuePreferences = context.getSharedPreferences("karaoke_queue", 0)
    private val playlistPreferences = context.getSharedPreferences("karaoke_playlists", 0)
    private val queueChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "items") session.active?.libraryChanged(queueChanged = true)
    }
    private val playlistsChanged = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Hundreds of import updates must not reset the rotary selection mid-browse.
        if (key == "lists" || (key == "spotify" && !SpotifyImporter.running)) session.active?.libraryChanged(queueChanged = false)
    }
    private val importFinished: () -> Unit = {
        if (!SpotifyImporter.running) session.active?.libraryChanged(queueChanged = false)
    }
    private val refreshLibrary = Runnable { invalidate() }
    private val search = VideoSearchSession(
        localVideos = {
            if (catalog === SingKingCatalog) SingKingCatalog.library + catalog.videos + saved.all() + queueStore.all() + playlists.all().flatMap { it.videos }
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
            SpotifyImporter.observe(importFinished)
            CarPlaybackLink.playVideo = { video ->
                val target = session.active ?: this
                val queued = queueStore.all()
                val index = queued.indexOfFirst { it.id == video.id }
                if (index >= 0) target.select(queued, index, QUEUE_SOURCE)
                else target.select(listOf(video), 0)
                true
            }
            CarPlaybackLink.resumeVideo = { video, position ->
                (session.active ?: this).select(listOf(video), 0, positionMs = position)
                true
            }
            CarPlaybackLink.loadHome = player::loadHome
            CarPlaybackLink.currentVideo = { currentVideo() }
            CarPlaybackLink.playNext = { video ->
                val current = CarPlaybackLink.currentVideo?.invoke()
                queueStore.playNext(video, current)
                queue = queueStore.all()
                selected = queue.indexOfFirst { it.id == current?.id }
                session.listSource = QUEUE_SOURCE
                true
            }
            CarPlaybackLink.repair = {
                if (session.hasVideo) player.recoverVideo() else player.prepare()
                session.hasVideo
            }
            CarPlaybackLink.showPlayer = {
                val current = currentVideo()
                if (current != null) {
                    if (!session.hasVideo) {
                        queue = listOf(current); selected = 0
                        if (!player.showCurrentVideo()) player.playFrom(current, history.all().firstOrNull { it.video.id == current.id }?.positionMs ?: 0)
                    } else player.showCurrentVideo()
                    player.resume()
                    (session.active ?: this).returnToPlayer()
                    runCatching { carContext.startCarApp(android.content.Intent(carContext, CarLyricsService::class.java)) }
                }
                current != null
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        session.active = this
        if (mode == Mode.HOME) MorpheHomeFeed.observe(homeChanged)
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
        MorpheHomeFeed.unobserve(homeChanged)
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
            SpotifyImporter.unobserve(importFinished)
            CarPlaybackLink.loadHome = null
            CarPlaybackLink.resumeVideo = null
            CarPlaybackLink.currentVideo = null
            CarPlaybackLink.playNext = null
            CarPlaybackLink.repair = null
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
        Mode.HOME -> homeTemplate()
        }
        return template
    }

    private fun browseTemplate(): Template {
        if (source == Source.HISTORY) return historyTemplate()
        val videos = currentVideos()
        val rows = mutableListOf<Row>()
        fun menu(title: String, detail: String, click: () -> Unit) {
            rows += Row.Builder().setTitle(title).addText(detail).setOnClickListener(click).build()
        }
        if (activeCollection == null && source == Source.RECENT) {
            menu("Home", "Morphe recommendations • your signed-in account") {
                open(Mode.HOME)
                MorpheHomeFeed.load(carContext)
            }
            if (currentVideo() != null) menu("Play current video", "Return to the current video") { returnToPlayer(resume = true) }
        }
        menu("Search YouTube", "Say a song or artist • all providers") { openSearch() }
        if (activeCollection == null && source == Source.RECENT) {
            menu("Queue", "${queueStore.all().size} songs • added from your phone") { open(Mode.QUEUE) }
            menu("Playlists", "Your saved karaoke albums") { open(Mode.PLAYLISTS) }
            menu("Genres & albums", "Browse curated Sing King collections") { open(Mode.COLLECTIONS) }
            menu("History", "Recently played videos") { open(Mode.BROWSE, source = Source.HISTORY) }
            menu("Favorites", "Your saved karaoke videos") { open(Mode.BROWSE, source = Source.SAVED) }
        }
        if (session.hasVideo && activeCollection == null && source == Source.RECENT) {
            menu("Previous video", "Go back in the current playing list") { step(-1); returnToPlayer() }
            menu("Seek current video", "Turn the knob to choose a time") { open(Mode.SEEK) }
            menu("Repair picture", "Reopen the current video at this position") { player.recoverVideo(); returnToPlayer() }
            val current = player.videoToSave(queue.getOrNull(selected))
            menu("Save current video", if (current != null && saved.contains(current.id)) "Saved • select to remove" else "Add to Favorites") {
                toggleFavorite(queue.getOrNull(selected))
            }
        }
        if (videos.isEmpty()) {
            rows += Row.Builder().setTitle(when {
                source == Source.SAVED -> "No saved songs yet"
                source == Source.HISTORY -> "No history yet"
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
                    .addText(when (source) { Source.SAVED -> "Saved karaoke"; Source.HISTORY -> "Recently played"; else -> "Sing King • Karaoke" })
                    .setImage(icon(R.drawable.ic_video), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener { select(videos, index, positionMs = if (source == Source.HISTORY)
                        history.all().firstOrNull { it.video.id == video.id }?.let { if (it.durationMs > 0 && it.positionMs >= it.durationMs - 3_000) 0 else it.positionMs } ?: 0 else 0) }.build()
            }
            if (visible.size < videos.size && remaining > 0)
                menu("Find more songs", "Search the complete library") { openSearch() }
        }
        val list = ItemList.Builder()
        rows.take(contentLimit()).forEach(list::addItem)
        return ListTemplate.Builder()
            .setHeader(header(activeCollection?.title ?: if (source == Source.RECENT) "Sing King • Recent" else if (source == Source.HISTORY) "History" else "Favorites").build())
            .setSingleList(list.build()).build()
    }

    private fun historyTemplate(): Template {
        val entries = history.all()
        val size = (contentLimit() - 2).coerceIn(1, 20)
        playlistPage = playlistPage.coerceAtMost((entries.size - 1).coerceAtLeast(0) / size)
        val list = ItemList.Builder()
        entries.drop(playlistPage * size).take(size).forEach { entry ->
            list.addItem(Row.Builder().setTitle(entry.video.title.take(100)).addText("Recently played")
                .setImage(icon(R.drawable.ic_history), Row.IMAGE_TYPE_SMALL).setOnClickListener {
                    select(entries.map { it.video }, entries.indexOf(entry), positionMs =
                        if (entry.durationMs > 0 && entry.positionMs >= entry.durationMs - 3_000) 0 else entry.positionMs)
                }.build())
        }
        if (entries.isEmpty()) list.addItem(Row.Builder().setTitle("No history yet").addText("Videos you play appear here").build())
        if (playlistPage > 0) list.addItem(Row.Builder().setTitle("Previous page").setOnClickListener { changePlaylistPage(playlistPage - 1) }.build())
        if ((playlistPage + 1) * size < entries.size) list.addItem(Row.Builder().setTitle("More history")
            .setOnClickListener { changePlaylistPage(playlistPage + 1) }.build())
        return ListTemplate.Builder().setHeader(header(pageTitle("History", entries.size, size)).build()).setSingleList(list.build()).build()
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
        // Restore only the committed query, never interim speech. Keeping this
        // Screen below playback also keeps the host's input/focus state.
        if (!editingSearch && search.query.isNotBlank()) builder.setInitialSearchText(search.query)
        if (currentVideo() != null) builder.setActionStrip(ActionStrip.Builder().addAction(nowPlayingAction()).build())
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
        val all = playlists.all()
        val pageSize = (contentLimit() - 2).coerceIn(1, 20)
        playlistPage = playlistPage.coerceAtMost((all.size - 1).coerceAtLeast(0) / pageSize)
        all.drop(playlistPage * pageSize).take(pageSize).forEach { playlist ->
            list.addItem(Row.Builder().setTitle(playlist.name.substringBefore(" · Spotify [")).addText(playlist.detail ?: "${playlist.videos.size} karaoke songs")
                .setOnClickListener { open(Mode.PLAYLIST, playlist = playlist) }.build())
        }
        pageRows(list, all.size, pageSize)
        return ListTemplate.Builder().setHeader(header(pageTitle("Your playlists", all.size, pageSize)).build())
            .setSingleList(list.build()).build()
    }

    private fun playlistTemplate(): Template {
        val playlist = playlists.all().firstOrNull { it.name == activePlaylist?.name } ?: return playlistsTemplate()
        val list = ItemList.Builder().setNoItemsMessage(playlist.detail ?: "Add songs in the phone app")
        val pageSize = (contentLimit() - 2).coerceIn(1, 20)
        playlistPage = playlistPage.coerceAtMost((playlist.videos.size - 1).coerceAtLeast(0) / pageSize)
        playlist.videos.drop(playlistPage * pageSize).take(pageSize).forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("${playlist.name.substringBefore(" · Spotify [")} • karaoke")
                .setOnClickListener { select(playlist.videos, playlist.videos.indexOf(video), playlist.name) }.build())
        }
        pageRows(list, playlist.videos.size, pageSize)
        return ListTemplate.Builder().setHeader(header(pageTitle(playlist.name.substringBefore(" · Spotify ["), playlist.videos.size, pageSize)).build())
            .setSingleList(list.build()).build()
    }

    private fun pageTitle(title: String, total: Int, size: Int): String =
        if (total <= size) title else "$title · ${playlistPage + 1}/${(total + size - 1) / size}"

    private fun pageRows(list: ItemList.Builder, total: Int, size: Int) {
        if (playlistPage > 0) list.addItem(Row.Builder().setTitle("Previous page")
            .setOnClickListener { changePlaylistPage(playlistPage - 1) }.build())
        if ((playlistPage + 1) * size < total) list.addItem(Row.Builder().setTitle("More songs / playlists")
            .addText("${(playlistPage + 1) * size} of $total")
            .setOnClickListener { changePlaylistPage(playlistPage + 1) }.build())
    }

    private fun changePlaylistPage(next: Int) {
        if (manager.top !== this) return
        // A template refresh retains the host's old scroll/focus index. A fresh
        // screen resets it, while replacing this page keeps the car stack bounded.
        manager.pop()
        manager.push(CarLyricsScreen(carContext, catalog, session, saved, remoteSearch, fullscreenHost,
            mode, source, activeCollection, activePlaylist, next))
    }

    private fun playerTemplate(): Template {
        val current = currentVideo()
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
            val statusRow = Row.Builder().setTitle("Car Lyrics")
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
            if (fullscreenHost) {
                val builder = NavigationTemplate.Builder().setActionStrip(controls)
                player.statusDetail?.let {
                    builder.setNavigationInfo(androidx.car.app.navigation.model.MessageInfo.Builder(it).build())
                }
                return builder.build()
            }
            return MapWithContentTemplate.Builder()
                .setMapController(androidx.car.app.navigation.model.MapController.Builder().build())
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
            .setOnClickListener { toggleFavorite(video) }.build()
    }

    private fun toggleFavorite(video: KaraokeVideo?) {
        val current = player.videoToSave(video)
        if (current == null) {
            androidx.car.app.CarToast.makeText(carContext,
                "Select this video in Car Lyrics to save it", androidx.car.app.CarToast.LENGTH_LONG).show()
        } else {
            val added = saved.toggle(current)
            androidx.car.app.CarToast.makeText(carContext,
                if (added) "Saved to Favorites" else "Removed from Favorites",
                androidx.car.app.CarToast.LENGTH_SHORT).show()
            invalidate()
        }
    }

    private fun icon(resource: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, resource)).build()
    private fun currentVideos(): List<KaraokeVideo> {
        if (activeCollection != null) {
            val byId = SingKingCatalog.library.associateBy { it.id }
            return activeCollection!!.ids.mapNotNull { byId[it] }
        }
        return when (source) {
            Source.RECENT -> catalog.videos
            Source.SAVED -> saved.all()
            Source.HISTORY -> history.all().map { it.video }
        }
    }

    private fun openSearch() {
        if (mode == Mode.SEARCH && manager.top === this) return
        // Keep the browsing/search Screen instance below playback. Popping only
        // the player lets the host restore that screen's own scroll and focus.
        val top = manager.top as? CarLyricsScreen
        if (top?.mode == Mode.PLAYER) manager.pop()
        if ((manager.top as? CarLyricsScreen)?.mode == Mode.SEARCH) return
        (manager.top as? CarLyricsScreen ?: this).open(Mode.SEARCH)
    }

    private fun open(next: Mode, source: Source = Source.RECENT,
                     collection: KaraokeCollection? = null, playlist: KaraokePlaylist? = null) {
        manager.push(CarLyricsScreen(carContext, catalog, session, saved, remoteSearch, fullscreenHost,
            next, source, collection, playlist))
    }

    private fun currentVideo(): KaraokeVideo? = player.videoToSave(queue.getOrNull(selected))
        ?: queue.getOrNull(selected) ?: history.all().firstOrNull()?.video

    private fun returnToPlayer(resume: Boolean = false) {
        val current = currentVideo() ?: return
        if (!session.hasVideo) {
            queue = listOf(current); selected = 0
            if (!player.showCurrentVideo()) player.playFrom(current, history.all().firstOrNull { it.video.id == current.id }?.positionMs ?: 0)
        } else player.showCurrentVideo()
        if ((manager.top as? CarLyricsScreen)?.mode != Mode.PLAYER)
            (manager.top as? CarLyricsScreen ?: this).open(Mode.PLAYER)
        if (resume) player.resume()
        manager.top.invalidate()
    }

    private fun nowPlayingAction() = Action.Builder().setTitle("Play current video")
        .setIcon(icon(R.drawable.ic_video)).setOnClickListener { returnToPlayer(resume = true) }.build()

    private fun searchAction() = Action.Builder().setIcon(icon(R.drawable.ic_mic))
        .setOnClickListener { openSearch() }.build()

    private fun header(title: String) = Header.Builder().setTitle(title)
        .setStartHeaderAction(if (ownsSession) Action.APP_ICON else Action.BACK).apply {
            if (currentVideo() != null) addEndHeaderAction(nowPlayingAction())
            addEndHeaderAction(searchAction())
        }

    private fun contentLimit() = carContext.getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST).let { if (it > 0) it.coerceAtMost(100) else 100 }

    private fun select(videos: List<KaraokeVideo>, index: Int, listSource: String? = null, positionMs: Long = 0) {
        val video = videos.getOrNull(index) ?: return
        queue = videos.toList()
        session.listSource = listSource
        selected = index
        playback = PlaybackStatus.LOADING
        history.opened(video, positionMs)
        if (positionMs > 0) player.playFrom(video, positionMs) else player.select(video)
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
        history.opened(queue[selected])
        player.select(queue[selected])
        invalidate()
    }

    private fun browse() {
        if (manager.top === this && (mode == Mode.PLAYER || mode == Mode.HOME)) manager.pop()
    }

    private fun homeTemplate(): Template {
        val list = ItemList.Builder()
        val cards = MorpheHomeFeed.cards
        Thumbnails.request(cards.mapNotNull { it.video }) { }
        val size = (contentLimit() - 1).coerceIn(1, 30)
        homePage = homePage.coerceIn(0, ((cards.size - 1).coerceAtLeast(0) / size))
        val visible = cards.drop(homePage * size).take(size)
        visible.forEach { card ->
            val row = Row.Builder().setTitle(card.title.take(100)).addText(card.channel)
            val video = card.video
            if (video != null) {
                val bitmap = Thumbnails.get(video.id)
                row.setImage(if (bitmap == null) icon(R.drawable.ic_video) else CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build(), Row.IMAGE_TYPE_SMALL)
                row.setOnClickListener { select(cards.mapNotNull { it.video }, cards.mapNotNull { it.video }.indexOfFirst { it.id == video.id }) }
            } else row.addText("Link unavailable • retry")
            list.addItem(row.build())
        }
        if (MorpheHomeFeed.loading && cards.isEmpty()) list.addItem(Row.Builder().setTitle("Loading your recommendations…").build())
        else if (MorpheHomeFeed.error != null && cards.isEmpty()) list.addItem(Row.Builder().setTitle("Home unavailable").addText(MorpheHomeFeed.error!!).build())
        else if (cards.isEmpty()) list.addItem(Row.Builder().setTitle("Your Morphe recommendations").addText("Use Refresh to load your signed-in Home").build())
        if (cards.isNotEmpty() && !MorpheHomeFeed.loading) list.addItem(Row.Builder().setTitle("More recommendations").setOnClickListener {
            if ((homePage + 1) * size < cards.size) { homePage++; invalidate() }
            else MorpheHomeFeed.load(carContext, more = true)
        }.build())
        val head = Header.Builder().setTitle("For you").setStartHeaderAction(Action.BACK)
        if (currentVideo() != null) head.addEndHeaderAction(nowPlayingAction())
        head.addEndHeaderAction(Action.Builder().setTitle(if (MorpheHomeFeed.loading) "Loading" else "Refresh")
            .setOnClickListener { homePage = 0; MorpheHomeFeed.load(carContext, force = true) }.build())
        return ListTemplate.Builder().setHeader(head.build()).setSingleList(list.build()).build()
    }

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
    val hasVideo get() = selected in queue.indices
    init { player.onStatus = { status = it; active?.playbackChanged() } }
}
