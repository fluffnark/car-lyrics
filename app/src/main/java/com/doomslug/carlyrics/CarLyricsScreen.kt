package com.doomslug.carlyrics

import androidx.activity.OnBackPressedCallback
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Host-rendered rows and actions support the Mazda Commander knob. */
class CarLyricsScreen(
    context: CarContext,
    private val catalog: VideoCatalog = SingKingCatalog,
    private val player: VideoPlayer = YouTubeSurface(context),
    private val saved: SavedVideos = SavedVideos(context),
) : Screen(context), DefaultLifecycleObserver {
    private enum class Source { RECENT, SAVED }
    private enum class Mode { BROWSE, SEARCH, COLLECTIONS, QUEUE, PLAYLISTS, PLAYLIST, PLAYER }
    private var source = Source.RECENT
    private var mode = Mode.BROWSE
    private var page = 0
    private var queue = emptyList<KaraokeVideo>()
    private var selected = -1
    private var searchQuery = ""
    private var activeCollection: KaraokeCollection? = null
    private val playlists = PlaylistStore(context)
    private val queueStore = QueueStore(context)
    private var activePlaylist: KaraokePlaylist? = null
    private var playback = PlaybackStatus.IDLE
    private var moving = false
    private var speedRegistered = false
    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { browse() }
    }
    private val speedListener = OnCarDataAvailableListener<Speed> { reading ->
        val value = reading.rawSpeedMetersPerSecond
        val speed = value.value
        if (value.status == CarValue.STATUS_SUCCESS && speed != null) {
            val nowMoving = speed > 0.5f
            if (nowMoving && !moving) browse()
            moving = nowMoving
        }
    }

    init {
        lifecycle.addObserver(this)
        carContext.onBackPressedDispatcher.addCallback(this, back)
        player.onStatus = { next ->
            if (mode == Mode.PLAYER && playback != next) { playback = next; invalidate() }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(player)
        if (catalog === SingKingCatalog) {
            SingKingCatalog.initialize(carContext)
            if (catalog.videos.isEmpty() || SingKingCatalog.isStale()) refresh()
        } else if (catalog.videos.isEmpty() && !catalog.loading) refresh()
        speedRegistered = runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(carContext.mainExecutor, speedListener)
        }.isSuccess
    }

    override fun onStop(owner: LifecycleOwner) {
        player.hide()
        mode = Mode.BROWSE
        back.isEnabled = false
        playback = PlaybackStatus.IDLE
        if (speedRegistered) runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
        }
        speedRegistered = false
    }

    override fun onDestroy(owner: LifecycleOwner) { player.close() }

    override fun onGetTemplate(): Template = when (mode) {
        Mode.BROWSE -> browseTemplate()
        Mode.SEARCH -> searchTemplate()
        Mode.COLLECTIONS -> collectionsTemplate()
        Mode.QUEUE -> queueTemplate()
        Mode.PLAYLISTS -> playlistsTemplate()
        Mode.PLAYLIST -> playlistTemplate()
        Mode.PLAYER -> playerTemplate()
    }

    private fun browseTemplate(): Template {
        val videos = currentVideos()
        val list = ItemList.Builder()
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
            val maxPage = (videos.size - 1) / PAGE_SIZE
            page = page.coerceIn(0, maxPage)
            if (page == 0 && activeCollection == null && source == Source.RECENT) {
                list.addItem(Row.Builder().setTitle("Queue • ${queueStore.all().size} songs")
                    .addText("Build a one-drive karaoke set")
                    .setOnClickListener { mode = Mode.QUEUE; back.isEnabled = true; invalidate() }.build())
                list.addItem(Row.Builder().setTitle("Playlists")
                    .addText("My mix, warm-up, and duets")
                    .setOnClickListener { mode = Mode.PLAYLISTS; back.isEnabled = true; invalidate() }.build())
                list.addItem(Row.Builder().setTitle("Genres & albums")
                    .addText("Browse curated Sing King collections")
                    .setOnClickListener { mode = Mode.COLLECTIONS; back.isEnabled = true; page = 0; invalidate() }.build())
            }
            if (page > 0) list.addItem(Row.Builder().setTitle("Previous page")
                .setOnClickListener { page--; invalidate() }.build())
            val visible = videos.drop(page * PAGE_SIZE).take(PAGE_SIZE)
            visible.forEachIndexed { offset, video ->
                val thumbnail = Thumbnails.get(video.id)
                val icon = if (thumbnail != null) IconCompat.createWithBitmap(thumbnail)
                    else IconCompat.createWithResource(carContext, R.drawable.ic_video)
                list.addItem(Row.Builder()
                    .setTitle(video.title.take(72))
                    .addText(if (source == Source.SAVED) "Saved • Sing King" else "Sing King • Karaoke")
                    .setImage(CarIcon.Builder(icon).build(), Row.IMAGE_TYPE_SMALL)
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        if (!moving) select(videos, page * PAGE_SIZE + offset)
                    }).build())
            }
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                Thumbnails.request(visible) { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && mode == Mode.BROWSE) invalidate() }
            }
            if (page < maxPage) list.addItem(Row.Builder().setTitle("More songs  •  ${page + 2} of ${maxPage + 1}")
                .setOnClickListener { page++; invalidate() }.build())
        }
        val switch = Action.Builder()
            .setTitle(if (source == Source.RECENT) "Saved" else "Recent")
            .setIcon(icon(if (source == Source.RECENT) R.drawable.ic_saved else R.drawable.ic_browse))
            .setOnClickListener {
                source = if (source == Source.RECENT) Source.SAVED else Source.RECENT
                page = 0
                invalidate()
            }.build()
        val header = Header.Builder()
            .setTitle(activeCollection?.title ?: if (source == Source.RECENT) "Sing King • Recent" else "Saved karaoke songs")
            .setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(switch)
            .build()
        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Search").setOnClickListener { openSearch() }.build())
            .build()
        return ListTemplate.Builder().setHeader(header).setSingleList(list.build()).setActionStrip(actions).build()
    }

    private fun searchTemplate(): Template {
        val matches = searchResults(searchQuery)
        val list = ItemList.Builder().setNoItemsMessage(if (searchQuery.isBlank()) "Type or say a song, artist, album, or genre" else "No matching karaoke videos")
        matches.take(30).forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("Sing King • karaoke")
                .setOnClickListener(ParkedOnlyOnClickListener.create { select(matches, matches.indexOf(video)) }).build())
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) { searchQuery = searchText; invalidate() }
            override fun onSearchSubmitted(searchText: String) { searchQuery = searchText; invalidate() }
        }).setSearchHint("Song, artist, album, or genre")
            .setInitialSearchText(searchQuery)
            .setItemList(list.build())
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun collectionsTemplate(): Template {
        val list = ItemList.Builder()
        SingKingCatalog.collections.forEach { collection ->
            list.addItem(Row.Builder().setTitle(collection.title)
                .addText("${collection.kind.replaceFirstChar { it.uppercase() }} • ${collection.ids.size} songs")
                .setOnClickListener { activeCollection = collection; mode = Mode.BROWSE; page = 0; invalidate() }.build())
        }
        list.addItem(Row.Builder().setTitle("Back to recent").setOnClickListener { activeCollection = null; mode = Mode.BROWSE; invalidate() }.build())
        return ListTemplate.Builder().setHeader(Header.Builder().setTitle("Genres & albums").setStartHeaderAction(Action.APP_ICON).build())
            .setSingleList(list.build()).build()
    }

    private fun queueTemplate(): Template {
        val items = queueStore.all()
        val list = ItemList.Builder().setNoItemsMessage("Add songs from the player with Queue")
        items.forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("Queued")
                .setOnClickListener(ParkedOnlyOnClickListener.create { select(items, items.indexOf(video)) }).build())
        }
        return ListTemplate.Builder().setHeader(Header.Builder().setTitle("Up next • ${items.size}").setStartHeaderAction(Action.APP_ICON).build())
            .setSingleList(list.build()).build()
    }

    private fun playlistsTemplate(): Template {
        val list = ItemList.Builder()
        playlists.all().forEach { playlist ->
            list.addItem(Row.Builder().setTitle(playlist.name).addText("${playlist.videos.size} karaoke songs")
                .setOnClickListener { activePlaylist = playlist; mode = Mode.PLAYLIST; back.isEnabled = true; invalidate() }.build())
        }
        return ListTemplate.Builder().setHeader(Header.Builder().setTitle("Your playlists").setStartHeaderAction(Action.APP_ICON).build())
            .setSingleList(list.build()).build()
    }

    private fun playlistTemplate(): Template {
        val playlist = activePlaylist ?: return playlistsTemplate()
        val list = ItemList.Builder().setNoItemsMessage("Add songs from the player with Add to mix")
        playlist.videos.forEach { video ->
            list.addItem(Row.Builder().setTitle(video.title.take(72)).addText("${playlist.name} • karaoke")
                .setOnClickListener(ParkedOnlyOnClickListener.create { select(playlist.videos, playlist.videos.indexOf(video)) }).build())
        }
        return ListTemplate.Builder().setHeader(Header.Builder().setTitle(playlist.name).setStartHeaderAction(Action.APP_ICON).build())
            .setSingleList(list.build()).build()
    }

    private fun playerTemplate(): Template {
        val current = queue.getOrNull(selected)
        val state = when (playback) {
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
            .setOnClickListener(ParkedOnlyOnClickListener.create { step(-1) }).build())
        pane.addAction(Action.Builder().setTitle("Next")
            .setOnClickListener(ParkedOnlyOnClickListener.create { step(1) }).build())
        val playbackAction = when (playback) {
            PlaybackStatus.LOADING, PlaybackStatus.PLAYING -> Action.Builder()
                .setTitle("Pause").setIcon(icon(R.drawable.ic_pause))
                .setOnClickListener { player.pause(); playback = PlaybackStatus.PAUSED; invalidate() }.build()
            else -> Action.Builder()
                .setTitle(if (playback == PlaybackStatus.ERROR) "Retry" else "Play")
                .setIcon(icon(R.drawable.ic_play))
                .setOnClickListener(ParkedOnlyOnClickListener.create {
                    if (!moving) { player.resume(); playback = PlaybackStatus.LOADING; invalidate() }
                }).build()
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

    private fun searchResults(query: String): List<KaraokeVideo> {
        if (query.isBlank()) return SingKingCatalog.library.take(30)
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        return SingKingCatalog.library.filter { video -> words.all { video.title.lowercase().contains(it) } }
    }

    private fun openSearch() { searchQuery = ""; mode = Mode.SEARCH; back.isEnabled = true; invalidate() }

    private fun select(videos: List<KaraokeVideo>, index: Int) {
        val video = videos.getOrNull(index) ?: return
        queue = videos.toList()
        selected = index
        mode = Mode.PLAYER
        back.isEnabled = true
        playback = PlaybackStatus.LOADING
        player.select(video)
        invalidate()
    }

    private fun step(delta: Int) {
        if (moving || queue.isEmpty()) return
        selected = (selected + delta + queue.size) % queue.size
        playback = PlaybackStatus.LOADING
        player.select(queue[selected])
        invalidate()
    }

    private fun browse() {
        player.hide()
        mode = Mode.BROWSE
        searchQuery = ""
        back.isEnabled = false
        playback = PlaybackStatus.IDLE
        invalidate()
    }

    private fun refresh() {
        catalog.refresh { if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) invalidate() }
        invalidate()
    }

    private companion object { const val PAGE_SIZE = 4 }
}
