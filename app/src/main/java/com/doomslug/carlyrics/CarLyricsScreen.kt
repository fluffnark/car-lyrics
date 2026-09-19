package com.doomslug.carlyrics

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** All selection and playback actions are host-rendered, so the Mazda knob can operate them. */
class CarLyricsScreen(context: CarContext) : Screen(context), DefaultLifecycleObserver {
    private val surface = YouTubeSurface(context)
    private var mode = Mode.BROWSE
    private var page = 0
    private var selected = -1
    private var playing = false
    private var moving = false
    private var speedRegistered = false
    private val speedListener = OnCarDataAvailableListener<Speed> { reading ->
        val value = reading.rawSpeedMetersPerSecond
        val speed = value.value
        if (value.status == CarValue.STATUS_SUCCESS && speed != null) {
            val nowMoving = speed > 0.5f
            if (nowMoving && !moving) {
                surface.hide()
                mode = Mode.BROWSE
                playing = false
                invalidate()
            }
            moving = nowMoving
        }
    }

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surface)
        if (SingKingCatalog.videos.isEmpty() && !SingKingCatalog.loading) refresh()
        speedRegistered = runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(carContext.mainExecutor, speedListener)
        }.isSuccess
    }

    override fun onStop(owner: LifecycleOwner) {
        surface.hide()
        mode = Mode.BROWSE
        playing = false
        if (speedRegistered) runCatching {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
        }
        speedRegistered = false
    }

    override fun onDestroy(owner: LifecycleOwner) { surface.close() }

    override fun onGetTemplate(): Template = if (mode == Mode.BROWSE) browseTemplate() else playerTemplate()

    private fun browseTemplate(): Template {
        val videos = SingKingCatalog.videos
        val list = ItemList.Builder()
        if (videos.isEmpty()) {
            list.addItem(Row.Builder().setTitle(
                if (SingKingCatalog.loading) "Loading Sing King uploads…"
                else SingKingCatalog.error ?: "No recent videos"
            ).build())
            if (!SingKingCatalog.loading) list.addItem(Row.Builder().setTitle("Retry")
                .setOnClickListener { refresh() }.build())
        } else {
            val maxPage = (videos.size - 1) / PAGE_SIZE
            page = page.coerceIn(0, maxPage)
            if (page > 0) list.addItem(Row.Builder().setTitle("Previous videos")
                .setOnClickListener { page--; invalidate() }.build())
            videos.drop(page * PAGE_SIZE).take(PAGE_SIZE).forEachIndexed { offset, video ->
                val index = page * PAGE_SIZE + offset
                list.addItem(Row.Builder().setTitle(video.title.take(64))
                    .setOnClickListener(ParkedOnlyOnClickListener.create {
                        if (!moving) select(index)
                    }).build())
            }
            if (page < maxPage) list.addItem(Row.Builder().setTitle("More videos")
                .setOnClickListener { page++; invalidate() }.build())
        }
        return ListTemplate.Builder().setSingleList(list.build()).build()
    }

    private fun playerTemplate(): Template {
        val current = SingKingCatalog.videos.getOrNull(selected)
        val pane = Pane.Builder().addRow(Row.Builder()
            .setTitle(current?.title?.take(50) ?: "Sing King")
            .build())
        pane.addAction(Action.Builder().setTitle("Previous")
            .setOnClickListener(ParkedOnlyOnClickListener.create { step(-1) }).build())
        pane.addAction(Action.Builder().setTitle("Next")
            .setOnClickListener(ParkedOnlyOnClickListener.create { step(1) }).build())
        val playbackAction = if (playing) Action.Builder().setTitle("Pause")
            .setOnClickListener { surface.pause(); playing = false; invalidate() }.build()
        else Action.Builder().setTitle("Play")
            .setOnClickListener(ParkedOnlyOnClickListener.create {
                if (!moving) { surface.resume(); playing = true; invalidate() }
            }).build()
        val strip = ActionStrip.Builder()
            .addAction(playbackAction)
            .addAction(Action.Builder().setTitle("Browse").setOnClickListener {
                surface.hide()
                mode = Mode.BROWSE
                playing = false
                invalidate()
            }.build()).build()
        return MapWithContentTemplate.Builder()
            .setContentTemplate(PaneTemplate.Builder(pane.build()).build())
            .setActionStrip(strip)
            .build()
    }

    private fun select(index: Int) {
        val video = SingKingCatalog.videos.getOrNull(index) ?: return
        selected = index
        mode = Mode.PLAYER
        playing = true
        surface.select(video)
        invalidate()
    }

    private fun step(delta: Int) {
        if (moving) return
        val size = SingKingCatalog.videos.size
        if (size == 0) return
        select((selected + delta + size) % size)
    }

    private fun refresh() {
        SingKingCatalog.refresh { if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) invalidate() }
        invalidate()
    }

    private enum class Mode { BROWSE, PLAYER }
    private companion object { const val PAGE_SIZE = 4 }
}
